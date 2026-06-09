# Eureka AP (自我保护模式) 测试指南

## 环境信息

| 节点 | 端口 | 容器名 | Dashboard |
|------|------|--------|-----------|
| Node 1 | 8761 | eureka1 | http://host.docker.internal:8761/ |
| Node 2 | 8762 | eureka2 | http://host.docker.internal:8762/ |
| Node 3 | 8763 | eureka3 | http://host.docker.internal:8763/ |

- 客户端应用 (eureka-demo): http://host.docker.internal:8082

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
  实际续约数 < 阈值续约数 (默认 85%) → 触发保护
  保护模式下: 不剔除任何实例！宁可返回过时数据也不丢数据
```

**为什么？** 防止网络分区 (Network Partition) 导致大量实例被误删。

---

## 测试步骤

### Step 1: 确认集群正常

打开三个 Dashboard，确认互相可见：

- http://host.docker.internal:8761/ → "registered-replicas" 里应显示 EUREKA2、EUREKA3
- http://host.docker.internal:8762/ → 应显示 EUREKA1、EUREKA3
- http://host.docker.internal:8763/ → 应显示 EUREKA1、EUREKA2

```bash
# 查看客户端注册状态
curl http://host.docker.internal:8082/info

# 查看自我保护状态
curl http://host.docker.internal:8082/test/self-preservation
```

**预期：** Dashboard 无红色告警，所有实例状态 UP。

### Step 2: 注册更多实例 (增大续约基数)

启动多个客户端实例，或手动注册模拟实例：

```bash
# 查看当前所有已注册应用
curl http://host.docker.internal:8082/test/self-preservation

# 查看所有注册详情
curl http://host.docker.internal:8082/test/multi-node-compare
```

### Step 3: 停掉 1 个 Eureka Server 节点

```powershell
docker stop eureka3
```

**立即观察：**

1. 打开 http://host.docker.internal:8761/ Dashboard
2. 应该看到顶部红色告警横幅：

```
EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCE ARE UP WHICH THEY'RE NOT.
RENEWALS ARE LESSER THAN THRESHOLD AND HENCE THE INSTANCES ARE NOT BEING EXPIRED
JUST TO BE SAFE.
```

这说明自我保护已激活！

```bash
# 验证自我保护状态
curl http://host.docker.internal:8082/test/self-preservation
```

### Step 4: 验证自我保护行为

自我保护激活后，**即使客户端停止心跳，实例也不会被剔除**。

验证方式：

```bash
# 1. 记录当前实例数
curl http://host.docker.internal:8082/test/self-preservation
# → 记下 totalInstances

# 2. 杀掉客户端应用 (Ctrl+C)

# 3. 等待 2 分钟 (超过正常的 90s 过期时间)
sleep 120

# 4. 重新查看 (如果客户端还能查询缓存)
# 或直接看 Dashboard: 实例仍然存在，没有被剔除!
```

**对比：如果自我保护关闭**

在 `application-docker.yml` 中设置 `enable-self-preservation: false`，重复上述测试：
- 客户端停止心跳 → 90s 后实例被剔除 (状态变 DOWN 然后消失)

### Step 5: 恢复节点

```powershell
docker start eureka3
```

等待 ~30s，Dashboard 红色告警消失，续约率恢复正常。

---

## 进阶测试: 节点间数据同步验证

### 验证 peer 复制

```bash
# 通过客户端注册 (连接到 eureka1)
# 然后分别在三个 Dashboard 查看是否都能看到这个实例
# http://host.docker.internal:8761/ ✓
# http://host.docker.internal:8762/ ✓ (从 eureka1 同步过来)
# http://host.docker.internal:8763/ ✓ (从 eureka1 同步过来)
```

### 模拟网络分区

```powershell
# 断开 eureka3 的网络 (不是 stop，而是隔离)
docker network disconnect eureka-net eureka3

# 观察:
# - eureka1 和 eureka2: 互相可见，eureka3 变为 "unavailable-replicas"
# - eureka3: eureka1 和 eureka2 变为 "unavailable-replicas"
# - 两边都可能触发自我保护

# 恢复网络
docker network connect eureka-net eureka3
```

---

## API 速查表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/info` | 当前服务注册信息 |
| GET | `/services` | 所有已注册服务名 |
| GET | `/services/{name}` | 指定服务的实例列表 |
| GET | `/eureka/app/{appName}` | Eureka 原生 API 查询应用详情 |
| GET | `/test/self-preservation` | 自我保护状态 + 测试指南 |
| GET | `/test/multi-node-compare` | 多节点注册表对比 |

## 关键观察点

```
正常状态 (3 节点全活):
  Dashboard: 无告警
  实例心跳: 正常续约
  过期实例: 90s 后被剔除 ✓

停 1 节点 (2/3):
  Dashboard: 红色 EMERGENCY 告警 ⚠️
  自我保护: 已激活
  过期实例: 不会被剔除 (AP 设计)
  新注册: 仍然成功 (写入剩余节点)

停 2 节点 (1/3):
  Dashboard: 红色告警 + 无 peer
  自我保护: 仍然激活
  过期实例: 不会被剔除
  新注册: 成功 (单节点仍可接受写入)

全部恢复:
  Dashboard: 告警消失
  节点同步: peer 间数据重新一致 ✓
  续约率: 恢复正常阈值
```

## 自我保护 vs 不保护的对比

| 场景 | 保护开启 (默认) | 保护关闭 |
|------|----------------|---------|
| 网络分区 | 保留所有实例 ✓ | 可能误删健康实例 ✗ |
| 真的宕机 | 过时实例不会被剔除 | 90s 后被剔除 |
| 设计理念 | **宁可返回过时数据** | **宁可返回空数据** |
| CAP 分类 | AP (可用性优先) | 偏向 CP (一致性优先) |

## 清理

```powershell
docker rm -f eureka1 eureka2 eureka3
docker network rm eureka-net
```
