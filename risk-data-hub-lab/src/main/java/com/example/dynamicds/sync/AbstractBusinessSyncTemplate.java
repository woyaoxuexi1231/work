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
 * 同步模板骨架类 — 实现生产者-消费者双线程同步模式。
 *
 * 模板方法 execute() 定义固定流程：
 * 1. 拉取线程（fetchThread）：从上游系统分页拉取未同步数据（sync_flag=0），放入阻塞队列
 * 2. 落库线程（insertThread）：从队列消费数据，逐条转换（transform）并写入中台库
 * 3. 完成后标记源行 sync_flag=1，防止重复同步
 *
 * 不同业务子类只需覆盖：
 * - fetchPage(): 如何拉取数据
 * - transform(): 如何将上游数据转换为中台标准格式
 * - save(): 如何保存到中台库
 * - markSourceRowSynced(): 如何标记已同步
 *
 * @param <S> 上游源数据类型
 * @param <T> 中台目标实体类型
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

        log.info("[同步模板] 业务 {} 开始执行，数据源={}, 类型={}, 分页大小={}, 批次号={}",
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
        log.info("[同步模板] 业务 {} 执行完成，总页数={}, 拉取总数={}, 落库总数={}, 最后游标ID={}",
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
                log.info("[同步模板] 业务 {} 第 {} 页拉取完成，行数={}, 当前游标={}",
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
                log.info("[同步模板] 业务 {} 第 {} 页落库完成，累计落库数={}",
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

    /**
     * 同步完成后向 event_message 表写入完成事件（发件箱模式）
     */
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
