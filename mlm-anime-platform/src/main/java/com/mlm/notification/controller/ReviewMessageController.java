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
 * 审核消息接口 — 全部 POST
 */
@RestController
@RequestMapping("/api/notifications")
public class ReviewMessageController {

    private static final Logger log = LoggerFactory.getLogger(ReviewMessageController.class);

    private final ReviewMessageService messageService;

    public ReviewMessageController(ReviewMessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/list")
    public ApiResult<List<ReviewMessage>> listUnread() {
        return ApiResult.ok(messageService.findUnread());
    }

    @PostMapping("/count")
    public ApiResult<Map<String, Long>> count() {
        return ApiResult.ok(Map.of("count", messageService.countUnread()));
    }

    @PostMapping("/read")
    public ApiResult<?> markAsRead(@RequestBody List<Long> ids) {
        messageService.markAsRead(ids.toArray(new Long[0]));
        return ApiResult.ok();
    }
}
