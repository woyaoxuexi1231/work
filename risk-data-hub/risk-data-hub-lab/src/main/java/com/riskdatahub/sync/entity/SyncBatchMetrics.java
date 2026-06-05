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
    private Long idGenDurationMs;         // 中 Leaf ID 生成耗时
    private Long cacheLookupDurationMs;   // ④查重：查已有sourceRowId耗时
    private Long splitCheckMs;            // ⑤拆分：将数据分为insert/update两类的耗时
    private Long saveDurationMs;          // ⑥落库：saveBatch总耗时（= ④+⑤+⑥INSERT+⑥INSERT写缓存+⑥UPDATE+…）
    private Long insertDurationMs;        // ⑥INSERT：批量新增耗时
    private Long cacheAddDurationMs;      // ⑥写缓存：新增后写入Redis缓存耗时
    private Long globalIdQueryDurationMs; // ⑦查ID：查询已有行的globalId耗时
    private Long setIdDurationMs;         // ⑦设ID：将globalId设到实体上的耗时
    private Long updateDurationMs;        // ⑧UPDATE：批量更新耗时
    private Long totalPageMs;             // 本页总耗时（拉取→转换→落库，不含排队）

    // ====== 速率 ======
    private Double rowsPerSecond;         // 本页处理速率（条/秒）

    private LocalDateTime recordedAt;
}
