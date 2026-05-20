package com.mlm.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 获取项目详情请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectGetRequest {
    private Long id;
}
