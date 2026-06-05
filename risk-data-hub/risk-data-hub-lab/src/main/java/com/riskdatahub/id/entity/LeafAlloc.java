package com.riskdatahub.id.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Leaf 号段发号器分配记录实体。
 * <p>
 * 对应数据库表 {@code leaf_alloc}，记录每个业务标签（biz_tag）当前已分配的最大 ID 和步长。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@TableName("leaf_alloc")
public class LeafAlloc {

    /** 业务标签（如 clean_trade / clean_stock） */
    private String bizTag;

    /** 当前已分配的最大 ID */
    private Long maxId;

    /** 号段步长 */
    private Integer step;

    /** 业务描述 */
    private String description;
}
