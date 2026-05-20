package com.mlm.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建项目请求
 */
@Data
@NoArgsConstructor
public class ProjectCreateRequest {
    private String name;
    private Long resourceId;
}
