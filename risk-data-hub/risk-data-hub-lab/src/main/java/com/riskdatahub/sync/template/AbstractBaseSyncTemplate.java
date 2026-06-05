package com.riskdatahub.sync.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskdatahub.common.util.TimeUtils;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.id.LeafSegmentService;
import com.riskdatahub.message.MessageOutboxService;
import com.riskdatahub.sync.entity.SyncBatchMetrics;
import com.riskdatahub.sync.mapper.SyncBatchMetricsMapper;
import com.riskdatahub.sync.model.BusinessSyncContext;
import com.riskdatahub.sync.model.SyncProgressEvent;
import com.riskdatahub.sync.model.SyncSupport.SyncCounter;
import com.riskdatahub.sync.model.SyncSupport.SyncMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import java.time.LocalDateTime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 同步模板顶层抽象 — 提取队列模式和信号量模式共有的字段、工具方法和抽象方法。
 * <p>
 * 子层抽象 {@link AbstractBusinessSyncTemplate}（队列模式）和 {@link AbstractSemaphoreSyncTemplate}（信号量模式）
 * 分别实现不同的拉取-落库协调策略，子层抽象共享本类的公共能力。
 * </p>
 *
 * @param <S> 上游源数据类型
 * @param <T> 中台目标实体类型
 * @author risk-data-hub
 */
@Slf4j
public abstract class AbstractBaseSyncTemplate<S, T> implements BusinessSyncTemplate, ApplicationEventPublisherAware {

    /** MyBatis 路由执行器 — 用于在指定数据源上执行 SQL */
    protected final RoutingMybatisExecutor routingMybatisExecutor;

    /** Leaf 号段 ID 生成器 — 生成中台表全局唯一 ID */
    protected final LeafSegmentService leafSegmentService;

    /** 消息发件箱 — 同步完成后写入事件消息 */
    protected final MessageOutboxService messageOutboxService;

    /** Spring 事件发布器 — 发布进度事件，由监听器异步处理 */
    private ApplicationEventPublisher eventPublisher;

    /** Jackson ObjectMapper — 用于构建 JSON 消息体 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 当前业务类型的双线程池（每个业务 2 线程：拉取 + 落库） */
    protected final ThreadPoolExecutor pairExecutor;

    /** 批次耗时记录 Mapper（由 Spring 注入到基类） */
    @org.springframework.beans.factory.annotation.Autowired
    private SyncBatchMetricsMapper batchMetricsMapper;

    protected AbstractBaseSyncTemplate(RoutingMybatisExecutor routingMybatisExecutor,
                                        LeafSegmentService leafSegmentService,
                                        MessageOutboxService messageOutboxService,
                                        ThreadPoolExecutor pairExecutor) {
        this.routingMybatisExecutor = routingMybatisExecutor;
        this.leafSegmentService = leafSegmentService;
        this.messageOutboxService = messageOutboxService;
        this.pairExecutor = pairExecutor;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /** 发布进度事件，供前端实时展示同步进度 */
    protected void publishProgress(Long taskId, String businessCode, int pulledCount, int savedCount) {
        if (taskId == null) return;
        eventPublisher.publishEvent(new SyncProgressEvent(taskId, businessCode, pulledCount, savedCount));
    }

    /** 发布含耗时指标的进度事件（由落库线程每页完成后调用） */
    protected void publishProgressWithMetrics(Long taskId, String businessCode,
                                               int pulledCount, int savedCount,
                                               SyncMetrics metrics) {
        if (taskId == null) return;
        SyncProgressEvent event = new SyncProgressEvent(taskId, businessCode, pulledCount, savedCount);
        event.setFetchDurationMs(metrics.getFetchDurationMs());
        event.setTransformDurationMs(metrics.getTransformDurationMs());
        event.setSaveDurationMs(metrics.getSaveDurationMs());
        event.setFetchPageCount(metrics.getFetchPageCount());
        event.setSaveBatchCount(metrics.getSaveBatchCount());
        event.setMaxFetchPageMs(metrics.getMaxFetchPageMs());
        event.setMaxSaveBatchMs(metrics.getMaxSaveBatchMs());
        event.setCacheLookupDurationMs(metrics.getCacheLookupDurationMs());
        event.setBatchInsertDurationMs(metrics.getBatchInsertDurationMs());
        event.setGlobalIdQueryDurationMs(metrics.getGlobalIdQueryDurationMs());
        event.setBatchUpdateDurationMs(metrics.getBatchUpdateDurationMs());
        eventPublisher.publishEvent(event);
    }

    /**
     * 同步完成后向 event_message 表写入完成事件（发件箱模式）。
     * <p>使用 Jackson ObjectMapper 序列化，避免字符串拼接构建 JSON。</p>
     */
    protected void publishBusinessSummaryEvent(BusinessSyncContext context, SyncCounter counter) {
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

    protected LocalDateTime now() {
        return TimeUtils.now();
    }

    /**
     * 获取当前业务的初始游标（断点续传）。
     * <p>从 {@link BusinessSyncContext#getInitialCursors()} 中读取当前业务编码对应的游标，
     * 如果存在有效的上次成功游标则返回，否则返回 0（从头开始）。</p>
     *
     * @param context 同步上下文
     * @return 起始游标 ID，0 表示从头开始
     */
    protected long initialCursor(BusinessSyncContext context) {
        Map<String, Long> cursors = context.getInitialCursors();
        if (cursors != null) {
            Long cursor = cursors.get(businessCode());
            if (cursor != null && cursor > 0) {
                return cursor;
            }
        }
        return 0L;
    }

    /**
     * 记录单批落库耗时到 sync_batch_metrics 表。
     * <p>由 runInsertThread 每处理完一页后调用。</p>
     */
    protected void recordBatchMetrics(BusinessSyncContext context, int pageNo, int rowCount,
                                       long fetchMs, long queueWaitMs,
                                       long transformMs, long saveMs,
                                       SyncMetrics metrics) {
        Long recordId = context.getBusinessRecordIds().get(businessCode());
        if (batchMetricsMapper == null) {
            log.warn("[同步模板] batchMetricsMapper 未注入，跳过批次耗时记录");
            return;
        }
        if (recordId == null) {
            log.warn("[同步模板] businessRecordIds 中找不到业务 {}，跳过批次耗时记录", businessCode());
            return;
        }
        try {
            long totalMs = fetchMs + transformMs + saveMs;
            double rps = totalMs > 0 ? (double) rowCount / totalMs * 1000 : 0;

            SyncBatchMetrics m = new SyncBatchMetrics();
            m.setId(leafSegmentService.nextId("sync_batch_metrics"));
            m.setRecordId(recordId);
            m.setBatchNo(pageNo);
            m.setPulledCount(rowCount);
            m.setSavedCount(rowCount);
            m.setInsertCount(0);
            m.setUpdateCount(0);

            m.setFetchDurationMs(fetchMs > 0 ? fetchMs : 0);
            m.setQueueWaitMs(queueWaitMs > 0 ? queueWaitMs : 0);
            m.setTransformDurationMs(transformMs);
            m.setSaveDurationMs(saveMs);
            m.setTotalPageMs(totalMs);

            // 子步骤使用当批值（resetBatchSubTimings 每批清零），不是累计值
            m.setCacheLookupDurationMs(metrics.getLastCacheLookupMs());
            m.setInsertCount(metrics.getLastInsertCount());
            m.setInsertDurationMs(metrics.getLastBatchInsertMs());
            m.setGlobalIdQueryDurationMs(metrics.getLastGlobalIdQueryMs());
            m.setUpdateCount(metrics.getLastUpdateCount());
            m.setUpdateDurationMs(metrics.getLastBatchUpdateMs());

            m.setRowsPerSecond(Math.round(rps * 10) / 10.0);

            m.setRecordedAt(now());
            batchMetricsMapper.insert(m);
        } catch (Exception e) {
            log.warn("[同步模板] 记录批次耗时失败: {}", e.getMessage(), e);
        }
    }

    // ==================== 子类需实现的抽象方法 ====================

    protected abstract List<S> fetchPage(BusinessSyncContext context, long lastId, int pageSize);

    protected abstract long sourceRowId(S row);

    protected abstract T transform(BusinessSyncContext context, S row);

    protected abstract void saveBatch(BusinessSyncContext context, List<T> targets, SyncMetrics metrics);
}
