package com.riskdatahub.sync.template;

import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.message.MessageOutboxService;
import com.riskdatahub.sync.model.BusinessSyncContext;
import com.riskdatahub.sync.model.SyncSupport.BusinessSyncResult;
import com.riskdatahub.sync.model.SyncSupport.PageChunk;
import com.riskdatahub.sync.model.SyncSupport.SyncCounter;
import com.riskdatahub.sync.model.SyncSupport.SyncMetrics;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public abstract class AbstractBusinessSyncTemplate<S, T> extends AbstractBaseSyncTemplate<S, T> {

    protected AbstractBusinessSyncTemplate(RoutingMybatisExecutor routingMybatisExecutor,
                                            LeafSegmentService leafSegmentService,
                                            MessageOutboxService messageOutboxService,
                                            ThreadPoolExecutor pairExecutor) {
        super(routingMybatisExecutor, leafSegmentService, messageOutboxService, pairExecutor);
    }

    @Override
    public final BusinessSyncResult execute(BusinessSyncContext context) throws Exception {
        BlockingQueue<PageChunk<S>> queue = new ArrayBlockingQueue<>(4);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        SyncCounter counter = new SyncCounter();

        log.info("[同步模板] 业务 {} 开始执行，数据源={}, 类型={}, 分页大小={}, 批次号={}, 任务ID={}",
                businessCode(), context.getDataSourceKey(), context.getDatasourceType(),
                context.getPageSize(), context.getBatchNo(), context.getTaskId());

        Future<?> fetchFuture = pairExecutor.submit(() ->
                runFetchThread(context, queue, counter, failure));
        Future<?> insertFuture = pairExecutor.submit(() ->
                runInsertThread(context, queue, counter, failure));

        fetchFuture.get();
        insertFuture.get();
        if (failure.get() != null) {
            throw failure.get();
        }

        publishBusinessSummaryEvent(context, counter);
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
                                BlockingQueue<PageChunk<S>> queue,
                                SyncCounter counter,
                                AtomicReference<RuntimeException> failure) {
        long cursor = initialCursor(context);
        int pageNo = 0;
        try {
            while (failure.get() == null) {
                SyncMetrics metrics = new SyncMetrics();
                metrics.stampFetchStarted();
                List<S> rows = fetchPage(context, cursor, context.getPageSize());
                if (rows.isEmpty()) {
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
                metrics.setBatchNo(pageNo);
                metrics.setPulledCount(rows.size());
                metrics.setSavedCount(rows.size());
                metrics.stampFetchFinished();
                metrics.stampFetchQueued();
                counter.addPulledCount(rows.size());
                queue.put(PageChunk.data(pageNo, rows, metrics));
                log.info("[同步模板] 业务 {} 第 {} 页拉取完成，行数={}, 当前游标={}",
                        businessCode(), pageNo, rows.size(), counter.getLastRowId());
                publishProgress(context.getTaskId(), businessCode(), counter.getPulledCount(), counter.getSavedCount());
                if (rows.size() < context.getPageSize()) {
                    break;
                }
            }
        } catch (Exception e) {
            recordFailure(queue, failure, new IllegalStateException(
                    businessCode() + " 拉取线程失败: " + e.getMessage(), e));
        } finally {
            if (failure.get() == null) {
                publishEnd(queue);
            }
        }
    }

    private void runInsertThread(BusinessSyncContext context,
                                 BlockingQueue<PageChunk<S>> queue,
                                 SyncCounter counter,
                                 AtomicReference<RuntimeException> failure) {
        try {
            while (failure.get() == null) {
                PageChunk<S> chunk = queue.take();
                SyncMetrics metrics = chunk.getMetrics();
                metrics.stampFetchQueuedFinished();
                if (chunk.isEnd()) {
                    break;
                }
                List<S> rows = chunk.getRows();

                preAllocateBatchIds(getIdTag(), rows.size(), metrics);

                metrics.stampTransformStarted();
                List<T> targets = new ArrayList<>(rows.size());
                for (S row : rows) {
                    targets.add(transform(context, row));
                }
                metrics.stampTransformFinished();

                metrics.stampSaveStarted();
                saveBatch(context, targets, metrics);
                metrics.stampSaveFinished();

                for (S row : rows) {
                    counter.incrementSavedCount();
                }
                counter.updateSavedMaxRowId(sourceRowId(rows.get(rows.size() - 1)));
                log.info("[同步模板] 业务 {} 第 {} 页落库完成，累计落库数={}",
                        businessCode(), chunk.getPageNo(), counter.getSavedCount());
                publishProgress(context.getTaskId(), businessCode(), counter.getPulledCount(), counter.getSavedCount());
                recordBatchMetrics(context, metrics);
            }
        } catch (Exception e) {
            recordFailure(queue, failure, new IllegalStateException(
                    businessCode() + " 落库线程失败: " + e.getMessage(), e));
        }
    }

    private void recordFailure(BlockingQueue<PageChunk<S>> queue,
                               AtomicReference<RuntimeException> failure,
                               RuntimeException exception) {
        if (failure.compareAndSet(null, exception)) {
            log.error("[同步模板] 业务 {} 执行失败: {}", businessCode(), exception.getMessage(), exception);
            queue.clear();
            queue.offer(PageChunk.end());
        }
    }

    private void publishEnd(BlockingQueue<PageChunk<S>> queue) {
        try {
            queue.put(PageChunk.end());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(businessCode() + " 结束信号发送失败: " + e.getMessage(), e);
        }
    }
}
