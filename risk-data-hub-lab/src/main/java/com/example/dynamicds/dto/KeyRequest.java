package com.example.dynamicds.dto;

import lombok.Data;

/**
 * 通用 key 请求体 — 用于根据 key 查询或删除数据源等操作。
 */
@Data
public class KeyRequest {
    private String key;
}
