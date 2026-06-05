package com.mlm.common.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知计数 VO — 未读消息数量的视图对象
 * <p>
 * 用于前端消息铃铛显示未读消息数量。
 *
 * @author mlm
 * @see com.mlm.notification.controller.ReviewMessageController
 */
@Data
@NoArgsConstructor
public class NotificationCountVO {

    /** 未读消息数量 */
    private long count;
}
