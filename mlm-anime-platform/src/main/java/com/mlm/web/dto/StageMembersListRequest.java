package com.mlm.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 阶段成员列表请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageMembersListRequest {
    private Long projectId;
}
