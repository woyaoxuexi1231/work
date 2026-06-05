package com.riskdatahub.dictionary.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 字典项实体 — 存储状态码与中文名称的映射。
 * <p>
 * 对应数据库表 {@code dict_item}，字典类型 + 编码构成唯一键。
 * </p>
 *
 * @author risk-data-hub
 */
@Data
@TableName("dict_item")
public class DictItem {

    /** 主键 ID（Leaf 号段生成） */
    private Long id;

    /** 字典类型（如 trade_status_oms / trade_status_broker） */
    private String dictType;

    /** 字典编码（如 NEW / DONE / CANCEL） */
    private String dictCode;

    /** 字典名称（中文，如 "待确认" / "已成交"） */
    private String dictName;

    /** 字典描述 */
    private String dictDesc;
}
