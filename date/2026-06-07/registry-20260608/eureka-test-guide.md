# Eureka AP (自我保护模式) 测试指南

## 环境信息

| 节点 | 端口 | 容器名 | Dashboard |
|------|------|--------|-----------|
| Node 1 | 8761 | eureka1 | http://localhost:8761/ |
| Node 2 | 8762 | eureka2 | http://localhost:8762/ |
| Node 3 | 8763 | eureka3 | http://localhost:8763/ |

- 客户端应用 (eureka-demo): http://localhost:8082

## 启动环境

```powershell
# 1. 构建 Eureka Server 镜像 (首次)
cd netflix-eureka-server-20260608
.\build_eureka.ps1

# 2. 启动 3 节点集群
.\install_eureka.ps1

# 3. 启动客户端应用
cd ../eureka
mvn spring-boot:run
```

## 核心概念

Eureka 是纯 **AP** 设计，核心机制是 **自我保护 (Self-Preservation)**：

```
正常状态:
  客户端每 30s 发心跳 → Server 收到 → 续约成功
  超过 90s 没心跳 → Server 剔除该实例

自我保护触发:
  每分钟统计心跳续约数
  实际续约数 < 预期续约数 × 85% → 触发保护
  保护模式下: 不剔除任何实例！宁可返回过时数据也不丢数据
```

**关键：什么能触发，什么不能触发：**

| 操作 | 能否触发自我保护 | 原因 |
|------|:---:|------|
| 停 Eureka Server 节点 | **不能** | 客户端还在给其他节点发心跳，续约率没降 |
| 大量客户端突然断心跳 | **能** | 续约率暴跌 < 85%，触发保护 |

---

## 测试 A：自我保护开启（默认）

### Step 1: 确认集群正常

```bash
# 查看客户端注册状态
curl http://localhost:8082/info

# 打开 Dashboard 确认无红色告警
# http://localhost:8761/
# http://localhost:8762/
# http://localhost:8763/
```

### Step 2: 批量注册假实例（不发心跳）

```bash
# 注册 10 个假实例到 Eureka Server
# 这些实例只注册，不会有 EurekaClient 维护心跳
curl -X POST "http://localhost:8082/test/fake-register?count=10"
```

返回示例：
```json
{
  "registered": ["10.0.0.1:30001 (fake-host-1)", "..."],
  "registeredCount": 10,
  "renewalAnalysis": {
    "realInstancesBefore": 1,
    "fakeInstancesAdded": 10,
    "totalInstancesAfter": 11,
    "expectedRenewalRate": "9%",       ← 远低于 85%
    "willTriggerSelfPreservation": true ← 将触发自我保护
  }
}
```

### Step 3: 等待 2 分钟

假实例的 lease 是 90s，等 2 分钟让它们过期。

```bash
# 等待 (PowerShell)
Start-Sleep 120
```

### Step 4: 观察 Dashboard

打开 http://localhost:8761/ ，应该看到顶部红色告警：

```
EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCE ARE UP WHICH THEY'RE NOT.
RENEWALS ARE LESSER THAN THRESHOLD AND HENCE THE INSTANCES ARE NOT BEING EXPIRED
JUST TO BE SAFE.
```

**这就是自我保护激活了！**

### Step 5: 验证假实例仍在

```bash
curl http://localhost:8082/test/fake-status
```

**预期结果：**
```json
{
  "fakeInstancesStillVisible": 10,     ← 假实例仍在！没有被剔除
  "verdict": "假实例仍在 → 自我保护已激活 (AP: 宁返过时数据也不丢)"
}
```

### Step 6: 清理

```bash
curl -X DELETE "http://localhost:8082/test/fake-cleanup"
```

---

## 测试 B：自我保护关闭（对比）

### Step 1: 用 `no-sp` profile 重新启动 Eureka Server

修改 `install_eureka.ps1` 中的环境变量：

```powershell
-e SPRING_PROFILES_ACTIVE=no-sp `    # 改这里: docker → no-sp
```

或者手动启动一个单节点对比：

```powershell
docker run -d --name eureka-test -p 18761:8761 `
  -e SPRING_PROFILES_ACTIVE=no-sp `
  -e SERVER_PORT=8761 `
  -e "eureka.client.registerWithEureka=false" `
  -e "eureka.client.fetchRegistry=false" `
  eureka-server:latest
```

### Step 2: 注册假实例

```bash
# 注册到关闭保护的 Eureka
curl -X POST "http://localhost:8082/test/fake-register?count=10"
```

### Step 3: 等待 2 分钟

```bash
Start-Sleep 120
```

### Step 4: 查看结果

```bash
curl http://localhost:8082/test/fake-status
```

**预期结果：**
```json
{
  "fakeInstancesStillVisible": 0,     ← 假实例被剔除了！
  "verdict": "假实例已被剔除 → 自我保护未激活或已关闭"
}
```

Dashboard 无红色告警，假实例已消失。

---

## 对比总结

| | 自我保护开启 (默认) | 自我保护关闭 |
|---|---|---|
| **停发心跳后** | 实例保留，不剔除 | 90s 后剔除 |
| **Dashboard 告警** | 红色 EMERGENCY | 无告警 |
| **设计理念** | 宁可返回过时数据 (AP) | 宁可返回空数据 |
| **适用场景** | 网络不稳定/分区风险高 | 网络稳定，追求数据新鲜 |

## API 速查表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/info` | 当前服务注册信息 |
| GET | `/services` | 所有已注册服务名 |
| GET | `/services/{name}` | 指定服务的实例列表 |
| GET | `/eureka/app/{appName}` | Eureka 原生 API 查询应用 |
| **POST** | `/test/fake-register?count=10` | **批量注册假实例 (触发保护)** |
| **GET** | `/test/fake-status` | **查看假实例是否被剔除** |
| **DELETE** | `/test/fake-cleanup` | **清理假实例** |

## 快速流程

```
# 1. 注册假实例
curl -X POST "http://localhost:8082/test/fake-register?count=10"

# 2. 等 2 分钟
# 3. 看 Dashboard → 红色 EMERGENCY 告警
# 4. 查状态 → 假实例仍在 (保护生效)
curl http://localhost:8082/test/fake-status

# 5. 清理
curl -X DELETE "http://localhost:8082/test/fake-cleanup"
```

## 清理

```powershell
docker rm -f eureka1 eureka2 eureka3
docker network rm eureka-net
```
