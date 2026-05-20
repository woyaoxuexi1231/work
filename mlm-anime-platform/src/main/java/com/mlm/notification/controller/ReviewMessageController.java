package com.mlm.notification.controller;

import com.mlm.common.dto.ApiResult;
import com.mlm.notification.entity.ReviewMessage;
import com.mlm.notification.service.ReviewMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 审核消息 REST 接口
 * <p>
 * 前端消息铃铛通过此接口查询未读消息。
 * 消息的写入由 Pipeline Handler 在进入审核状态时自动触发。
 */
@RestController
@RequestMapping("/api/notifications")
public class ReviewMessageController {

    private static final Logger log = LoggerFactory.getLogger(ReviewMessageController.class);

    private final ReviewMessageService messageService;

    public ReviewMessageController(ReviewMessageService messageService) {
        this.messageService = messageService;
    }

    /** 未读消息列表 */
    @GetMapping
    public ApiResult<List<ReviewMessage>> listUnread() {
        return ApiResult.ok(messageService.findUnread());
    }

    /** 未读消息数量（用于前端 badge 显示） */
    @GetMapping("/count")
    public ApiResult<Map<String, Long>> count() {
        return ApiResult.ok(Map.of("count", messageService.countUnread()));
    }

    /** 标记已读 */
    @PostMapping("/read")
    public ApiResult<?> markAsRead(@RequestBody List<Long> ids) {
        messageService.markAsRead(ids.toArray(new Long[0]));
        return ApiResult.ok();
    }
}
