package com.riskdatahub.sync.template;

import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.message.MessageOutboxService;
import com.riskdatahub.sync.model.BusinessSyncContext;
import com.riskdatahub.sync.model.SyncSupport.BusinessSyncResult;
import com.riskdatahub.sync.model.SyncSupport.PageChunk;
import com.riskdatahub.sync.model.SyncSupport.SyncCounter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 同步模板骨架类 — 有界队列生产者-消费者模式 + 模板方法模式。
 * <p>
 * <b>模板方法模式：</b>{@code execute()} 用 {@code final} 修饰，固定同步流程：
 * 拉取 → 转换 → 落库 → 标记已同步。子类通过覆盖 5 个抽象方法定制每个步骤的具体实现。
 * </p>
 * <p>
 * <b>生产者-消费者模式：</b>每类业务内部使用两个线程：
 * <ul>
 *   <li>拉取线程（生产者）：从上游分页拉取未同步数据，放入有界队列</li>
 *   <li>落库线程（消费者）：从队列读取数据，逐条转换并写入中台库</li>
 * </ul>
 * 队列容量为 4，拉取过快时 put() 阻塞，通过背压控制内存上限。
 * </p>
 *
 * @param <S> 上游源数据类型
 * @param <T> 中台目标实体类型
 * @author risk-data-hub
 */
@Slf4j
public abstract class AbstractBusinessSyncTemplate<S, T> extends AbstractBaseSyncTemplate<S, T> {

    protected AbstractBusinessSyncTemplate(RoutingMybatisExecutor routingMybatisExecutor,
                                            LeafSegmentService leafSegmentService,
                                            MessageOutboxService messageOutboxService,
                                            ThreadPoolExecutor pairExecutor) {
        super(routingMybatisExecutor, leafSegmentService, messageOutboxService, pairExecutor);
    }

    /**
     * 模板方法 — final 禁止子类重写，确保所有业务类型的同步流程一致。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>创建容量为 4 的有界阻塞队列（背压控制）</li>
     *   <li>向 pairExecutor 提交拉取线程和落库线程</li>
     *   <li>等待两个线程都完成（Future.get() 阻塞等待）</li>
     *   <li>检查是否有异常（failure 信号旗），有则抛出</li>
     *   <li>发布同步完成事件（发件箱模式）</li>
     * </ol>
     * </p>
     */
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

    /**
     * 拉取线程（生产者）：基于游标分页查询，通过有界队列传递给落库线程。
     * <p>游标推进防御死循环：如果最后一条记录的 ID 不超过当前游标，立即终止。</p>
     */
    private void runFetchThread(BusinessSyncContext context,
                                BlockingQueue<PageChunk<S>> queue,
                                SyncCounter counter,
                                AtomicReference<RuntimeException> failure) {
        long cursor = initialCursor(context);
        int pageNo = 0;
        try {
            while (failure.get() == null) {
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
                counter.addPulledCount(rows.size());
                queue.put(PageChunk.data(pageNo, rows));
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

    /**
     * 落库线程（消费者）：从队列读取数据，转换并写入中台库。
     * <p>收到哨兵 end() 对象后退出循环（优雅终止）。</p>
     */
    private void runInsertThread(BusinessSyncContext context,
                                 BlockingQueue<PageChunk<S>> queue,
                                 SyncCounter counter,
                                 AtomicReference<RuntimeException> failure) {
        try {
            while (failure.get() == null) {
                PageChunk<S> chunk = queue.take();
                if (chunk.isEnd()) {
                    break;
                }
                List<S> rows = chunk.getRows();
                List<T> targets = new ArrayList<>(rows.size());
                for (S row : rows) {
                    targets.add(transform(context, row));
                }
                saveBatch(context, targets);
                long pageMaxId = 0;
                for (S row : rows) {
                    markSourceRowSynced(context, sourceRowId(row));
                    counter.incrementSavedCount();
                    long rowId = sourceRowId(row);
                    if (rowId > pageMaxId) pageMaxId = rowId;
                }
                counter.updateSavedMaxRowId(pageMaxId);
                log.info("[同步模板] 业务 {} 第 {} 页落库完成，累计落库数={}",
                        businessCode(), chunk.getPageNo(), counter.getSavedCount());
                publishProgress(context.getTaskId(), businessCode(), counter.getPulledCount(), counter.getSavedCount());
            }
        } catch (Exception e) {
            recordFailure(queue, failure, new IllegalStateException(
                    businessCode() + " 落库线程失败: " + e.getMessage(), e));
        }
    }

    /**
     * 记录故障信号并通知对端线程终止。
     * <p>CAS 保证"第一个失败"的线程写入自己的异常作为根因，
     * 后续失败不再覆盖。clear + offer(end) 确保对端不会永远阻塞在 take() 上。</p>
     */
    private void recordFailure(BlockingQueue<PageChunk<S>> queue,
                               AtomicReference<RuntimeException> failure,
                               RuntimeException exception) {
        if (failure.compareAndSet(null, exception)) {
            log.error("[同步模板] 业务 {} 执行失败: {}", businessCode(), exception.getMessage(), exception);
            queue.clear();
            queue.offer(PageChunk.end());
        }
    }

    /** 发送结束哨兵到队列 */
    private void publishEnd(BlockingQueue<PageChunk<S>> queue) {
        try {
            queue.put(PageChunk.end());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(businessCode() + " 结束信号发送失败: " + e.getMessage(), e);
        }
    }
}
