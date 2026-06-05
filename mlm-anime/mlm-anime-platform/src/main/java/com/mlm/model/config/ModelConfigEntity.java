package com.mlm.model.config;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mlm.common.enums.ModelType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

/**
 * 模型配置实体 — 对应数据库 model_config 表
 * <p>
 * 存储各 AI 厂商的接入配置，包括 API 端点、密钥、轮询参数等。
 * 配置通过 {@link com.mlm.config.DataInitializer} 在首次启动时初始化，
 * 或在管理后台进行增删改查。
 * <p>
 * 【安全说明】
 * {@link #apiKey} 字段标记了 {@link JsonIgnore}，确保 HTTP 响应中
 * 不会泄露 API 密钥。密钥仅在 {@link ModelConfigLoader} 加载配置时
 * 内部使用。
 *
 * @author mlm
 * @see ModelConfigLoader
 * @see com.mlm.model.core.ModelGateway
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@TableName("model_config")
public class ModelConfigEntity {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 厂商标识：openai / stable_diffusion / kling */
    private String vendor;

    /** 模型类型：文生文 / 文生图 / 图生视频 */
    private ModelType modelType;

    /** API 接口地址 */
    private String apiEndpoint;

    /** API 密钥（JSON 序列化时忽略，防止泄露） */
    @JsonIgnore
    private String apiKey;

    /** 轮询间隔（秒），默认 30 秒 */
    private Integer pollInterval = 30;

    /** 最大轮询次数，默认 60 次 */
    private Integer maxPollCount = 60;

    /** 格式校验失败后的最大重试次数，默认 3 次 */
    private Integer maxRetries = 3;

    /** 是否启用：true=启用，false=禁用 */
    private Boolean isEnabled = true;
}
