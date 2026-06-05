package com.riskdatahub.sync.template;

import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.message.MessageOutboxService;
import com.riskdatahub.sync.model.BusinessSyncContext;
import com.riskdatahub.sync.model.SyncSupport.BusinessSyncResult;
import com.riskdatahub.sync.model.SyncSupport.SyncCounter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 同步模板骨架类 — 信号量交替模式 + 模板方法模式。
 * <p>
 * <b>模板方法模式：</b>{@code execute()} 用 {@code final} 修饰，固定同步流程：
 * 拉取 → 转换 → 落库 → 标记已同步。子类通过覆盖 5 个抽象方法定制每个步骤的具体实现。
 * </p>
 * <p>
 * <b>信号量交替模式：</b>每类业务内部使用两个线程严格交替执行：
 * <ul>
 *   <li>拉取线程（生产者）：持有 fetchPermit 时执行拉取，完成后释放 insertPermit</li>
 *   <li>落库线程（消费者）：持有 insertPermit 时执行落库，完成后释放 fetchPermit</li>
 * </ul>
 * 任意时刻最多 1 页数据在内存中，从根本上杜绝内存积压。
 * </p>
 *
 * @param <S> 上游源数据类型
 * @param <T> 中台目标实体类型
 * @author risk-data-hub
 */
@Slf4j
public abstract class AbstractSemaphoreSyncTemplate<S, T> extends AbstractBaseSyncTemplate<S, T> {

    protected AbstractSemaphoreSyncTemplate(RoutingMybatisExecutor routingMybatisExecutor,
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
     *   <li>创建两个信号量：fetchPermit=1（拉取先执行），insertPermit=0（等待拉取完成）</li>
     *   <li>创建共享页缓冲区（交替使用，最多 1 页数据在内存）</li>
     *   <li>向 pairExecutor 提交拉取线程和落库线程</li>
     *   <li>等待两个线程都完成（Future.get() 阻塞等待）</li>
     *   <li>检查是否有异常（failure 信号旗），有则抛出</li>
     *   <li>发布同步完成事件（发件箱模式）</li>
     * </ol>
     * </p>
     */
    @Override
    public final BusinessSyncResult execute(BusinessSyncContext context) throws Exception {
        // 两个信号量保证拉取和落库严格交替执行
        Semaphore fetchPermit = new Semaphore(1);  // 初始 1，拉取先执行
        Semaphore insertPermit = new Semaphore(0); // 初始 0，落库等待拉取完成
        // 共享页缓冲区 — 最多 1 页数据，由信号量保证交替访问，无需额外同步
        List<S> sharedPage = new ArrayList<>();
        AtomicBoolean noMoreData = new AtomicBoolean(false);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        SyncCounter counter = new SyncCounter();

        log.info("[同步模板] 业务 {} 开始执行，数据源={}, 类型={}, 分页大小={}, 批次号={}, 任务ID={}",
                businessCode(), context.getDataSourceKey(), context.getDatasourceType(),
                context.getPageSize(), context.getBatchNo(), context.getTaskId());

        Future<?> fetchFuture = pairExecutor.submit(() ->
                runFetchThread(context, sharedPage, fetchPermit, insertPermit, counter, noMoreData, failure));
        Future<?> insertFuture = pairExecutor.submit(() ->
                runInsertThread(context, sharedPage, fetchPermit, insertPermit, counter, noMoreData, failure));

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
     * 拉取线程：持有 fetchPermit 时拉取一页，放入共享缓冲区，释放 insertPermit 唤醒落库。
     * <p>
     * 游标推进防御死循环：如果最后一条记录的 ID 不超过当前游标，立即终止。
     * 数据拉空时设置 noMoreData 哨兵并唤醒落库线程优雅退出。
     * </p>
     */
    private void runFetchThread(BusinessSyncContext context,
                                List<S> sharedPage,
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

                sharedPage.clear();
                sharedPage.addAll(rows);

                log.info("[同步模板] 业务 {} 第 {} 页拉取完成，行数={}, 当前游标={}",
                        businessCode(), pageNo, rows.size(), counter.getLastRowId());
                publishProgress(context.getTaskId(), businessCode(), counter.getPulledCount(), counter.getSavedCount());

                insertPermit.release();

                if (rows.size() < context.getPageSize()) {
                    noMoreData.set(true);
                    break;
                }
            }
        } catch (Exception e) {
            recordFailure(insertPermit, failure, new IllegalStateException(
                    businessCode() + " 拉取线程失败: " + e.getMessage(), e));
        }
    }

    /**
     * 落库线程：持有 insertPermit 时从共享缓冲区读取数据，转换并写入中台库。
     * <p>
     * 处理完一页后释放 fetchPermit 唤醒拉取线程，严格交替执行。
     * 遇到 noMoreData 且缓冲区为空时优雅退出。
     * </p>
     */
    private void runInsertThread(BusinessSyncContext context,
                                 List<S> sharedPage,
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

                // 将数据从共享缓冲区复制出来，立即释放 fetchPermit 让拉取线程继续
                List<S> rows = new ArrayList<>(sharedPage);
                sharedPage.clear();
                fetchPermit.release();

                // 批量转换落库（此时拉取线程已在拉取下一页，互不干扰）
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
                        businessCode(), counter.getPageCount(), counter.getSavedCount());
                publishProgress(context.getTaskId(), businessCode(), counter.getPulledCount(), counter.getSavedCount());
            }
        } catch (Exception e) {
            recordFailure(fetchPermit, failure, new IllegalStateException(
                    businessCode() + " 落库线程失败: " + e.getMessage(), e));
        }
    }

    /**
     * 记录故障信号并通知对端线程终止。
     * <p>
     * CAS 保证"第一个失败"的线程写入自己的异常作为根因。
     * 释放对端线程正在等待的信号量，避免其永久阻塞。
     * </p>
     */
    private void recordFailure(Semaphore blockedSemaphore,
                               AtomicReference<RuntimeException> failure,
                               RuntimeException exception) {
        if (failure.compareAndSet(null, exception)) {
            log.error("[同步模板] 业务 {} 执行失败: {}", businessCode(), exception.getMessage(), exception);
            blockedSemaphore.release();
        }
    }
}
