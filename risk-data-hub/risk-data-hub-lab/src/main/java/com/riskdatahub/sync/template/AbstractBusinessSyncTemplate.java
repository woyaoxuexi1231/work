package com.riskdatahub.sync.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskdatahub.common.util.TimeUtils;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.message.MessageOutboxService;
import com.riskdatahub.sync.model.BusinessSyncContext;
import com.riskdatahub.sync.model.SyncSupport.BusinessSyncResult;
import com.riskdatahub.sync.model.SyncSupport.PageChunk;
import com.riskdatahub.sync.model.SyncSupport.SyncCounter;
import com.riskdatahub.sync.model.SyncSupport.SyncProgress;
import com.riskdatahub.sync.model.SyncSupport.SyncProgressListener;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 同步模板骨架类 — 生产者-消费者双线程模式 + 模板方法模式。
 * <p>
 * <b>模板方法模式：</b>{@code execute()} 用 {@code final} 修饰，固定同步流程：
 * 拉取 → 转换 → 落库 → 标记已同步。子类通过覆盖 5 个抽象方法定制每个步骤的具体实现。
 * </p>
 * <p>
 * <b>生产者-消费者模式：</b>每类业务内部使用两个线程：
 * <ul>
 *   <li>拉取线程（生产者）：从上游分页拉取未同步数据，放入 {@link ArrayBlockingQueue}</li>
 *   <li>落库线程（消费者）：从队列读取数据，逐条转换并写入中台库</li>
 * </ul>
 * 通过容量为 4 的有界队列实现背压（back pressure），避免内存被未落库的数据撑爆。
 * </p>
 *
 * @param <S> 上游源数据类型
 * @param <T> 中台目标实体类型
 * @author risk-data-hub
 */
@Slf4j
public abstract class AbstractBusinessSyncTemplate<S, T> implements BusinessSyncTemplate {

    /** MyBatis 路由执行器 — 用于在指定数据源上执行 SQL */
    protected final RoutingMybatisExecutor routingMybatisExecutor;

    /** Leaf 号段 ID 生成器 — 生成中台表全局唯一 ID */
    protected final LeafSegmentService leafSegmentService;

    /** 消息发件箱 — 同步完成后写入事件消息 */
    protected final MessageOutboxService messageOutboxService;

    /** Jackson ObjectMapper — 用于构建 JSON 消息体 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 当前业务类型的双线程池（每个业务 2 线程：拉取 + 落库） */
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
    public final BusinessSyncResult execute(BusinessSyncContext context,
                                            SyncProgressListener progressListener) throws Exception {
        BlockingQueue<PageChunk<S>> queue = new ArrayBlockingQueue<>(4);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        SyncCounter counter = new SyncCounter();

        log.info("[同步模板] 业务 {} 开始执行，数据源={}, 类型={}, 分页大小={}, 批次号={}",
                businessCode(), context.getDataSourceKey(), context.getDatasourceType(),
                context.getPageSize(), context.getBatchNo());

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
                businessCode(), counter.getPageCount(), counter.getPulledCount(),
                counter.getSavedCount(), counter.getLastRowId());
        return new BusinessSyncResult(
                businessCode(),
                counter.getPageCount(),
                counter.getPulledCount(),
                counter.getSavedCount(),
                counter.getLastRowId());
    }

    /**
     * 拉取线程（生产者）：基于游标分页查询，通过有界队列传递给落库线程。
     * <p>
     * 游标推进防御死循环：如果最后一条记录的 ID 不超过当前游标，立即终止。
     * </p>
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
                progressListener.onProgress(new SyncProgress(
                        businessCode(), "FETCHED", pageNo,
                        counter.getPulledCount(), counter.getSavedCount(), counter.getLastRowId()));
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
     * <p>
     * 收到哨兵 end() 对象后退出循环（优雅终止）。
     * </p>
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
                        businessCode(), "SAVED", chunk.getPageNo(),
                        counter.getPulledCount(), counter.getSavedCount(), counter.getLastRowId()));
            }
        } catch (Exception e) {
            recordFailure(queue, failure, new IllegalStateException(
                    businessCode() + " 落库线程失败: " + e.getMessage(), e));
        }
    }

    /**
     * 记录故障信号并通知对端线程终止。
     * <p>
     * CAS 保证"第一个失败"的线程写入自己的异常作为根因，
     * 后续失败不再覆盖。clear + offer(end) 确保对端不会永远阻塞在 take() 上。
     * </p>
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

    /**
     * 同步完成后向 event_message 表写入完成事件（发件箱模式）。
     * <p>
     * 使用 Jackson ObjectMapper 序列化，避免字符串拼接构建 JSON。
     * </p>
     */
    private void publishBusinessSummaryEvent(BusinessSyncContext context, SyncCounter counter) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("businessCode", businessCode());
            payload.put("dataSourceKey", context.getDataSourceKey());
            payload.put("datasourceType", context.getDatasourceType());
            payload.put("batchNo", context.getBatchNo());
            payload.put("pulledCount", counter.getPulledCount());
            payload.put("savedCount", counter.getSavedCount());
            String json = objectMapper.writeValueAsString(payload);
            messageOutboxService.publish(
                    "risk.sync.business.completed",
                    context.getBatchNo() + ":" + businessCode(),
                    json);
        } catch (JsonProcessingException e) {
            log.error("[同步模板] 发布事件序列化失败: {}", e.getMessage(), e);
        }
    }

    // ==================== 子类需实现的抽象方法 ====================

    /**
     * 分页拉取上游未同步数据。
     *
     * @param context  同步上下文
     * @param lastId   上一页最后一条记录的 ID（游标）
     * @param pageSize 每页大小
     * @return 本页数据列表
     */
    protected abstract List<S> fetchPage(BusinessSyncContext context, long lastId, int pageSize);

    /**
     * 获取源数据行的 ID（用于游标推进）。
     *
     * @param row 源数据行
     * @return 行 ID
     */
    protected abstract long sourceRowId(S row);

    /**
     * 将上游数据行转换为中台实体。
     *
     * @param context 同步上下文
     * @param row     上游数据行
     * @return 中台实体
     */
    protected abstract T transform(BusinessSyncContext context, S row);

    /**
     * 将转换后的中台实体写入数据库。
     *
     * @param target 中台实体
     */
    protected abstract void save(T target);

    /**
     * 将源数据标记为已同步（设置 sync_flag = 1）。
     *
     * @param context 同步上下文
     * @param rowId   源数据行 ID
     */
    protected abstract void markSourceRowSynced(BusinessSyncContext context, long rowId);

    /**
     * 获取当前时间的格式化字符串。
     *
     * @return "yyyy-MM-dd HH:mm:ss" 格式的时间字符串
     */
    protected String now() {
        return TimeUtils.now();
    }
}
