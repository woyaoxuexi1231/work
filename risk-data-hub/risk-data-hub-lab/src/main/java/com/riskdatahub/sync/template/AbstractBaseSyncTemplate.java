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

@Slf4j
public abstract class AbstractBaseSyncTemplate<S, T> implements BusinessSyncTemplate, ApplicationEventPublisherAware {

    protected final RoutingMybatisExecutor routingMybatisExecutor;
    protected final LeafSegmentService leafSegmentService;
    protected final MessageOutboxService messageOutboxService;
    private ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final java.util.Queue<Long> batchIdQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    protected final ThreadPoolExecutor pairExecutor;

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

    protected void publishProgress(Long taskId, String businessCode, int pulledCount, int savedCount) {
        if (taskId == null) return;
        eventPublisher.publishEvent(new SyncProgressEvent(taskId, businessCode, pulledCount, savedCount));
    }

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

    protected void preAllocateBatchIds(String tag, int count, SyncMetrics metrics) {
        batchIdQueue.clear();
        metrics.stampIdGenStarted();
        long[] ids = leafSegmentService.nextIdBatch(tag, count);
        for (long id : ids) batchIdQueue.add(id);
        metrics.stampIdGenFinished();
    }

    protected long nextId(String tag) {
        Long id = batchIdQueue.poll();
        return id != null ? id : leafSegmentService.nextId(tag);
    }

    protected abstract String getIdTag();

    protected void recordBatchMetrics(BusinessSyncContext context, SyncMetrics metrics) {
        Long recordId = context.getBusinessRecordIds().get(businessCode());
        if (batchMetricsMapper == null) {
            log.warn("[同步模板] batchMetricsMapper 未注入，跳过批次时间节点记录");
            return;
        }
        if (recordId == null) {
            log.warn("[同步模板] businessRecordIds 中找不到业务 {}，跳过批次时间节点记录", businessCode());
            return;
        }
        try {
            SyncBatchMetrics row = toBatchMetricsRow(metrics);
            row.setId(leafSegmentService.nextId("sync_batch_metrics"));
            row.setRecordId(recordId);
            row.setRecordedAt(now());
            batchMetricsMapper.insert(row);
        } catch (Exception e) {
            log.warn("[同步模板] 记录批次时间节点失败: {}", e.getMessage(), e);
        }
    }

    private SyncBatchMetrics toBatchMetricsRow(SyncMetrics m) {
        SyncBatchMetrics row = new SyncBatchMetrics();
        row.setBatchNo(m.getBatchNo());
        row.setPulledCount(m.getPulledCount());
        row.setSavedCount(m.getSavedCount());
        row.setInsertCount(m.getInsertCount());
        row.setUpdateCount(m.getUpdateCount());
        row.setFetchStartedAt(m.getFetchStartedAt());
        row.setFetchFinishedAt(m.getFetchFinishedAt());
        row.setFetchQueuedAt(m.getFetchQueuedAt());
        row.setProcessStartedAt(m.getProcessStartedAt());
        row.setProcessFinishedAt(m.getProcessFinishedAt());
        row.setIdGenStartedAt(m.getIdGenStartedAt());
        row.setIdGenFinishedAt(m.getIdGenFinishedAt());
        row.setTransformStartedAt(m.getTransformStartedAt());
        row.setTransformFinishedAt(m.getTransformFinishedAt());
        row.setSaveStartedAt(m.getSaveStartedAt());
        row.setCacheLookupStartedAt(m.getCacheLookupStartedAt());
        row.setCacheLookupFinishedAt(m.getCacheLookupFinishedAt());
        row.setInsertStartedAt(m.getInsertStartedAt());
        row.setInsertFinishedAt(m.getInsertFinishedAt());
        row.setCacheAddStartedAt(m.getCacheAddStartedAt());
        row.setCacheAddFinishedAt(m.getCacheAddFinishedAt());
        row.setGlobalIdQueryStartedAt(m.getGlobalIdQueryStartedAt());
        row.setGlobalIdQueryFinishedAt(m.getGlobalIdQueryFinishedAt());
        row.setSetIdStartedAt(m.getSetIdStartedAt());
        row.setSetIdFinishedAt(m.getSetIdFinishedAt());
        row.setUpdateStartedAt(m.getUpdateStartedAt());
        row.setUpdateFinishedAt(m.getUpdateFinishedAt());
        row.setSaveFinishedAt(m.getSaveFinishedAt());
        return row;
    }

    protected abstract List<S> fetchPage(BusinessSyncContext context, long lastId, int pageSize);

    protected abstract long sourceRowId(S row);

    protected abstract T transform(BusinessSyncContext context, S row);

    protected abstract void saveBatch(BusinessSyncContext context, List<T> targets, SyncMetrics metrics);
}
