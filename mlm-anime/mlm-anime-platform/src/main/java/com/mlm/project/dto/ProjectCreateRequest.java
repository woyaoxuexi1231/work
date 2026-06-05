package com.mlm.project.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建项目请求 DTO。
 */
@Data
@NoArgsConstructor
public class ProjectCreateRequest {
    /** 项目名称 */
    private String name;
    /** 可选：引用资源 ID */
    private Long resourceId;
}
