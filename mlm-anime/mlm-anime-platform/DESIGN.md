# MLM 动漫平台 — 技术设计方案

## 一、架构总览

```
┌──────────────────────────────────────────────────────────────────┐
│              前端 (Thymeleaf + Tailwind CSS 毛玻璃)                 │
│              内嵌于 Spring Boot，无需独立部署                        │
└──────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────┐
│                    Spring Boot (单体应用)                          │
│  Controller → Pipeline Engine → Model Gateway → Resource Service │
└──────────────────────────────────────────────────────────────────┘
                                │
      ┌─────────────────────────┼─────────────────────────┐
      ▼                         ▼                         ▼
┌──────────────┐   ┌──────────────────┐   ┌──────────────────────┐
│  RabbitMQ    │   │   MySQL / H2     │   │  MinIO + Caffeine    │
│ (单队列串行)  │   │ (项目/任务/配置)  │   │  (文件存储 + 本地缓存) │
└──────────────┘   └──────────────────┘   └──────────────────────┘
```

---

## 二、Pipeline 数据流程

### 2.1 项目状态机

```
                    ┌──────────┐
                    │  DRAFT   │  剧本创作
                    └────┬─────┘
                         │ 创作完成 → 提交审核
                         ▼
                    ┌──────────┐
                    │  REVIEW  │  审核
                    └────┬─────┘
                         │ 审核通过              审核驳回 → DRAFT
                         ▼
                    ┌──────────┐
                    │STORYBOARD│  拆分镜
                    └────┬─────┘
                         │ 分镜完成
                         ▼
                    ┌──────────┐
                    │GENERATING│  AI 成片
                    └────┬─────┘
                         │ 生成完成
                         ▼
                    ┌──────────┐
                    │ APPROVAL │  终审
                    └────┬─────┘
                         │ 审批通过              审批驳回 → GENERATING
                         ▼
                    ┌──────────┐
                    │COMPLETED │  已完成
                    └──────────┘
```

### 2.2 单步骤内的异步任务子状态

每个 Pipeline 步骤如果涉及 AI 调用，内部子状态为：

```
PENDING ──→ PROCESSING ──→ SUCCESS
                │
                └──→ FAILED (可手动重试)
```

### 2.3 完整数据流

```
用户创建项目
    │
    ▼
[剧本创作] ──→ 调用大模型(异步) ──→ 轮询/回调获取结果 ──→ 保存 → 状态 → REVIEW
    │
    ▼
[审核] ──→ 人工审核 → 通过/驳回
    │
    ▼
[拆分镜] ──→ 调用大模型(异步) ──→ 格式校验(最多重试3次) ──→ 保存分镜 → 状态 → GENERATING
    │
    ▼
[AI成片] ──→ 文生图(多张) ──→ 图生视频 ──→ 合成 ──→ 状态 → APPROVAL
    │                │
    │                └── 每张图/每个视频都是独立异步任务，全部完成后该步骤才完成
    │
    ▼
[审批] ──→ 人工终审 → 通过(COMPLETED) / 驳回(GENERATING)
```

### 2.4 RabbitMQ 消息流转

```
Producer                          Consumer (单队列串行消费)
   │                                    │
   ├─ 项目ID + 目标步骤 ──→ [pipeline.queue] ──→ 消费
   │                                    │
   │                                    ├─ 1. 校验当前状态是否允许转换
   │                                    ├─ 2. 执行业务逻辑 (调AI/校验)
   │                                    ├─ 3. 成功 → 更新状态 → ACK
   │                                    └─ 4. 失败 → 判断重试次数
   │                                              │
   │                                    未超限: NACK (消息回队列)
   │                                    已超限: 标记 FAILED → ACK
```

**顺序性保障：** 单队列 + 单消费者，FIFO 消费。同一项目的状态更新消息按顺序入队，不会出现乱序。

**幂等性：** 状态更新 SQL 使用乐观锁 `UPDATE project SET status = ? WHERE id = ? AND status = ?`，重复消费不会产生副作用。

---

## 三、代码结构设计

```
mlm-anime-platform/
├── pom.xml                          # Maven 父 POM
├── mlm-common/                      # 公共模块
│   └── src/main/java/com/mlm/common/
│       ├── dto/                     # 统一 DTO
│       │   ├── ApiResult.java       # 统一响应体
│       │   ├── PageResult.java      # 分页响应
│       │   └── BaseModelDto.java    # AI 模型返回基类 (含扩展 Map)
│       ├── enums/
│       │   ├── ProjectStatus.java   # 项目状态枚举
│       │   ├── StepStatus.java      # 步骤子状态枚举
│       │   └── ModelType.java       # 模型类型枚举 (文生图/图生视频/文生文)
│       ├── exception/
│       │   ├── BizException.java
│       │   └── PipelineException.java
│       └── constant/
│           └── MqConstant.java      # 队列/交换机常量
│
├── mlm-pipeline/                    # Pipeline 引擎模块
│   └── src/main/java/com/mlm/pipeline/
│       ├── engine/
│       │   ├── PipelineEngine.java          # 核心引擎 (状态流转入口)
│       │   ├── StateMachine.java            # 状态机 (校验 + 转换)
│       │   ├── StepHandler.java             # 步骤处理器接口
│       │   └── StepHandlerRegistry.java     # 步骤处理器注册表
│       ├── handler/
│       │   ├── DraftStepHandler.java        # 剧本创作处理
│       │   ├── ReviewStepHandler.java       # 审核处理
│       │   ├── StoryboardStepHandler.java   # 拆分镜处理
│       │   ├── GeneratingStepHandler.java   # AI成片处理
│       │   └── ApprovalStepHandler.java     # 审批处理
│       ├── mq/
│       │   ├── PipelineProducer.java        # 消息生产者
│       │   └── PipelineConsumer.java        # 消息消费者 (单线程)
│       ├── scheduler/
│       │   └── TaskPollingScheduler.java    # 定时轮询AI任务状态
│       ├── service/
│       │   ├── ProjectService.java
│       │   └── TaskService.java
│       ├── dao/
│       │   ├── ProjectMapper.java
│       │   └── TaskMapper.java
│       └── entity/
│           ├── Project.java
│           └── Task.java
│
├── mlm-model/                       # 模型接入层模块
│   └── src/main/java/com/mlm/model/
│       ├── core/
│       │   ├── ModelGateway.java            # 顶层统一入口
│       │   ├── GenerateRequest.java         # 统一入参 (自定义通用参数)
│       │   ├── GenerateResponse.java        # 统一返回基类 (含扩展 Map)
│       │   └── ModelConfig.java             # 模型配置实体
│       ├── adapter/
│       │   ├── ModelAdapter.java            # 适配器接口 (generate + queryStatus)
│       │   ├── OpenAiAdapter.java           # OpenAI/DALL-E 适配器
│       │   ├── StableDiffusionAdapter.java  # SD 适配器
│       │   ├── KlingAdapter.java            # 可灵适配器
│       │   └── ...                          # 其他厂商适配器
│       ├── dto/
│       │   ├── OpenAiResponse.java          # 各厂商响应 DTO (继承基类)
│       │   ├── KlingResponse.java
│       │   └── ...
│       ├── config/
│       │   └── ModelConfigLoader.java       # 从 DB 加载模型参数配置
│       └── retry/
│           └── FormatValidator.java         # 返回格式校验 + 重试
│
├── mlm-resource/                    # 资源库模块
│   └── src/main/java/com/mlm/resource/
│       ├── service/
│       │   ├── ResourceService.java
│       │   └── OssService.java
│       ├── cache/
│       │   └── ResourceCache.java           # 本地缓存 (Caffeine, 3天TTL)
│       ├── dao/
│       │   └── ResourceMapper.java
│       └── entity/
│           └── Resource.java
│
├── mlm-api/                         # Web 层模块
│   └── src/main/java/com/mlm/api/
│       ├── controller/
│       │   ├── ProjectController.java       # 项目管理 API
│       │   ├── PipelineController.java      # Pipeline 控制 API (手动触发/重试)
│       │   ├── ResourceController.java      # 资源库 API
│       │   └── ModelController.java         # 模型调用 API
│       ├── config/
│       │   ├── RabbitMqConfig.java
│       │   └── AsyncConfig.java
│       └── Application.java                 # 启动类
│
└── mlm-sql/                         # 数据库脚本
    └── schema.sql
```

---

## 四、核心模块详解

### 4.1 Pipeline Engine (核心引擎)

```java
// PipelineEngine — 接收上层请求，触发状态流转
public class PipelineEngine {

    // 推动项目到下一步
    public void advance(Project project) {
        ProjectStatus current = project.getStatus();
        ProjectStatus next = StateMachine.next(current);  // 状态机决定下一跳
        StepHandler handler = StepHandlerRegistry.get(next);
        handler.handle(project);  // 各 handler 内部决定同步还是异步
    }

    // 停用/恢复: 手动重试某个失败步骤
    public void retry(Long projectId) {
        Project project = projectService.getById(projectId);
        // 重置当前步骤的子状态为 PENDING，重新入队
        project.setStepStatus(StepStatus.PENDING);
        pipelineProducer.send(project);
    }
}
```

**状态机规则 (StateMachine):**

```java
public class StateMachine {
    private static final Map<ProjectStatus, ProjectStatus> TRANSITIONS = Map.of(
        DRAFT,      REVIEW,
        REVIEW,     STORYBOARD,   // 审核通过 → 分镜; 驳回 → DRAFT (业务层处理)
        STORYBOARD, GENERATING,
        GENERATING, APPROVAL,
        APPROVAL,   COMPLETED     // 审批通过 → 完成; 驳回 → GENERATING
    );

    public static ProjectStatus next(ProjectStatus current) {
        return TRANSITIONS.get(current);
    }

    // 校验状态跳转是否合法
    public static boolean canTransition(ProjectStatus from, ProjectStatus to) {
        return TRANSITIONS.get(from) == to
            || (from == REVIEW && to == DRAFT)      // 审核驳回
            || (from == APPROVAL && to == GENERATING); // 审批驳回
    }
}
```

### 4.2 Model Access Layer (适配器模式)

```java
// 适配器接口
public interface ModelAdapter {
    // 提交生成任务，返回厂商任务ID
    String submit(GenerateRequest request);

    // 查询任务状态
    TaskStatus queryStatus(String vendorTaskId);

    // 将厂商返回转为统一响应
    GenerateResponse parseResult(Object vendorResponse);

    // 模型类型匹配 (文生图 / 图生视频 / 文生文)
    boolean supports(ModelType type, String vendor);
}

// 统一入口
public class ModelGateway {
    private final List<ModelAdapter> adapters;

    public GenerateResponse generate(GenerateRequest request) {
        ModelAdapter adapter = adapters.stream()
            .filter(a -> a.supports(request.getType(), request.getVendor()))
            .findFirst()
            .orElseThrow(() -> new BizException("无匹配的模型适配器"));

        // 1. 创建本地任务记录 (状态: PROCESSING)
        Task task = taskService.create(request);

        // 2. 提交给厂商 (异步)
        String vendorTaskId = adapter.submit(request);
        task.setVendorTaskId(vendorTaskId);
        taskService.save(task);

        // 3. 返回任务ID给业务层，业务层可轮询
        return GenerateResponse.processing(task.getId());
    }

    // 供定时任务调用: 轮询任务状态
    public void pollAndUpdate(Task task) {
        ModelAdapter adapter = findAdapter(task.getVendor(), task.getType());
        TaskStatus status = adapter.queryStatus(task.getVendorTaskId());

        switch (status) {
            case SUCCESS:
                GenerateResponse result = adapter.parseResult(status.getRawData());
                task.setStatus(StepStatus.SUCCESS);
                task.setResult(result);
                break;
            case FAILED:
                task.setStatus(StepStatus.FAILED);
                break;
            case PROCESSING:
                // 仍然处理中，等待下次轮询
                break;
        }
        taskService.save(task);
    }
}
```

**统一入参 (GenerateRequest):**

```java
public class GenerateRequest {
    private ModelType type;          // TEXT_TO_IMAGE / IMAGE_TO_VIDEO / TEXT_TO_TEXT
    private String vendor;           // openai / sd / kling / ...
    private String prompt;           // 提示词 (通用)
    private String negativePrompt;   // 反向提示词
    private Integer width;           // 图片/视频宽度
    private Integer height;          // 图片/视频高度
    private String referenceImageUrl;// 参考图 (图生视频时用)
    private Map<String, Object> extraParams; // 厂商特有参数
}
```

**统一返回基类 (GenerateResponse):**

```java
public class GenerateResponse {
    private Long taskId;             // 本地任务ID
    private StepStatus status;       // PROCESSING / SUCCESS / FAILED
    private String resultUrl;        // 生成结果 OSS URL
    private Map<String, Object> extensions; // 扩展字段 (厂商特有字段放这里)
}
```

### 4.3 定时轮询调度器

```java
@Component
public class TaskPollingScheduler {

    @Scheduled(fixedDelay = 60_000)  // 每分钟扫描一次待轮询任务
    public void pollProcessingTasks() {
        List<Task> tasks = taskService.findByStatus(StepStatus.PROCESSING);
        for (Task task : tasks) {
            // 根据模型配置决定是否到了轮询时间
            ModelConfig config = modelConfigLoader.load(task.getVendor(), task.getType());
            if (task.shouldPollNow(config.getPollIntervalSeconds())) {
                modelGateway.pollAndUpdate(task);
            }
        }
    }
}
```

### 4.4 RabbitMQ 单队列串行消费

```java
// 生产者
public class PipelineProducer {
    public void send(Project project) {
        rabbitTemplate.convertAndSend("pipeline.exchange", "pipeline.step", project.getId());
    }
}

// 消费者 — 单线程保证顺序
@Component
public class PipelineConsumer {

    @RabbitListener(queues = "pipeline.step.queue")
    public void onMessage(Long projectId, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            Project project = projectService.getById(projectId);
            pipelineEngine.advance(project);
            channel.basicAck(tag, false);  // 手动 ACK
        } catch (Exception e) {
            // 判断重试次数
            Integer retryCount = getRetryCount(tag);
            if (retryCount < 3) {
                channel.basicNack(tag, false, true);  // 回队尾
            } else {
                // 标记项目当前步骤为 FAILED
                projectService.markStepFailed(projectId, e.getMessage());
                channel.basicAck(tag, false);  // ACK 掉，不再重试
            }
        }
    }
}
```

---

## 五、数据库核心表设计

```sql
-- 项目表
CREATE TABLE project (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    name         VARCHAR(200)  NOT NULL COMMENT '项目名称',
    status       VARCHAR(30)   NOT NULL DEFAULT 'DRAFT' COMMENT '项目状态',
    step_status  VARCHAR(30)   NOT NULL DEFAULT 'PENDING' COMMENT '当前步骤子状态',
    resource_id  BIGINT        COMMENT '关联资源ID',
    context_json TEXT          COMMENT 'Pipeline 上下文 (JSON)',
    error_msg    VARCHAR(500)  COMMENT '当前步骤失败原因',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status)
);

-- 生成任务表 (每个AI调用一条)
CREATE TABLE task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id      BIGINT        NOT NULL COMMENT '所属项目ID',
    step            VARCHAR(30)   NOT NULL COMMENT '所属步骤',
    model_type      VARCHAR(30)   NOT NULL COMMENT '模型类型',
    vendor          VARCHAR(30)   NOT NULL COMMENT '厂商',
    vendor_task_id  VARCHAR(100)  COMMENT '厂商任务ID',
    status          VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    request_json    TEXT          COMMENT '请求参数JSON',
    result_json     TEXT          COMMENT '结果JSON',
    poll_count      INT           DEFAULT 0 COMMENT '已轮询次数',
    max_poll_count  INT           DEFAULT 60 COMMENT '最大轮询次数',
    poll_interval   INT           DEFAULT 30 COMMENT '轮询间隔(秒)',
    next_poll_at    DATETIME      COMMENT '下次轮询时间',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project_step (project_id, step),
    INDEX idx_status_next_poll (status, next_poll_at)
);

-- 模型配置表
CREATE TABLE model_config (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    vendor          VARCHAR(30)  NOT NULL COMMENT '厂商',
    model_type      VARCHAR(30)  NOT NULL COMMENT '模型类型',
    api_endpoint    VARCHAR(500) NOT NULL COMMENT 'API地址',
    api_key         VARCHAR(200) NOT NULL COMMENT 'API密钥',
    poll_interval   INT          DEFAULT 30 COMMENT '默认轮询间隔(秒)',
    max_poll_count  INT          DEFAULT 60 COMMENT '默认最大轮询次数',
    max_retries     INT          DEFAULT 3 COMMENT '最大重试次数',
    is_enabled      TINYINT      DEFAULT 1,
    UNIQUE KEY uk_vendor_type (vendor, model_type)
);

-- 资源表
CREATE TABLE resource (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(200)  NOT NULL,
    type        VARCHAR(30)   NOT NULL COMMENT 'IMAGE/VIDEO/AUDIO/TEXT',
    oss_key     VARCHAR(500)  NOT NULL COMMENT 'OSS存储Key',
    oss_url     VARCHAR(500)  COMMENT 'OSS访问URL',
    file_size   BIGINT,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

## 六、技术选型总结

| 层面 | 选型 | 理由 |
|------|------|------|
| 消息队列 | RabbitMQ | 消息持久化、手动ACK、单队列FIFO保证顺序性 |
| 数据库 | MySQL | 事务支持、行锁做状态更新的乐观锁 |
| 缓存 | Redis + Caffeine | Redis 做分布式缓存，Caffeine 做资源本地缓存(3天TTL) |
| 文件存储 | MinIO | 资源库文件存储，数据库只存ID引用 |
| 定时任务 | Spring @Scheduled | 轮询AI任务状态，按模型配置差异化间隔 |
| AI模型接入 | 适配器模式 | 统一入参/出参，厂商切换只需加子类 |

---

## 七、待确认项

1. **技术栈语言** — 方案默认 Java/Spring Boot，是否调整？(Go/Python/Node)
2. **数据库** — 默认 MySQL，是否用 PostgreSQL？
3. **OSS 选型** — 阿里云 OSS 还是 MinIO 自建？
4. **前端框架** — 是否需要一并设计前端结构？
5. **是否现在就按这个方案开始写代码** — 还是先调整方案细节？
