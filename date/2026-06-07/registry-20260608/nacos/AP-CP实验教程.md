# Nacos AP/CP 完整实验教程

> **目标**：通过实际操作，理解 Nacos 的 AP 和 CP 模式到底是什么，以及如何测试和验证。

---

## 📚 理论准备（重要！）

### 1. AP 和 CP 说的是什么？

**AP 和 CP 描述的是 Nacos 集群节点之间的数据一致性策略**，不是服务实例的存活状态。

- **AP (Availability + Partition Tolerance)**：优先保证可用性，允许数据暂时不一致
- **CP (Consistency + Partition Tolerance)**：优先保证数据一致性，可能牺牲可用性

### 2. Nacos 如何区分 AP 和 CP？

Nacos 通过**实例类型**来区分：

| 实例类型 | 配置 | 一致性模式 | 适用场景 |
|---------|------|-----------|---------|
| **临时实例** | `ephemeral=true` | AP | 服务发现（默认） |
| **持久实例** | `ephemeral=false` | CP (Raft) | 配置管理 |

### 3. 为什么之前 Python 脚本会报错？

错误信息：`receive invalid redirect request from peer 172.20.0.3`

**原因**：
- 你的 Nacos 是 3 节点集群（nacos1, nacos2, nacos3）
- 集群内部使用 Docker 内网 IP（172.20.0.3）通信
- 你的客户端（192.168.3.4）无法访问这个内网 IP
- Nacos 尝试重定向请求到集群其他节点时失败

**解决方案**：
1. 修复 `cluster.conf`，使用宿主机可访问的 IP
2. 或者本教程中，我们会直接测试单节点和集群场景

---

## 🧪 实验环境准备

### 环境信息

根据你的 `install_nacos_cluster.ps1` 脚本，你的环境是：

```text
Nacos 集群（3节点）：
  - nacos1: 192.168.3.100:8848 (Docker 内网: nacos1:8848)
  - nacos2: 192.168.3.100:8948 (Docker 内网: nacos2:8848)
  - nacos3: 192.168.3.100:9848 (Docker 内网: nacos3:8848)

MySQL: 192.168.3.100:3306 (容器名: mysql)

测试服务（已创建）：
  - user-demo-nacos-20260609: 18081
  - order-demo-nacos-20260609: 18082
  - product-demo-nacos-20260609: 18083
```

### 检查 Nacos 集群状态

```bash
# 检查 3 个 Nacos 节点是否运行
docker ps | Select-String "nacos"

# 查看集群节点信息
curl "http://192.168.3.100:8848/nacos/v1/ns/operator/cluster/health"

# 查看集群节点列表
curl "http://192.168.3.100:8848/nacos/v1/ns/operator/cluster/nodes"
```

---

## 🔬 实验一：AP 模式测试（临时实例）

### 理论基础

**AP 模式特点**：
- 使用 **Distro 协议**（阿里自研的 AP 协议）
- 优先保证可用性
- 允许集群节点间数据暂时不一致
- 网络分区时，各节点仍然可以接收注册和查询请求
- 恢复后自动同步数据

**对应实例类型**：临时实例（`ephemeral=true`）

### 测试步骤

#### 步骤 1：启动服务（AP 模式）

修改 `user-demo-nacos-20260609` 的配置，明确指定为 AP 模式：

**application.yml**：
```yaml
server:
  port: 18081

spring:
  application:
    name: user-demo-nacos-20260609
  cloud:
    nacos:
      discovery:
        server-addr: 192.168.3.100:8848
        namespace: public
        cluster-name: DEFAULT
        ephemeral: true  # 临时实例 = AP 模式
```

启动服务：
```bash
cd registry-20260608/nacos/user-demo-nacos-20260609
mvn spring-boot:run
```

**预期结果**：
```text
========================================
  User Demo Service Started [Nacos]
  Service: user-demo-nacos-20260609
  Port: 18081
  Nacos: 192.168.3.100:8848
  Mode: AP (ephemeral=true)
========================================
```

#### 步骤 2：验证注册成功

```bash
# 方式1：通过 Nacos 控制台查看
# 打开浏览器：http://192.168.3.100:8848/nacos
# 用户名/密码：nacos/nacos
# 进入 "服务管理" -> "服务列表"

# 方式2：通过 API 查询
curl "http://192.168.3.100:8848/nacos/v1/ns/instance/list?serviceName=user-demo-nacos-20260609"

# 预期返回：
# {"name":"user-demo-nacos-20260609","groupName":"DEFAULT_GROUP","clusters":"DEFAULT",...}
```

#### 步骤 3：模拟网络分区（核心实验）

**目标**：观察 AP 模式在网络分区时的行为

**实验设计**：
1. 停止 nacos2 和 nacos3，只保留 nacos1
2. 观察服务是否还能注册和查询
3. 恢复 nacos2 和 nacos3，观察数据同步

**执行**：

```powershell
# 1. 停止 nacos2 和 nacos3
docker stop nacos2
docker stop nacos3

# 2. 确认只有 nacos1 运行
docker ps | Select-String "nacos"

# 3. 尝试注册新服务（启动 order-demo）
cd registry-20260608/nacos/order-demo-nacos-20260609
mvn spring-boot:run

# 4. 查询服务列表（应该成功，即使是单节点）
curl "http://192.168.3.100:8848/nacos/v1/ns/instance/list?serviceName=order-demo-nacos-20260609"
```

**AP 模式的预期行为**：
```text
✅ 即使集群只有 1 个节点，仍然可以注册服务
✅ 查询请求仍然可以成功
⚠️  数据还没有同步到其他节点（因为它们停了）
```

#### 步骤 4：恢复集群，观察数据同步

```powershell
# 恢复 nacos2 和 nacos3
docker start nacos2
docker start nacos3

# 等待几秒，让集群自动同步数据

# 从 nacos2 查询（应该能看到数据已同步）
curl "http://192.168.3.100:8948/nacos/v1/ns/instance/list?serviceName=order-demo-nacos-20260609"
```

**预期结果**：
```text
✅ nacos2 和 nacos3 自动同步了 order-demo 的注册信息
✅ 这就是 AP 模式：最终一致性
```

#### 步骤 5：测试高可用（重要！）

```powershell
# 停止 nacos1（原来的主节点）
docker stop nacos1

# 尝试查询服务（应该仍然成功）
curl "http://192.168.3.100:8948/nacos/v1/ns/instance/list?serviceName=user-demo-nacos-20260609"

# 启动新服务（应该成功）
cd registry-20260608/nacos/product-demo-nacos-20260609
mvn spring-boot:run
```

**AP 模式的核心特征**：
```text
✅ 任何存活的节点都可以接收读写请求
✅ 不需要多数派确认
✅ 优先保证可用性
```

---

## 🔬 实验二：CP 模式测试（持久实例）

### 理论基础

**CP 模式特点**：
- 使用 **Raft 协议**（分布式一致性协议）
- 优先保证数据一致性
- 必须得到多数派（>50%）节点确认才能写入
- 网络分区时，少数派节点会拒绝写入
- 适合配置管理等需要强一致性的场景

**对应实例类型**：持久实例（`ephemeral=false`）

### 测试步骤

#### 步骤 1：修改服务为 CP 模式

修改 `order-demo-nacos-20260609` 的配置：

**application.yml**：
```yaml
server:
  port: 18082

spring:
  application:
    name: order-demo-nacos-20260609
  cloud:
    nacos:
      discovery:
        server-addr: 192.168.3.100:8848
        namespace: public
        cluster-name: DEFAULT
        ephemeral: false  # 持久实例 = CP 模式
```

启动服务：
```bash
cd registry-20260608/nacos/order-demo-nacos-20260609
mvn spring-boot:run
```

**预期结果**：
```text
========================================
  Order Demo Service Started [Nacos]
  Service: order-demo-nacos-20260609
  Port: 18082
  Nacos: 192.168.3.100:8848
  Mode: CP (ephemeral=false)
========================================
```

#### 步骤 2：验证注册成功

```bash
# 查询持久实例
curl "http://192.168.3.100:8848/nacos/v1/ns/instance/list?serviceName=order-demo-nacos-20260609&ephemeral=false"

# 注意：持久实例和临时实例在 Nacos 内部是分开存储的
```

#### 步骤 3：模拟网络分区（核心实验）

**目标**：观察 CP 模式在网络分区时的行为

**实验设计**：
1. 3 节点集群正常运行
2. 停止 2 个节点，只保留 1 个节点（少数派）
3. 观察写入请求是否会被拒绝

**执行**：

```powershell
# 1. 确保 3 个节点都运行
docker start nacos1 nacos2 nacos3

# 2. 查看 Raft 集群状态（Leader 是谁）
docker exec nacos1 cat /home/nacos/data/naming/raft/meta.properties
# 或者查看日志
docker logs nacos1 | Select-String "Leader"

# 3. 停止 2 个节点，只保留 1 个（假设 nacos1 是 Leader）
docker stop nacos2
docker stop nacos3

# 4. 尝试注册新服务（或注销已有服务）
curl -X DELETE "http://192.168.3.100:8848/nacos/v1/ns/instance?serviceName=order-demo-nacos-20260609&ip=192.168.3.4&port=18082"
```

**CP 模式的预期行为**：
```text
❌ 注册/注销请求失败
❌ 返回类似 "no leader" 或 "failed to get leader" 的错误
✅ 查询请求可能仍然成功（从本地节点读取）
```

**原因**：
```text
Raft 协议要求多数派（>50%）节点存活才能写入
3 个节点中只保留 1 个，无法形成多数派（需要至少 2 个）
因此拒绝写入，保证数据一致性
```

#### 步骤 4：恢复多数派，观察写入恢复

```powershell
# 启动 nacos2（现在有 nacos1 + nacos2 = 2/3，形成多数派）
docker start nacos2

# 等待几秒，Raft 重新选举 Leader

# 再次尝试写入（应该成功）
curl -X POST "http://192.168.3.100:8848/nacos/v1/ns/instance" `
  -d "serviceName=test-cp-service&ip=192.168.3.4&port=19999&ephemeral=false"
```

**预期结果**：
```text
✅ 多数派恢复后，写入请求成功
✅ 数据强一致性得到保证
```

---

## 📊 实验三：AP vs CP 对比测试

### 测试矩阵

| 场景 | AP 模式（临时实例） | CP 模式（持久实例） |
|------|-------------------|-------------------|
| **集群完整（3/3）** | ✅ 读写正常 | ✅ 读写正常 |
| **少数派（1/3）** | ✅ 仍然可以读写 | ❌ 拒绝写入 |
| **多数派（2/3）** | ✅ 读写正常 | ✅ 读写正常 |
| **网络分区** | ✅ 各分区独立服务 | ❌ 少数派分区拒绝写入 |
| **恢复后** | ✅ 自动同步（最终一致） | ✅ 立即一致（Raft 保证） |

### 实际测试脚本

创建一个测试脚本 `test-ap-cp.ps1`：

```powershell
# test-ap-cp.ps1
# Nacos AP vs CP 对比测试脚本

$NacosAddr = "192.168.3.100:8848"
$ServiceAP = "user-demo-nacos-20260609"   # 临时实例
$ServiceCP = "order-demo-nacos-20260609"  # 持久实例

Write-Host "`n========== 测试 1: 集群完整 ==========" -ForegroundColor Cyan
Write-Host "查询 AP 服务: " -NoNewline
curl -s "$NacosAddr/nacos/v1/ns/instance/list?serviceName=$ServiceAP" | Select-String "hosts"
Write-Host "查询 CP 服务: " -NoNewline
curl -s "$NacosAddr/nacos/v1/ns/instance/list?serviceName=$ServiceCP&ephemeral=false" | Select-String "hosts"

Write-Host "`n========== 测试 2: 停止 2 个节点 ==========" -ForegroundColor Cyan
docker stop nacos2 nacos3
Start-Sleep 5

Write-Host "AP 模式注册新实例: " -NoNewline
curl -s -X POST "$NacosAddr/nacos/v1/ns/instance" `
  -d "serviceName=test-ap&ip=1.1.1.1&port=1111&ephemeral=true"
Write-Host "`nCP 模式注册新实例: " -NoNewline
curl -s -X POST "$NacosAddr/nacos/v1/ns/instance" `
  -d "serviceName=test-cp&ip=2.2.2.2&port=2222&ephemeral=false"

Write-Host "`n========== 测试 3: 恢复多数派 ==========" -ForegroundColor Cyan
docker start nacos2
Start-Sleep 5

Write-Host "CP 模式注册新实例（恢复后）: " -NoNewline
curl -s -X POST "$NacosAddr/nacos/v1/ns/instance" `
  -d "serviceName=test-cp&ip=2.2.2.2&port=2222&ephemeral=false"

Write-Host "`n========== 清理 ==========" -ForegroundColor Cyan
docker start nacos3
curl -s -X DELETE "$NacosAddr/nacos/v1/ns/instance?serviceName=test-ap&ip=1.1.1.1&port=1111&ephemeral=true"
curl -s -X DELETE "$NacosAddr/nacos/v1/ns/instance?serviceName=test-cp&ip=2.2.2.2&port=2222&ephemeral=false"
Write-Host "测试完成" -ForegroundColor Green
```

运行测试：
```powershell
cd registry-20260608/nacos
.\test-ap-cp.ps1
```

---

## 🎯 实验四：真实场景演示

### 场景 1：服务实例故障（不是 AP/CP 的范畴！）

**很多人会混淆的概念**：

❌ **错误理解**：停止 Spring Boot 服务 = 测试 AP/CP
✅ **正确理解**：停止 Spring Boot 服务 = 测试心跳机制和实例剔除

**演示**：

```bash
# 启动 user-demo（AP 模式，临时实例）
cd user-demo-nacos-20260609
mvn spring-boot:run &

# 查看实例状态
curl "http://192.168.3.100:8848/nacos/v1/ns/instance/list?serviceName=user-demo-nacos-20260609"

# 强制杀掉服务（模拟故障）
kill -9 <PID>

# 等待 15-30 秒（Nacos 心跳超时时间）
Start-Sleep 30

# 再次查询（实例应该被剔除）
curl "http://192.168.3.100:8848/nacos/v1/ns/instance/list?serviceName=user-demo-nacos-20260609"
```

**结论**：
```text
这个实验演示的是：
  - Nacos 的心跳机制
  - 临时实例的自动剔除
  - 和 AP/CP 无关！
```

### 场景 2：Nacos 集群故障（这才是 AP/CP 的范畴！）

**正确的 AP/CP 测试**：

```powershell
# 确保 3 个节点都运行
docker start nacos1 nacos2 nacos3

# 注册一个 AP 服务和一个 CP 服务
# （启动 user-demo 和 order-demo）

# 停止 2 个节点
docker stop nacos2 nacos3

# 尝试通过剩余的 nacos1 注册新服务
# AP 模式：成功
# CP 模式：失败

# 这就是 AP vs CP 的核心差异！
```

---

## 📝 实验总结

### 如何判断当前是 AP 还是 CP？

| 特征 | AP 模式 | CP 模式 |
|------|---------|---------|
| **实例类型** | `ephemeral=true` | `ephemeral=false` |
| **协议** | Distro | Raft |
| **写入要求** | 任意节点可写入 | 必须 Leader 确认 |
| **网络分区** | 各节点独立服务 | 少数派拒绝写入 |
| **数据一致性** | 最终一致 | 强一致 |
| **适用场景** | 服务发现 | 配置管理 |

### 查看 Nacos 使用的模式

```bash
# 查看实例类型
curl "http://192.168.3.100:8848/nacos/v1/ns/instance/list?serviceName=user-demo-nacos-20260609"

# 返回结果中：
# - "ephemeral": true  -> AP 模式
# - "ephemeral": false -> CP 模式
```

### 最常见的误解

❌ **误解**："我停止 Spring Boot 服务，观察 Nacos 是否还能查询，这就是测试 AP/CP"
✅ **真相**：这只是测试服务实例的健康检查和自动剔除机制

❌ **误解**："Nacos 要么全是 AP，要么全是 CP"
✅ **真相**：Nacos 同时支持 AP 和 CP，通过 `ephemeral` 参数区分

✅ **正确理解**：AP/CP 描述的是 **Nacos 集群节点之间的数据一致性策略**，只有在 **Nacos 集群出现故障或网络分区** 时，才能体现出差异！

---

## 🚀 快速验证清单

- [ ] 启动 3 个 Demo 服务（user, order, product）
- [ ] 在 Nacos 控制台查看服务列表
- [ ] 停止 2 个 Nacos 节点，测试 AP 服务是否能注册
- [ ] 停止 2 个 Nacos 节点，测试 CP 服务是否能注册
- [ ] 恢复 Nacos 节点，观察数据同步
- [ ] 查看实例详情，确认 `ephemeral` 字段的值

---

## 📚 延伸阅读

1. **Nacos 官方文档 - AP/CP 模式**
   - https://nacos.io/zh-cn/docs/v2/architecture/architecture-design.html

2. **Raft 协议可视化**
   - http://thesecretlivesofdata.com/raft/

3. **Distro 协议（阿里自研）**
   - 搜索 "Nacos Distro 协议原理"

---

## 🛠️ 故障排查

### 问题 1：服务无法注册，报 400 错误

**原因**：Nacos 集群重定向失败（Docker 内网 IP 问题）

**解决**：
```powershell
# 进入 Nacos 容器，修改 cluster.conf
docker exec -it nacos1 bash
vi /home/nacos/conf/cluster.conf

# 将 172.20.0.x 改为宿主机的可访问 IP
# 保存后重启集群
docker restart nacos1 nacos2 nacos3
```

### 问题 2：CP 模式测试时，无法判断 Leader

**解决**：
```powershell
# 查看 Raft 状态
docker logs nacos1 | Select-String "Leader"
docker exec nacos1 cat /home/nacos/data/naming/raft/meta.properties

# 或者通过 API
curl "http://192.168.3.100:8848/nacos/v1/ns/operator/cluster/nodes"
```

### 问题 3：服务注册成功，但控制台看不到

**原因**：可能使用了错误的命名空间或分组

**解决**：
```bash
# 明确指定命名空间
curl "http://192.168.3.100:8848/nacos/v1/ns/instance/list?serviceName=user-demo-nacos-20260609&namespaceId=public"
```

---

## ✅ 实验完成标志

当你能够回答以下问题时，说明你已经理解了 Nacos 的 AP 和 CP：

1. ✅ AP 和 CP 分别描述的是什么？
2. ✅ 如何通过配置区分 AP 和 CP？
3. ✅ 网络分区时，AP 和 CP 的行为有什么不同？
4. ✅ 为什么停止 Spring Boot 服务不等于测试 AP/CP？
5. ✅ 如何在生产环境中选择使用 AP 还是 CP？

---

**祝实验顺利！** 🎉

如果遇到问题，查看 Nacos 日志：
```powershell
docker logs nacos1 | Select-String "ERROR"
docker logs nacos1 | Select-String "Leader"
```
