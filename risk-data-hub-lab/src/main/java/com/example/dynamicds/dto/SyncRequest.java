package com.example.dynamicds.dto;

import lombok.Data;

/**
 * 同步任务请求体 — 封装数据源 key 和分页大小。
 */
@Data
public class SyncRequest {
    private String dataSourceKey;
    private int pageSize = 2;
}
