# Nacos AP vs CP 实验指南

## 核心概念

> **AP/CP 说的是 Nacos 集群节点之间的数据一致性策略，不是说你的业务服务。**

| 模式 | 对应配置 | 底层协议 | 含义 |
|------|----------|----------|------|
| AP | `ephemeral=true` (临时实例) | Distro | 优先可用，少数派也能写，允许短暂不一致 |
| CP | `ephemeral=false` (永久实例) | Raft | 优先一致，少数派拒绝写入 |

**实验思路：停掉 Nacos 节点 → 观察注册行为差异**

---

## 前置准备

```powershell
# 1. MYSQL 必须已运行 (你的 install_mysql.ps1)
# 2. 部署 3 节点 Nacos 集群
cd registry-20260608\nacos
.\install_nacos_cluster.ps1

# 3. 安装 Python 依赖
cd python_demo
pip install -r requirements.txt
```

验证集群就绪：
```powershell
curl http://localhost:8848/nacos/v1/ns/operator/metrics    # nacos1
curl http://localhost:8948/nacos/v1/ns/operator/metrics    # nacos2
curl http://localhost:9048/nacos/v1/ns/operator/metrics    # nacos3
```

---

## 实验：AP (临时实例)

### Step 1 - 启动 AP 模式的 Python 服务

```powershell
cd registry-20260608\nacos\python_demo

# 临时实例 = AP 模式
$env:NACOS_ADDR="localhost:8848"
$env:SERVICE_NAME="nacos-demo"
$env:SERVER_PORT="18080"
$env:EPHEMERAL="true"

python server.py
```

输出类似：
```
  Nacos Demo [AP] -> localhost:8848
  DESKTOP-XXX (192.168.1.100):18080  ephemeral=true
```

### Step 2 - 验证已注册

浏览器打开三个 Nacos 控制台：
- http://localhost:8848/nacos  (nacos1)
- http://localhost:8948/nacos  (nacos2)
- http://localhost:9048/nacos  (nacos3)

→ **服务列表** → 确认 `nacos-demo` 在三个节点都可见

命令行验证：
```powershell
curl http://localhost:8848/nacos/v1/ns/instance/list?serviceName=nacos-demo
curl http://localhost:8948/nacos/v1/ns/instance/list?serviceName=nacos-demo
curl http://localhost:9048/nacos/v1/ns/instance/list?serviceName=nacos-demo
```

三台都应该返回 `"hosts"` 里有一个实例。

### Step 3 - 制造网络分区：停掉 nacos2 和 nacos3

```powershell
docker stop nacos2 nacos3
```

现在 3 节点集群只剩 nacos1，它是少数派（1/3）。

### Step 4 - AP 测试：注册新临时实例

开第二个终端，再起一个 AP 服务，指向 nacos1：

```powershell
$env:NACOS_ADDR="localhost:8848"
$env:SERVICE_NAME="nacos-demo"
$env:SERVER_PORT="18081"
$env:EPHEMERAL="true"
python server.py
```

### 观察

**即使只剩 1 个 Nacos 节点，临时实例注册成功。**

```powershell
curl http://localhost:8848/nacos/v1/ns/instance/list?serviceName=nacos-demo
```

应该能看到 2 个实例（18080 + 18081）。

> **原因：临时实例走 Distro 协议，优先保证可用性(A)。节点独立接受写入，之后异步同步。**

---

## 实验：CP (永久实例)

### Step 5 - CP 测试：尝试注册永久实例

**nacos2 和 nacos3 仍处于 stop 状态。**

```powershell
$env:NACOS_ADDR="localhost:8848"
$env:SERVICE_NAME="nacos-demo"
$env:SERVER_PORT="18082"
$env:EPHEMERAL="false"        # ← CP 模式
python server.py
```

### 观察

**注册失败（或 timeout）。**

因为只剩 1/3 节点，Raft 无法达成多数派，拒绝写入。

> **原因：永久实例走 Raft 协议，优先保证一致性(C)。少数派不允许单独提交。**

### Step 6 - 恢复集群

```powershell
docker start nacos2 nacos3
```

等待 10-20 秒集群恢复。再次尝试 CP 注册：

```powershell
$env:NACOS_ADDR="localhost:8848"
$env:SERVER_PORT="18082"
$env:EPHEMERAL="false"
python server.py
```

**这次应该成功** — Raft 多数派重新形成。

---

## 清理

```powershell
# Ctrl+C 停掉所有 python server.py

# 删除 Nacos 中的实验数据
curl -X DELETE "http://localhost:8848/nacos/v1/ns/instance?serviceName=nacos-demo&ip=192.168.1.100&port=18080&ephemeral=true"
curl -X DELETE "http://localhost:8848/nacos/v1/ns/instance?serviceName=nacos-demo&ip=192.168.1.100&port=18081&ephemeral=true"
curl -X DELETE "http://localhost:8848/nacos/v1/ns/instance?serviceName=nacos-demo&ip=192.168.1.100&port=18082&ephemeral=false"
```

---

## 实验结论

```
Nacos AP (ephemeral=true):   少数派也能写 → 优先可用性
Nacos CP (ephemeral=false):  少数派拒绝写入 → 优先一致性

AP/CP 的本质是注册中心集群节点之间的数据一致性策略
停业务服务只会演示心跳/剔除，不是 AP/CP
```
