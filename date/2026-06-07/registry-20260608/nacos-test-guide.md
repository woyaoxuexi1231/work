# Nacos AP/CP 对比测试指南

## 环境信息

| 节点 | HTTP 端口 | gRPC 端口 | 容器名 |
|------|----------|----------|--------|
| Node 1 | 8848 | 9848 | nacos1 |
| Node 2 | 8849 | 9849 | nacos2 |
| Node 3 | 8850 | 9850 | nacos3 |

- Nacos Console: http://host.docker.internal:8848/nacos (账号 nacos/nacos)
- 客户端应用 (nacos-demo): http://host.docker.internal:8081

## 启动环境

```powershell
# 1. 启动 3 节点集群
.\install_nacos.ps1

# 2. 启动客户端应用
cd nacos
mvn spring-boot:run
```

## 核心概念

| 模式 | 实例类型 | 一致性协议 | 特点 |
|------|---------|-----------|------|
| **AP** | ephemeral=true (临时实例) | Distro | 异步复制，允许短暂不一致，高可用 |
| **CP** | ephemeral=false (持久实例) | Raft | 强一致性，需多数节点(>N/2)存活，写入需 Leader |

---

## 测试步骤

### Step 1: 批量注册 AP + CP 实例

```bash
# 一键注册 3 个 AP 实例 + 3 个 CP 实例
curl -X POST "http://host.docker.internal:8081/test/batch-register?apCount=3&cpCount=3"
```

或分别注册：

```bash
# 注册 AP 临时实例 (Distro 协议)
curl -X POST "http://host.docker.internal:8081/test/register/ap?serviceName=ap-test-service&port=10001"

# 注册 CP 持久实例 (Raft 协议)
curl -X POST "http://host.docker.internal:8081/test/register/cp?serviceName=cp-test-service&port=20001"
```

### Step 2: 确认注册成功

```bash
# 对比查看 AP 和 CP 实例
curl http://host.docker.internal:8081/test/ap-vs-cp
```

预期：AP 和 CP 各有 3 个实例，全部 UP。

### Step 3: 查看 Nacos Console

打开 http://host.docker.internal:8848/nacos → 服务管理 → 服务列表

- `ap-test-service`: 实例标记为 **临时**
- `cp-test-service`: 实例标记为 **持久**

### Step 4: 模拟 1 节点故障 (2/3 多数仍存活)

```powershell
docker stop nacos3
```

```bash
# 再次对比
curl http://host.docker.internal:8081/test/ap-vs-cp
```

**预期结果：**
- ✅ AP 实例: 短暂可能有节点数据不一致，但最终全部可见
- ✅ CP 实例: 仍然全部可见且可读写 (2/3 多数 → Raft 仍有 Leader)

```bash
# 验证 CP 仍可写入
curl -X POST "http://host.docker.internal:8081/test/register/cp?serviceName=cp-test-service&port=20099"
# → 成功，因为 2/3 多数仍可选举 Leader
```

### Step 5: 模拟 2 节点故障 (1/3 失去多数)

```powershell
docker stop nacos2
```

```bash
# 再次对比
curl http://host.docker.internal:8081/test/ap-vs-cp

# 尝试 CP 写入
curl -X POST "http://host.docker.internal:8081/test/register/cp?serviceName=cp-test-service&port=20098"
# → 失败! 只剩 1/3 节点，Raft 无法选出 Leader

# 尝试 AP 写入
curl -X POST "http://host.docker.internal:8081/test/register/ap?serviceName=ap-test-service&port=10098"
# → 成功! AP 只写当前节点内存，不依赖多数
```

**预期结果：**
- ❌ CP 写入失败: "no leader" 或超时
- ✅ AP 写入成功: 仅存节点接受写入

### Step 6: 恢复节点

```powershell
docker start nacos2
docker start nacos3

# 等待几秒后查看
sleep 10
curl http://host.docker.internal:8081/test/ap-vs-cp
```

**预期：**
- CP 实例: Raft 重新选举 Leader，数据一致
- AP 实例: Distro 异步同步，最终全部可见

---

## API 速查表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/info` | 查看当前服务注册信息 |
| GET | `/services` | 列出所有已注册服务 |
| GET | `/services/{name}` | 查看指定服务的实例列表 |
| POST | `/test/register/ap?serviceName=xxx&port=yyy` | 注册 AP 临时实例 |
| POST | `/test/register/cp?serviceName=xxx&port=yyy` | 注册 CP 持久实例 |
| DELETE | `/test/deregister?serviceName=xxx&port=yyy` | 注销实例 |
| GET | `/test/ap-vs-cp` | AP vs CP 对比查询 |
| POST | `/test/batch-register?apCount=3&cpCount=3` | 批量注册 |

## 关键观察点

```
集群健康时:
  AP 写入 ✓  CP 写入 ✓  AP 读取 ✓  CP 读取 ✓

停 1 节点 (2/3):
  AP 写入 ✓  CP 写入 ✓  AP 读取 ✓  CP 读取 ✓
  → 多数仍存活，一切正常

停 2 节点 (1/3):
  AP 写入 ✓  CP 写入 ✗  AP 读取 ✓  CP 读取 (旧数据)
  → AP 仍可用，CP 因无 Leader 不可写

全部恢复:
  AP 最终一致 ✓  CP 重新选举 Leader，数据强一致 ✓
```

## 清理

```powershell
docker rm -f nacos1 nacos2 nacos3
```
