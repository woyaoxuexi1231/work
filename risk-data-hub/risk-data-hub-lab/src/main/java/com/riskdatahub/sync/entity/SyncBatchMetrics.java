package com.riskdatahub.sync.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sync_batch_metrics")
public class SyncBatchMetrics {

    private Long id;

    // ====== 关联信息 ======
    private Long recordId;
    private Integer batchNo;

    // ====== 数据量 ======
    private Integer pulledCount;
    private Integer savedCount;
    private Integer insertCount;
    private Integer updateCount;

    // ====== 各阶段耗时（毫秒） ======
    private Long fetchDurationMs;         // 本页拉取耗时
    private Long queueWaitMs;             // 在队列中等待耗时（fetch完成→insert开始处理）
    private Long transformDurationMs;     // 转换耗时（所有行转换累加）
    private Long cacheLookupDurationMs;   // 查重缓存耗时
    private Long saveDurationMs;          // 落库总耗时（含查globalId + INSERT + UPDATE）
    private Long insertDurationMs;        // 批量 INSERT 耗时
    private Long globalIdQueryDurationMs; // 查 globalId 耗时
    private Long updateDurationMs;        // 批量 UPDATE 耗时
    private Long totalPageMs;             // 本页总耗时（拉取→转换→落库，不含排队）

    // ====== 速率 ======
    private Double rowsPerSecond;         // 本页处理速率（条/秒）

    private LocalDateTime recordedAt;
}
