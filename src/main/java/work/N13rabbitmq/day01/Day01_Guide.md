# Day 01: 工业级集群部署与环境拓扑架构 🚀

欢迎开启 RabbitMQ 30天巅峰通关手册的第一天！今天我们将深入探讨 RabbitMQ 的底层集群机制，并亲手搭建一个工业级的 3 节点集群。

---

## 💡 核心理论补给站

### 1. Erlang Cookie：集群的“接头暗号”
RabbitMQ 集群中的节点通过 **Erlang Cookie** 进行相互认证。
- **原理**：每个节点都有一个名为 `.erlang.cookie` 的文件，只有 Cookie 值完全相同的节点才能建立连接并同步元数据。
- **生产建议**：在多机器部署时，必须手动同步该文件或通过环境变量指定相同的值。

### 2. 节点类型：磁盘节点 (Disk) vs 内存节点 (RAM)
- **磁盘节点 (Disk Node)**：将集群的元数据（队列定义、交换机、绑定等）持久化到磁盘。
    - *特点*：重启后数据不丢失，集群中至少需要一个磁盘节点。
- **内存节点 (RAM Node)**：元数据仅存储在内存中。
    - *特点*：由于不需要频繁写入磁盘，元数据同步速度极快，适合处理大量队列/交换机动态创建的场景。
- **最佳实践**：为了高可用，生产环境通常配置 2 个磁盘节点，其余为内存节点。

### 3. 核心端口速查
- `5672`：AMQP 0-9-1 协议端口，生产者和消费者使用的主要端口。
- `15672`：管理界面 (Management UI) 端口，浏览器访问地址。
- `25672`：集群内部通信端口，节点间同步数据使用。
- `4369`：EPMD (Erlang Port Mapper Daemon)，用于节点发现。

---

## 🚀 快速上手 (Quick Start)

我已经为你准备好了所有必要的文件。按照以下步骤操作：

### 1. 进入工作目录
```bash
cd d:\project\demo\demo-java\example\rabbit\study\day01
```

### 2. 执行一键搭建脚本 (推荐)
如果你想快速看到效果，可以直接运行：
```bash
bash setup_cluster.sh
```
这个脚本会自动：
- 启动 3 个 RabbitMQ 容器。
- 自动处理 `rabbit2` 和 `rabbit3` 的节点停止、加入集群（并指定为 RAM 类型）、启动。
- 最后输出整个集群的状态报告。

### 3. 或者：手动体验地狱难度 (推荐用于学习)
如果你想手动感受每个步骤，请参考下面的实战步骤：
- **启动容器**：`docker-compose up -d`
- **查看状态**：`docker exec rabbit1 rabbitmqctl cluster_status` (此时你会发现节点还没形成集群)
- **手动加入**：参考下方的 [2. 节点加入集群](#2-节点加入集群-手动模拟过程) 章节。

### 4. 验证与监控
- **运行健康检查**：`bash health_check.sh`
- **管理界面**：访问 `http://localhost:15672` (账号/密码: guest/guest)

---

## 🛠 地狱实战指南

### 第一步：docker-compose.yml 详解
在 [docker-compose.yml](file:///d:/project/demo/demo-java/example/rabbit/study/day01/docker-compose.yml) 中，我们通过 `RABBITMQ_ERLANG_COOKIE` 环境变量统一了集群的认证凭据。

### 第二步：一键健康检查脚本
[health_check.sh](file:///d:/project/demo/demo-java/example/rabbit/study/day01/health_check.sh) 能够实时扫描节点是否存活，并清晰地区分哪些是磁盘节点，哪些是内存节点。

---

## 📖 实验手册 (详细步骤)

### 1. 启动集群
在 `day01` 目录下执行：
```bash
docker-compose up -d
```

### 2. 节点加入集群 (手动模拟过程)
虽然 Docker 镜像可以自动处理，但为了深入理解，我们将执行以下手动步骤（在 `rabbit2` 上执行）：
```bash
# 停止当前应用
docker exec -it rabbit2 rabbitmqctl stop_app
# 加入 rabbit1 集群 (并指定为内存节点)
docker exec -it rabbit2 rabbitmqctl join_cluster --ram rabbit@rabbit1
# 启动应用
docker exec -it rabbit2 rabbitmqctl start_app
```

### 3. 运行健康检查
执行我们编写的脚本：
```bash
bash health_check.sh
```

### 4. 节点退出集群（主动退出）
如果某个节点要主动退出集群（例如维护升级）：
```bash
# 停止节点应用（保留数据）
docker exec -it rabbit2 rabbitmqctl stop_app

# 从集群中移除自己
docker exec -it rabbit2 rabbitmqctl reset

# 重新启动应用（独立节点）
docker exec -it rabbit2 rabbitmqctl start_app
```

### 5. 剔除集群节点（从其他节点操作）
如果要从集群中强制移除某个节点（例如故障节点）：
```bash
# 在 rabbit1 上执行，剔除 rabbit2
docker exec -it rabbit1 rabbitmqctl forget_cluster_node rabbit@rabbit2

# 如果要同时删除该节点的数据
docker exec -it rabbit1 rabbitmqctl forget_cluster_node rabbit@rabbit2 --offline
```

### 6. 查看集群状态
```bash
# 查看完整集群状态
docker exec -it rabbit1 rabbitmqctl cluster_status

# 查看节点列表
docker exec -it rabbit1 rabbitmqctl list_nodes
```

---

## 🏆 验收标准
- [ ] 访问 `http://localhost:15672` (账号: guest, 密码: guest)，在 **Overview -> Nodes** 页面看到 3 个绿色节点。
- [ ] 验证 `rabbit2` 和 `rabbit3` 的 Type 为 `RAM`。
- [ ] `health_check.sh` 输出所有节点状态为 `Running`。

**准备好了吗？让我们开始编写代码吧！**
