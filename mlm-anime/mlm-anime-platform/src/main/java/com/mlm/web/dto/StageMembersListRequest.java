package com.mlm.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询阶段负责人列表请求 DTO
 *
 * @author mlm
 */
@Data
@NoArgsConstructor
public class StageMembersListRequest {
    /** 项目 ID */
    private Long projectId;
}
