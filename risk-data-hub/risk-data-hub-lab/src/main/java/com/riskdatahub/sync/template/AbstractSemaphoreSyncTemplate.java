package com.riskdatahub.sync.template;

import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.sync.model.BusinessSyncContext;
import com.riskdatahub.sync.model.SyncSupport.BusinessSyncResult;
import com.riskdatahub.sync.model.SyncSupport.SyncCounter;
import com.riskdatahub.sync.model.SyncSupport.SyncMetrics;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public abstract class AbstractSemaphoreSyncTemplate<S, T> extends AbstractBaseSyncTemplate<S, T> {

    protected AbstractSemaphoreSyncTemplate(RoutingMybatisExecutor routingMybatisExecutor,
                                            LeafSegmentService leafSegmentService,
                                            ThreadPoolExecutor pairExecutor) {
        super(routingMybatisExecutor, leafSegmentService, pairExecutor);
    }

    @Override
    public final BusinessSyncResult execute(BusinessSyncContext context) throws Exception {
        Semaphore fetchPermit = new Semaphore(1);
        Semaphore insertPermit = new Semaphore(0);
        List<S> sharedPage = new ArrayList<>();
        AtomicReference<SyncMetrics> pendingMetrics = new AtomicReference<>();
        AtomicBoolean noMoreData = new AtomicBoolean(false);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        SyncCounter counter = new SyncCounter();

        log.info("[同步模板] 业务 {} 开始执行，数据源={}, 类型={}, 分页大小={}, 批次号={}, 任务ID={}",
                businessCode(), context.getDataSourceKey(), context.getDatasourceType(),
                context.getPageSize(), context.getBatchNo(), context.getTaskId());

        Future<?> fetchFuture = pairExecutor.submit(() ->
                runFetchThread(context, sharedPage, pendingMetrics, fetchPermit, insertPermit, counter, noMoreData, failure));
        Future<?> insertFuture = pairExecutor.submit(() ->
                runInsertThread(context, sharedPage, pendingMetrics, fetchPermit, insertPermit, counter, noMoreData, failure));

        fetchFuture.get();
        insertFuture.get();
        if (failure.get() != null) {
            throw failure.get();
        }

        log.info("[同步模板] 业务 {} 执行完成，总页数={}, 拉取总数={}, 落库总数={}, 最后游标ID={}",
                businessCode(), counter.getPageCount(), counter.getPulledCount(),
                counter.getSavedCount(), counter.getLastRowId());
        return new BusinessSyncResult(
                businessCode(),
                counter.getPageCount(),
                counter.getPulledCount(),
                counter.getSavedCount(),
                counter.getSavedMaxRowId());
    }

    private void runFetchThread(BusinessSyncContext context,
                                List<S> sharedPage,
                                AtomicReference<SyncMetrics> pendingMetrics,
                                Semaphore fetchPermit,
                                Semaphore insertPermit,
                                SyncCounter counter,
                                AtomicBoolean noMoreData,
                                AtomicReference<RuntimeException> failure) {
        long cursor = initialCursor(context);
        int pageNo = 0;
        try {
            while (failure.get() == null) {
                fetchPermit.acquire();
                if (noMoreData.get()) break;

                SyncMetrics metrics = new SyncMetrics();
                metrics.stampFetchStarted();
                List<S> rows = fetchPage(context, cursor, context.getPageSize());
                if (rows.isEmpty()) {
                    noMoreData.set(true);
                    sharedPage.clear();
                    insertPermit.release();
                    break;
                }

                long nextCursor = sourceRowId(rows.get(rows.size() - 1));
                if (nextCursor <= cursor) {
                    throw new IllegalStateException(
                            businessCode() + " 业务游标未推进，已主动终止，避免死循环");
                }
                cursor = nextCursor;
                pageNo++;
                counter.setPageCount(pageNo);
                counter.setLastRowId(cursor);
                counter.addPulledCount(rows.size());

                metrics.setBatchNo(pageNo);
                metrics.setPulledCount(rows.size());
                metrics.setSavedCount(rows.size());
                metrics.stampFetchFinished();
                metrics.stampFetchQueued();
                pendingMetrics.set(metrics);

                sharedPage.clear();
                sharedPage.addAll(rows);

                log.info("[同步模板] 业务 {} 第 {} 页拉取完成，行数={}, 当前游标={}",
                        businessCode(), pageNo, rows.size(), counter.getLastRowId());
                publishProgress(context.getTaskId(), businessCode(), counter.getPulledCount(), counter.getSavedCount());

                insertPermit.release();

                if (rows.size() < context.getPageSize()) {
                    noMoreData.set(true);
                    insertPermit.release();
                    break;
                }
            }
        } catch (Exception e) {
            recordFailure(insertPermit, failure, new IllegalStateException(
                    businessCode() + " 拉取线程失败: " + e.getMessage(), e));
        }
    }

    private void runInsertThread(BusinessSyncContext context,
                                 List<S> sharedPage,
                                 AtomicReference<SyncMetrics> pendingMetrics,
                                 Semaphore fetchPermit,
                                 Semaphore insertPermit,
                                 SyncCounter counter,
                                 AtomicBoolean noMoreData,
                                 AtomicReference<RuntimeException> failure) {
        try {
            while (failure.get() == null) {
                insertPermit.acquire();

                if (noMoreData.get() && sharedPage.isEmpty()) {
                    break;
                }

                SyncMetrics metrics = pendingMetrics.get();
                List<S> rows = new ArrayList<>(sharedPage);
                sharedPage.clear();
                fetchPermit.release();

                preAllocateBatchIds(getIdTag(), rows.size(), metrics);

                metrics.stampTransformStarted();
                List<T> targets = new ArrayList<>(rows.size());
                for (S row : rows) {
                    targets.add(transform(context, row));
                }
                metrics.stampTransformFinished();

                try {
                    saveBatch(context, targets, metrics);
                } catch (Exception e) {
                    log.error("[同步模板] 业务 {} 第 {} 页落库失败: {}", businessCode(), counter.getPageCount(), e.getMessage(), e);
                    metrics.setErrorMessage(e.getMessage());
                    recordBatchMetrics(context, metrics);
                    throw e;
                }
                for (S row : rows) {
                    counter.incrementSavedCount();
                }
                counter.updateSavedMaxRowId(sourceRowId(rows.get(rows.size() - 1)));
                log.info("[同步模板] 业务 {} 第 {} 页落库完成，累计落库数={}",
                        businessCode(), counter.getPageCount(), counter.getSavedCount());
                publishProgress(context.getTaskId(), businessCode(), counter.getPulledCount(), counter.getSavedCount());
                recordBatchMetrics(context, metrics);
            }
        } catch (Exception e) {
            recordFailure(fetchPermit, failure, new IllegalStateException(
                    businessCode() + " 落库线程失败: " + e.getMessage(), e));
        }
    }

    private void recordFailure(Semaphore blockedSemaphore,
                               AtomicReference<RuntimeException> failure,
                               RuntimeException exception) {
        if (failure.compareAndSet(null, exception)) {
            log.error("[同步模板] 业务 {} 执行失败: {}", businessCode(), exception.getMessage(), exception);
            blockedSemaphore.release();
        }
    }
}
