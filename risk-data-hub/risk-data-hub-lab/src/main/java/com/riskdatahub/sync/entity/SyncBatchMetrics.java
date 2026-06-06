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

    // ====== 各阶段时间戳（由外部自行计算耗时） ======
    private LocalDateTime fetchStartedAt;      // 拉取开始
    private LocalDateTime fetchQueuedAt;       // 拉取完成(入队)
    private LocalDateTime processStartedAt;    // 开始处理(出队)
    private LocalDateTime idGenStartedAt;      // ID生成开始
    private LocalDateTime idGenFinishedAt;     // ID生成完成
    private LocalDateTime transformStartedAt;  // 转换开始
    private LocalDateTime transformFinishedAt; // 转换完成
    private LocalDateTime saveStartedAt;       // 落库开始
    private LocalDateTime cacheLookupFinishedAt; // 查缓存完成
    private LocalDateTime insertFinishedAt;    // 新增写入完成
    private LocalDateTime cacheAddFinishedAt;  // 写缓存完成
    private LocalDateTime globalIdQueryFinishedAt; // 查主键完成
    private LocalDateTime setIdFinishedAt;     // 设主键完成
    private LocalDateTime updateFinishedAt;    // 更新写入完成
    private LocalDateTime saveFinishedAt;      // 落库完成(本批结束)

    private LocalDateTime recordedAt;
}
