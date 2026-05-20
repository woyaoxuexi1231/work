package com.mlm.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建项目请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCreateRequest {
    private String name;
    private Long resourceId;
}
