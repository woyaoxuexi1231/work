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
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 同步模板骨架类 — 生产者-消费者双线程模式 + 模板方法模式。
 * <p>
 * <b>模板方法模式</b><br>
 * {@code execute()} 用 {@code final} 修饰，固定了同步流程：
 * 拉取 → 转换 → 落库 → 标记已同步。
 * 子类通过覆盖 5 个抽象方法定制每个步骤的具体实现。
 * 这是 GoF 23 种设计模式之一的典型应用。
 * <p>
 * <b>生产者-消费者模式</b><br>
 * 每类业务内部使用两个线程：
 * <ul>
 *   <li>拉取线程（生产者）：从上游分页拉取未同步数据，放入 ArrayBlockingQueue</li>
 *   <li>落库线程（消费者）：从队列读取数据，逐条转换并写入中台库</li>
 * </ul>
 * 通过 BlockingQueue 解耦两个线程，拉取速度与落库速度互相独立：
 * 拉得快 → 队列积压（上限 4 页），落库线程追赶；落库快 → 队列为空，落库线程阻塞在 take() 等待。
 * <p>
 * <b>为什么是 {@code ArrayBlockingQueue(4)} 而不是无界队列？</b>
 * <ol>
 *   <li><b>背压（back pressure）</b> — 限制队列长度，拉取线程在 {@code queue.put()} 阻塞，
 *       自然放慢拉取速度，避免内存被未落库的数据撑爆。</li>
 *   <li><b>4 页 ≈ 4&times;pageSize 行数据</b>，在内存和吞吐之间取平衡。</li>
 * </ol>
 * <p>
 * <b>{@code AtomicReference&lt;RuntimeException&gt; failure} 的设计</b><br>
 * 两个线程可能任一失败。failure 作为跨线程的"故障信号旗"：
 * <ul>
 *   <li>任一线程发现对端失败（{@code failure.get() != null}），立即自我终止。</li>
 *   <li>CAS（{@code compareAndSet}）保证"第一个失败"的线程写入自己的异常，
 *       后续失败不再覆盖，保留根因。</li>
 *   <li>{@code queue.clear() + queue.offer(PageChunk.end())} 确保对端能快速退出，
 *       不会永远阻塞在 {@code take()} 上。</li>
 * </ul>
 *
 * @param <S> 上游源数据类型
 * @param <T> 中台目标实体类型
 */
@Slf4j
public abstract class AbstractBusinessSyncTemplate<S, T> implements BusinessSyncTemplate {

    /** MyBatis 路由执行器 — 用于在指定数据源上执行 SQL */
    protected final RoutingMybatisExecutor routingMybatisExecutor;
    /** Leaf 号段 ID 生成器 — 生成中台表全局唯一 ID */
    protected final LeafSegmentService leafSegmentService;
    /** 消息发件箱 — 同步完成后写入事件消息 */
    private final MessageOutboxService messageOutboxService;
    /**
     * 当前业务类型的双线程池（每个业务 2 线程）。
     * 由 SyncThreadPoolConfig 创建并注入，每个业务模板独立使用各自的线程池，
     * 避免不同业务类型之间互相干扰。
     */
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

    /**
     * 模板方法 — final 禁止子类重写，确保同步流程一致。
     *
     * 执行流程：
     * 1. 创建容量为 4 的有界阻塞队列（背压控制）
     * 2. 向 pairExecutor 提交拉取线程和落库线程
     * 3. 等待两个线程都完成（Future.get() 阻塞等待）
     * 4. 检查是否有异常（failure 信号旗），有则抛出
     * 5. 发布同步完成事件（发件箱模式）
     */
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

    // ========== 基于 Semaphore 的交替执行版本 ==========

    /**
     * 信号量版同步执行 — 使用两个 Semaphore 控制拉取线程和落库线程交替运行。
     * <p>
     * <b>与 execute()（生产者-消费者 + BlockingQueue）的区别：</b>
     * <ul>
     *   <li>execute()：拉取和落库可同时运行，队列最多缓冲 4 页，适合 IO 密集且上下游速度不均的场景。</li>
     *   <li>executeWithSemaphore()：拉取一页 → 落库一页 → 拉取下一页 → … 严格交替执行，
     *       适合内存敏感或需要严格顺序保证的场景。</li>
     * </ul>
     * <p>
     * <b>信号量协调逻辑：</b>
     * <pre>
     * fetchTurn (初始 1)          insertTurn (初始 0)
     *   ↓ acquire()                 ↓ acquire() 阻塞
     *   拉取一页数据                等待 fetch 释放
     *   ↓ insertTurn.release()      ↓ 获取到信号 → 落库数据
     *   等待 insert 完成 ←──────    ↓ fetchTurn.release()
     *   ↓ acquire() 阻塞            回到顶部等待下一页
     * </pre>
     * 最后一批数据取完后，fetch 设置 {@code lastBatch=true} 并退出循环，
     * insert 处理完最后一批后看到 {@code lastBatch} 也退出。
     */
    public BusinessSyncResult executeWithSemaphore(BusinessSyncContext context,
                                                   SyncProgressListener progressListener) throws Exception {
        Semaphore fetchTurn = new Semaphore(1);
        Semaphore insertTurn = new Semaphore(0);
        AtomicReference<List<S>> pageRef = new AtomicReference<>();
        AtomicBoolean lastBatch = new AtomicBoolean(false);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        SyncCounter counter = new SyncCounter();

        log.info("[信号量版] 业务 {} 开始交替执行，数据源={}, 类型={}, 分页大小={}, 批次号={}",
                businessCode(), context.getDataSourceKey(), context.getDatasourceType(),
                context.getPageSize(), context.getBatchNo());

        Future<?> fetchFuture = pairExecutor.submit(() -> {
            long cursor = 0L;
            int pageNo = 0;
            try {
                while (failure.get() == null) {
                    fetchTurn.acquire();
                    if (failure.get() != null) break;

                    List<S> rows = fetchPage(context, cursor, context.getPageSize());
                    if (rows.isEmpty()) {
                        pageRef.set(List.of());
                        lastBatch.set(true);
                        insertTurn.release();
                        break;
                    }
                    long nextCursor = sourceRowId(rows.get(rows.size() - 1));
                    if (nextCursor <= cursor) {
                        throw new IllegalStateException(businessCode() + " 游标未推进，已主动终止");
                    }
                    cursor = nextCursor;
                    pageNo++;
                    counter.setPageCount(pageNo);
                    counter.setLastRowId(cursor);
                    counter.addPulledCount(rows.size());
                    pageRef.set(rows);

                    log.info("[信号量版] 业务 {} 第 {} 页拉取完成，行数={}, 当前游标={}",
                            businessCode(), pageNo, rows.size(), cursor);

                    insertTurn.release(); // 通知落库线程开始处理

                    if (rows.size() < context.getPageSize()) {
                        lastBatch.set(true); // 标记最后一批，insert 处理完即止
                        break;               // fetch 不继续循环，不重新 acquire
                    }
                }
            } catch (Exception e) {
                recordSemaphoreFailure(failure, pageRef, lastBatch, insertTurn,
                        new RuntimeException(businessCode() + " 拉取线程失败: " + e.getMessage(), e));
            }
        });

        Future<?> insertFuture = pairExecutor.submit(() -> {
            try {
                while (failure.get() == null) {
                    insertTurn.acquire();
                    if (failure.get() != null) break;

                    List<S> rows = pageRef.get();
                    if (rows == null || rows.isEmpty()) break;

                    for (S row : rows) {
                        T target = transform(context, row);
                        save(target);
                        markSourceRowSynced(context, sourceRowId(row));
                        counter.incrementSavedCount();
                    }

                    log.info("[信号量版] 业务 {} 落库完成，累计落库数={}", businessCode(), counter.getSavedCount());

                    if (lastBatch.get()) break;             // 最后一批，不再 fetch
                    fetchTurn.release();                     // 通知拉取线程继续
                }
            } catch (Exception e) {
                recordSemaphoreFailure(failure, pageRef, lastBatch, fetchTurn,
                        new RuntimeException(businessCode() + " 落库线程失败: " + e.getMessage(), e));
            }
        });

        fetchFuture.get();
        insertFuture.get();
        if (failure.get() != null) throw failure.get();

        publishBusinessSummaryEvent(context, counter);
        log.info("[信号量版] 业务 {} 执行完成，总页数={}, 拉取总数={}, 落库总数={}, 最后游标ID={}",
                businessCode(), counter.getPageCount(), counter.getPulledCount(),
                counter.getSavedCount(), counter.getLastRowId());
        return new BusinessSyncResult(
                businessCode(),
                counter.getPageCount(),
                counter.getPulledCount(),
                counter.getSavedCount(),
                counter.getLastRowId());
    }

    /** 信号量版的故障通知 — 唤醒对端线程使其能退出 */
    private void recordSemaphoreFailure(AtomicReference<RuntimeException> failure,
                                        AtomicReference<List<S>> pageRef,
                                        AtomicBoolean lastBatch,
                                        Semaphore peerTurn,
                                        RuntimeException exception) {
        if (failure.compareAndSet(null, exception)) {
            log.error("[信号量版] 业务 {} 异常终止: {}", businessCode(), exception.getMessage(), exception);
            pageRef.set(List.of());
            lastBatch.set(true);
            peerTurn.release(); // 唤醒对端线程（即使没有 permit 也强制释放）
        }
    }

    /**
     * 拉取线程（生产者）：
     * - 基于游标（ID 大小）分页查询，每次取 pageSize 条
     * - 通过游标推进防御死循环：如果最后一条的 ID <= 当前游标，说明游标没动，立即终止
     * - 每页放入队列后回调 progressListener，让前端能实时看到进度
     * - 拉取数量不足 pageSize 说明已到尾页，结束循环
     * - 异常时通过 recordFailure 通知对端线程
     */
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

    /**
     * 落库线程（消费者）：
     * - 阻塞等待队列数据（queue.take() — 无数据时线程挂起，不占 CPU）
     * - 收到哨兵 end() 对象后退出循环（优雅终止）
     * - 逐条处理：transform() 转换 → save() 写入中台库 → markSourceRowSynced() 标记已同步
     * - 每页处理完后回调 progressListener
     */
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
