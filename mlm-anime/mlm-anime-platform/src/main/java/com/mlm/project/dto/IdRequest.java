package com.mlm.project.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用 ID 请求 DTO — 只需传入一个 ID 的场景。
 */
@Data
@NoArgsConstructor
public class IdRequest {
    /** 实体 ID */
    private Long id;
}
