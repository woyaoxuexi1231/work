# Eureka Server 集群 (Docker)

基于 Spring Cloud Netflix Eureka Server，Docker 一键部署 3 节点集群。
**核心目标：通过真实实验理解 Eureka 的 AP 本质。**

## Eureka 的 AP 是什么？

CAP 定理中，Eureka 选择了 **AP（可用性 + 分区容错）**：

| 策略 | Eureka 怎么做 | 为什么 |
|------|-------------|--------|
| 网络分区时 | **不剔除**失联节点，保留过期实例 | 万一是网络抖动呢？误杀比误留更危险 |
| 心跳低于阈值 | 触发**自我保护模式**，冻结剔除 | 优先保证注册中心可用，拒绝服务更致命 |
| 分区恢复 | 节点间增量同步，最终一致 | 容忍短暂不一致，反正客户端有本地缓存 |

> 一句话：Eureka 认为"同时持有过期数据也比拿不到数据强"。

---

## 架构

```
┌─────────────────────────────────────────────┐
│               Docker Network: eureka-net     │
│                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │ eureka1  │  │ eureka2  │  │ eureka3  │  │
│  │  :8761   │  │  :8761   │  │  :8761   │  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  │
│       │              │              │        │
└───────┼──────────────┼──────────────┼────────┘
        │              │              │
    宿主机 :8761   宿主机 :8762   宿主机 :8763
```

---

## 前置条件

| 工具 | 版本要求 |
|------|----------|
| JDK | 1.8+ |
| Maven | 3.6+ |
| Docker | 20.10+ |

## 快速开始

```powershell
.\build_eureka.ps1      # 编译 + 构建镜像
.\install_eureka.ps1    # 启动 3 节点集群
```

| 节点 | Dashboard |
|------|-----------|
| eureka1 | http://host.docker.internal:8761/ |
| eureka2 | http://host.docker.internal:8762/ |
| eureka3 | http://host.docker.internal:8763/ |

---

## AP 测试一：自我保护模式（核心）

### 配置

```yaml
enable-self-preservation: true        # 开启自我保护
renewal-percent-threshold: 0.85       # 心跳续约低于 85% 触发
```

### 演示步骤

```
初始状态：3 个节点全部正常，心跳比例 100% > 85%，正常模式下运行。
```

1. 打开 eureka1 Dashboard，确认 `DS Replicas` 里 eureka2、eureka3 都是 UP
2. `docker stop eureka3` — 模拟 eureka3 宕机（网络分区）
3. 持续刷新 eureka1 Dashboard，观察变化

### 你将看到

- ⏱ **约 1 分钟后**，Dashboard 顶部出现红字：
  > **EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCES ARE UP WHEN THEY'RE NOT. RENEWALS ARE LESSER THAN THRESHOLD AND HENCE THE INSTANCES ARE NOT BEING EXPIRED JUST TO BE SAFE.**

- 📊 eureka3 **依然出现在 DS Replicas 列表中**（不会被剔除）
- 🟢 eureka1 和 eureka2 继续正常服务，不影响注册/发现

### 这说明了什么？

| 行为 | AP 体现 |
|------|---------|
| eureka3 宕机但没被剔除 | **分区容错 (P)**：宁可保留过期数据 |
| eureka1/2 继续正常服务 | **可用性 (A)**：服务不中断 |
| 数据可能在短时间内不一致 | **牺牲一致性 (C)**：容忍短暂脏读 |

> 反例：如果这是 Zookeeper（CP 系统），eureka3 宕机后剩余 2 个节点必须等选举出新 Leader，期间整个集群**不可写**。

---

## AP 测试二：分区恢复

1. 接上一步，`docker start eureka3`
2. 等待约 30 秒刷新 eureka1 Dashboard
3. 🔴 红字警告消失，恢复 `DS Replicas` 全部 UP

> Eureka 通过**增量同步**恢复数据，不用全量拉取，最终达到一致。

---

## 对比：关掉自我保护试试

修改 `application-docker.yml` 中 `enable-self-preservation: false`，重跑：

```powershell
docker stop eureka1 eureka2 eureka3
docker rm eureka1 eureka2 eureka3
.\install_eureka.ps1
```

重复测试一，你会发现停掉 eureka3 后，它**约 60 秒内从 DS Replicas 消失**。

| 模式 | 停掉一个节点后 | 含义 |
|------|--------------|------|
| 自我保护 ON (AP) | 节点保留，红字警告 | 宁可多留，不误杀 |
| 自我保护 OFF | 节点被剔除 | 数据一致了，但可能误删 |

## 常用命令

```powershell
docker logs -f eureka1              # 查看日志
docker stop eureka3                 # 停单节点
docker start eureka3                # 恢复节点
docker stop eureka1 eureka2 eureka3 # 停集群
docker rm -f eureka1 eureka2 eureka3; docker network rm eureka-net  # 彻底清除
```

## 配置文件说明

| 文件 | 用途 |
|------|------|
| `application-docker.yml` | Docker 集群（自我保护开启，85% 阈值） |
| `application-peer1/2/3.yml` | 本地开发集群（端口 12001-12003） |
| `application-dev.yml` | 单机开发（端口 10001） |
