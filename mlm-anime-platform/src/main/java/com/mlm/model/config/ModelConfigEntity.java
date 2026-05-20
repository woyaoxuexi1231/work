package com.mlm.model.config;

import com.baomidou.mybatisplus.annotation.*;
import com.mlm.common.enums.ModelType;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型配置实体 — 对应数据库 model_config 表
 * <p>
 * 每个厂商（openai / stable_diffusion / kling）+ 模型类型（文生文/文生图/图生视频）一条配置。
 * ModelGateway 根据任务的 vendor + modelType 加载对应配置来发起 API 调用和轮询。
 *
 * @see com.mlm.model.config.ModelConfigLoader
 * @see com.mlm.model.core.ModelGateway
 */
@Data
@NoArgsConstructor
@TableName("model_config")
public class ModelConfigEntity {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 厂商标识：openai / stable_diffusion / kling */
    private String vendor;

    /** 模型类型 */
    private ModelType modelType;

    /** API 端点地址 */
    private String apiEndpoint;

    /** API 密钥 */
    private String apiKey;

    /** 任务轮询间隔（秒） */
    private Integer pollInterval = 30;

    /** 最大轮询次数 */
    private Integer maxPollCount = 60;

    /** 格式校验失败后最大重试次数 */
    private Integer maxRetries = 3;

    /** 是否启用 */
    private Boolean isEnabled = true;
}
