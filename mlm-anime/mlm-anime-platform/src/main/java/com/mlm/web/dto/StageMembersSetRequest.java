package com.mlm.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量设置阶段负责人请求 DTO
 *
 * @author mlm
 */
@Data
@NoArgsConstructor
public class StageMembersSetRequest {
    /** 项目 ID */
    private Long projectId;
    /** 阶段负责人列表 */
    private List<StageMemberItem> members;

    /**
     * 阶段负责人条目
     */
    @Data
    @NoArgsConstructor
    public static class StageMemberItem {
        /** 阶段编码（EpisodeStatus 的 int code） */
        private Integer stage;
        /** 负责人用户 ID */
        private Long userId;
    }
}
