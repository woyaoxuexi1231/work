package com.mlm.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知数量响应对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCountVO {
    private long count;
}
