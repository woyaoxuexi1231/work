package com.example.dynamicds.sync;

import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.service.LeafSegmentService;
import com.example.dynamicds.service.MessageOutboxService;
import com.example.dynamicds.sync.SyncSupport.BusinessSyncResult;
import com.example.dynamicds.sync.SyncSupport.PageChunk;
import com.example.dynamicds.sync.SyncSupport.SyncCounter;
import com.example.dynamicds.sync.SyncSupport.SyncProgress;
import com.example.dynamicds.sync.SyncSupport.SyncProgressListener;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 这是同步模板模式的骨架类。
 * 外层流程固定为：拉取 -> 转换 -> 落库，不同业务只覆盖各自的查询、转换和回写逻辑。
 */
@Slf4j
public abstract class AbstractBusinessSyncTemplate<S, T> implements BusinessSyncTemplate {

    protected final RoutingMybatisExecutor routingMybatisExecutor;
    protected final LeafSegmentService leafSegmentService;
    private final MessageOutboxService messageOutboxService;
    private final ThreadPoolExecutor pairExecutor;

    protected AbstractBusinessSyncTemplate(RoutingMybatisExecutor routingMybatisExecutor,
                                           LeafSegmentService leafSegmentService,
                                           MessageOutboxService messageOutboxService,
                                           ThreadPoolExecutor pairExecutor) {
        this.routingMybatisExecutor = routingMybatisExecutor;
        this.leafSegmentService = leafSegmentService;
        this.messageOutboxService = messageOutboxService;
        this.pairExecutor = pairExecutor;
    }

    @Override
    public final BusinessSyncResult execute(BusinessSyncContext context,
                                            SyncProgressListener progressListener) throws Exception {
        BlockingQueue<PageChunk<S>> queue = new ArrayBlockingQueue<>(4);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        SyncCounter counter = new SyncCounter();

        log.info("[同步模板] 业务 {} 开始执行，source={}, type={}, pageSize={}, batchNo={}",
                businessCode(), context.getDataSourceKey(), context.getDatasourceType(), context.getPageSize(), context.getBatchNo());

        Future<?> fetchFuture = pairExecutor.submit(() ->
                runFetchThread(context, queue, counter, progressListener, failure));
        Future<?> insertFuture = pairExecutor.submit(() ->
                runInsertThread(context, queue, counter, progressListener, failure));

        fetchFuture.get();
        insertFuture.get();
        if (failure.get() != null) {
            throw failure.get();
        }

        publishBusinessSummaryEvent(context, counter);
        log.info("[同步模板] 业务 {} 执行完成，pageCount={}, pulledCount={}, savedCount={}, lastRowId={}",
                businessCode(), counter.getPageCount(), counter.getPulledCount(), counter.getSavedCount(), counter.getLastRowId());
        return new BusinessSyncResult(
                businessCode(),
                counter.getPageCount(),
                counter.getPulledCount(),
                counter.getSavedCount(),
                counter.getLastRowId());
    }

    private void runFetchThread(BusinessSyncContext context,
                                BlockingQueue<PageChunk<S>> queue,
                                SyncCounter counter,
                                SyncProgressListener progressListener,
                                AtomicReference<RuntimeException> failure) {
        long cursor = 0L;
        int pageNo = 0;
        try {
            while (failure.get() == null) {
                List<S> rows = fetchPage(context, cursor, context.getPageSize());
                if (rows.isEmpty()) {
                    break;
                }
                long nextCursor = sourceRowId(rows.get(rows.size() - 1));
                if (nextCursor <= cursor) {
                    throw new IllegalStateException(businessCode() + " 业务游标未推进，已主动终止，避免死循环");
                }
                cursor = nextCursor;
                pageNo++;
                counter.setPageCount(pageNo);
                counter.setLastRowId(cursor);
                counter.addPulledCount(rows.size());
                queue.put(PageChunk.data(pageNo, rows));
                log.info("[同步模板] 业务 {} 拉取完成 pageNo={}, rowCount={}, lastRowId={}",
                        businessCode(), pageNo, rows.size(), counter.getLastRowId());
                progressListener.onProgress(new SyncProgress(
                        businessCode(), "FETCHED", pageNo, counter.getPulledCount(), counter.getSavedCount(), counter.getLastRowId()));
                if (rows.size() < context.getPageSize()) {
                    break;
                }
            }
        } catch (Exception e) {
            recordFailure(queue, failure, new IllegalStateException(businessCode() + " 拉取线程失败: " + e.getMessage(), e));
        } finally {
            if (failure.get() == null) {
                publishEnd(queue);
            }
        }
    }

    private void runInsertThread(BusinessSyncContext context,
                                 BlockingQueue<PageChunk<S>> queue,
                                 SyncCounter counter,
                                 SyncProgressListener progressListener,
                                 AtomicReference<RuntimeException> failure) {
        try {
            while (failure.get() == null) {
                PageChunk<S> chunk = queue.take();
                if (chunk.isEnd()) {
                    break;
                }
                for (S row : chunk.getRows()) {
                    T target = transform(context, row);
                    save(target);
                    markSourceRowSynced(context, sourceRowId(row));
                    counter.incrementSavedCount();
                }
                log.info("[同步模板] 业务 {} 落库完成 pageNo={}, savedCount={}",
                        businessCode(), chunk.getPageNo(), counter.getSavedCount());
                progressListener.onProgress(new SyncProgress(
                        businessCode(), "SAVED", chunk.getPageNo(), counter.getPulledCount(), counter.getSavedCount(), counter.getLastRowId()));
            }
        } catch (Exception e) {
            recordFailure(queue, failure, new IllegalStateException(businessCode() + " 落库线程失败: " + e.getMessage(), e));
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

    private void publishBusinessSummaryEvent(BusinessSyncContext context, SyncCounter counter) {
        String payload = "{"
                + "\"businessCode\":\"" + businessCode() + "\","
                + "\"dataSourceKey\":\"" + context.getDataSourceKey() + "\","
                + "\"datasourceType\":\"" + context.getDatasourceType() + "\","
                + "\"batchNo\":\"" + context.getBatchNo() + "\","
                + "\"pulledCount\":" + counter.getPulledCount() + ","
                + "\"savedCount\":" + counter.getSavedCount()
                + "}";
        messageOutboxService.publish("risk.sync.business.completed", context.getBatchNo() + ":" + businessCode(), payload);
    }

    protected abstract List<S> fetchPage(BusinessSyncContext context, long lastId, int pageSize);

    protected abstract long sourceRowId(S row);

    protected abstract T transform(BusinessSyncContext context, S row);

    protected abstract void save(T target);

    protected abstract void markSourceRowSynced(BusinessSyncContext context, long rowId);
}
