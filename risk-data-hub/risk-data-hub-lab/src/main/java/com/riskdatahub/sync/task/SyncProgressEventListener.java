package com.riskdatahub.sync.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.sync.model.SyncProgressEvent;
import com.riskdatahub.sync.task.entity.SyncBusinessRecord;
import com.riskdatahub.sync.task.entity.SyncTask;
import com.riskdatahub.sync.task.mapper.SyncBusinessRecordMapper;
import com.riskdatahub.sync.task.mapper.SyncTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同步进度事件监听器 — 将实时同步进度写入 sync_task 表和 sync_business_record 表。
 * <p>
 * 通过 Spring {@link EventListener} 订阅 {@link SyncProgressEvent}，
 * 每 1 秒最多写一次 DB（按任务 ID 独立节流），避免频繁写库。
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncProgressEventListener {

    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final SyncTaskMapper syncTaskMapper;
    private final SyncBusinessRecordMapper syncBusinessRecordMapper;

    /** 每个任务最近一次写入进度的时间戳（ms），用于节流 */
    private final ConcurrentHashMap<Long, Long> lastDbWriteTimes = new ConcurrentHashMap<>();

    /**
     * 处理同步进度事件。
     * <p>同任务每秒最多写一次 DB，其余事件直接跳过。
     * 同时更新 sync_task.message（概要）和 sync_business_record（明细）。</p>
     *
     * @param event 同步进度事件
     */
    @EventListener
    public void handleProgress(SyncProgressEvent event) {
        // 节流：每秒最多写一次，避免频繁写库
        // 但 pulledCount == savedCount（终态）时强制写入，避免前端永远看到"堆积中"
        boolean isFinal = event.getPulledCount() == event.getSavedCount();
        long now = System.currentTimeMillis();
        Long lastWrite = lastDbWriteTimes.get(event.getTaskId());
        if (!isFinal && lastWrite != null && now - lastWrite < 1000) {
            return;
        }
        lastDbWriteTimes.put(event.getTaskId(), now);

        routingMybatisExecutor.run(HubConstants.DS_HUB, () -> {
            // 更新 sync_business_record 实时明细
            LambdaUpdateWrapper<SyncBusinessRecord> wrapper = new LambdaUpdateWrapper<SyncBusinessRecord>()
                    .eq(SyncBusinessRecord::getTaskId, event.getTaskId())
                    .eq(SyncBusinessRecord::getBusinessCode, event.getBusinessCode())
                    .set(SyncBusinessRecord::getPulledCount, event.getPulledCount())
                    .set(SyncBusinessRecord::getSavedCount, event.getSavedCount());
            syncBusinessRecordMapper.update(null, wrapper);

            // 计算总进度：所有业务 saved 之和 / pulled 之和，取百分比
            List<SyncBusinessRecord> records = syncBusinessRecordMapper.selectList(
                    new LambdaQueryWrapper<SyncBusinessRecord>()
                            .eq(SyncBusinessRecord::getTaskId, event.getTaskId()));
            int totalPulled = records.stream().mapToInt(r -> r.getPulledCount() != null ? r.getPulledCount() : 0).sum();
            int totalSaved = records.stream().mapToInt(r -> r.getSavedCount() != null ? r.getSavedCount() : 0).sum();
            int progress = totalPulled > 0 ? Math.min(99, (totalSaved * 100) / totalPulled) : 0;

            // 更新 sync_task 的概要信息、总数、进度
            syncTaskMapper.update(null, new LambdaUpdateWrapper<SyncTask>()
                    .eq(SyncTask::getId, event.getTaskId())
                    .set(SyncTask::getMessage,
                            "正在同步 " + event.getBusinessCode()
                                    + ": 已拉取 " + event.getPulledCount()
                                    + ", 已落库 " + event.getSavedCount())
                    .set(SyncTask::getTotalPulledCount, totalPulled)
                    .set(SyncTask::getTotalSavedCount, totalSaved)
                    .set(SyncTask::getProgress, progress));
        });
    }
}
