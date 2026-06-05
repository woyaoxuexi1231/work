package com.mlm.notification.controller;

import com.mlm.common.dto.ApiResult;
import com.mlm.common.dto.NotificationCountVO;
import com.mlm.notification.entity.ReviewMessage;
import com.mlm.notification.service.ReviewMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审核消息控制器 — 消息的查询、计数和已读标记
 * <p>
 * 【职责】
 * <ul>
 *   <li>消息列表（list）— 查询所有未读消息</li>
 *   <li>未读计数（count）— 查询未读消息数量</li>
 *   <li>标记已读（read）— 批量标记消息为已读</li>
 * </ul>
 * <p>
 * 消息由各 StepHandler（如 {@link com.mlm.pipeline.handler.ReviewStepHandler}、
 * {@link com.mlm.pipeline.handler.ApprovalStepHandler}）在剧集进入审核状态时创建，
 * 前端通过此控制器拉取和消费消息。
 *
 * @author mlm
 * @see ReviewMessage 审核消息实体
 * @see ReviewMessageService 审核消息服务
 */
@RestController
@RequestMapping("/api/notifications")
public class ReviewMessageController {

    private static final Logger log = LoggerFactory.getLogger(ReviewMessageController.class);

    private final ReviewMessageService messageService;

    /**
     * 构造审核消息控制器
     *
     * @param messageService 审核消息服务
     */
    public ReviewMessageController(ReviewMessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 查询未读消息列表
     *
     * @return 未读消息列表（按创建时间倒序）
     */
    @PostMapping("/list")
    public ApiResult<List<ReviewMessage>> listUnread() {
        List<ReviewMessage> messages = messageService.findUnread();
        log.debug("未读消息查询: count={}", messages.size());
        return ApiResult.ok(messages);
    }

    /**
     * 查询未读消息数量
     *
     * @return 未读计数
     */
    @PostMapping("/count")
    public ApiResult<NotificationCountVO> count() {
        NotificationCountVO vo = new NotificationCountVO();
        vo.setCount(messageService.countUnread());
        return ApiResult.ok(vo);
    }

    /**
     * 批量标记消息为已读
     *
     * @param ids 要标记为已读的消息 ID 列表
     * @return 操作成功
     */
    @PostMapping("/read")
    public ApiResult<Void> markAsRead(@RequestBody List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            messageService.markAsRead(ids.toArray(new Long[0]));
            log.debug("消息标记已读: ids={}", ids);
        }
        return ApiResult.ok();
    }
}
