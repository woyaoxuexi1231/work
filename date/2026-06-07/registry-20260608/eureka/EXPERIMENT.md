# Eureka AP 实验指南

## 核心概念

> **Eureka 是纯 AP 系统。宁可数据不一致，也要保证可用。**

- Peer-to-Peer 架构，无 Leader，每个节点平等
- 每个节点可独立接受注册/查询
- 自我保护模式：心跳低于阈值时不下线实例，防止网络抖动误杀

**实验思路：停掉 Eureka 节点 → 观察注册行为**

---

## 前置准备

```powershell
# 1. 部署 3 节点 Eureka 集群
cd registry-20260608\eureka\eureka-server
.\install_eureka_cluster.ps1

# 2. 安装 Python 依赖
cd ..\python_demo
pip install -r requirements.txt
```

验证集群：
- http://localhost:8761  (eureka1)
- http://localhost:8861  (eureka2)
- http://localhost:8961  (eureka3)

三个页面都应正常显示，DS Replicas 显示另外两个节点。

---

## 实验：Eureka AP

### Step 1 - 启动 Python 服务

```powershell
cd registry-20260608\eureka\python_demo

$env:EUREKA_ADDR="localhost:8761"
$env:SERVICE_NAME="PYTHON-DEMO"
$env:SERVER_PORT="18082"

python server.py
```

输出：
```
  Eureka Demo [AP] -> localhost:8761
  DESKTOP-XXX (192.168.1.100):18082
```

### Step 2 - 验证已注册

Eureka 控制台：
- http://localhost:8761 → **Instances currently registered: PYTHON-DEMO**
- http://localhost:8861 → 也应出现 PYTHON-DEMO
- http://localhost:8961 → 也应出现 PYTHON-DEMO

API 验证：
```powershell
# XML 格式
curl http://localhost:8761/eureka/v2/apps

# JSON 格式 (需 Accept header)
curl -H "Accept: application/json" http://localhost:8761/eureka/v2/apps
```

### Step 3 - 制造网络分区：停掉 eureka2 和 eureka3

```powershell
docker stop eureka2 eureka3
```

只剩 eureka1（1/3，少数派）。

### Step 4 - 注册新服务

开第二个终端，指向 eureka1：

```powershell
$env:EUREKA_ADDR="localhost:8761"
$env:SERVICE_NAME="PYTHON-DEMO-2"
$env:SERVER_PORT="18083"
python server.py
```

### 观察

**即使只剩 1 个 Eureka 节点，新服务注册成功。**

打开 http://localhost:8761 → 可以看到 PYTHON-DEMO 和 PYTHON-DEMO-2

> **原因：Eureka 是 AP 设计，Peer-to-Peer 架构。每个节点独立接受注册，优先可用性。**

### Step 5 - 恢复集群

```powershell
docker start eureka2 eureka3
```

等待 15-30 秒，刷新 http://localhost:8861 和 :8961

→ 节点恢复后，数据异步同步。可能 PYTHON-DEMO-2 还没出现（最终一致性），等一会就同步过来了。

---

## 清理

```powershell
# Ctrl+C 停掉所有 python server.py

# 通过 Eureka API 删除实例
$INST_ID = "DESKTOP-XXX:PYTHON-DEMO:18082"
curl -X DELETE "http://localhost:8761/eureka/v2/apps/PYTHON-DEMO/$INST_ID"
curl -X DELETE "http://localhost:8761/eureka/v2/apps/PYTHON-DEMO-2/DESKTOP-XXX:PYTHON-DEMO-2:18083"
```

---

## 实验结论

```
Eureka 纯 AP:
  - 即使只剩 1/3 节点，仍可注册和查询 (优先可用性)
  - Peer-to-Peer 无 Leader，不存在 Raft 的多数派问题
  - 自我保护模式防止因网络抖动误删实例
  - 数据最终一致（异步同步），可能出现短暂不一致

Nacos vs Eureka:
  Eureka   → 纯 AP
  Nacos    → AP (临时实例) + CP (永久实例) 可选
```
