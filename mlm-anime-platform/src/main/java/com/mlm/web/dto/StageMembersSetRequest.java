package com.mlm.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 设置阶段成员请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageMembersSetRequest {
    private Long projectId;
    private List<StageMemberItem> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageMemberItem {
        private Integer stage;
        private Long userId;
    }
}
