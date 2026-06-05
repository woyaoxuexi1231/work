package com.mlm.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建项目请求 DTO
 *
 * @author mlm
 */
@Data
@NoArgsConstructor
public class ProjectCreateRequest {
    /** 项目名称 */
    private String name;
    /** 可选：引用资源 ID（从资源库创建时传入） */
    private Long resourceId;
}
