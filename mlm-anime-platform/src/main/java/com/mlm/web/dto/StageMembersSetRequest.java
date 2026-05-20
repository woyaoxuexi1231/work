package com.mlm.web.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class StageMembersSetRequest {
    private Long projectId;
    private List<StageMemberItem> members;

    @Data
    @NoArgsConstructor
    public static class StageMemberItem {
        private Integer stage;
        private Long userId;
    }
}
