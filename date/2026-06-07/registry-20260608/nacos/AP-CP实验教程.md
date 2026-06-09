# Nacos AP / CP 实验教程

> 全程复制粘贴，不需要改任何代码。一步一步跟着走。

---

## 0. 前置确认

```powershell
docker ps --filter "name=nacos" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

✅ 应看到三行：`nacos1 / nacos2 / nacos3`，状态 `Up`。

打开浏览器访问 `http://localhost:8848/nacos`，账号 `nacos`，密码 `nacos`。

- 点 **集群管理 → 节点列表**，看到 3 个节点，状态都是 `UP`。

---

## 1. 实验一：注册中心 AP 模式

### 1.1 启动 3 个 Demo

开三个 PowerShell 窗口，每个贴一行：

**窗口 1（user，端口 18081）：**

```powershell
cd d:\project\work\date\2026-06-07\registry-20260608\nacos\user-demo-nacos-20260609
mvn spring-boot:run
```

**窗口 2（order，端口 18082）：**

```powershell
cd d:\project\work\date\2026-06-07\registry-20260608\nacos\order-demo-nacos-20260609
mvn spring-boot:run
```

**窗口 3（product，端口 18083）：**

```powershell
cd d:\project\work\date\2026-06-07\registry-20260608\nacos\product-demo-nacos-20260609
mvn spring-boot:run
```

等约 30 秒，刷新 Nacos 控制台 → **服务管理 → 服务列表**。

✅ 看到三个服务各 1 个健康实例。

### 1.2 验证：三个 Nacos 节点都能查到

```powershell
curl "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=user-demo-nacos-20260609"
curl "http://localhost:8849/nacos/v1/ns/instance/list?serviceName=user-demo-nacos-20260609"
curl "http://localhost:8850/nacos/v1/ns/instance/list?serviceName=user-demo-nacos-20260609"
```

✅ 三个节点返回的实例列表都包含 `user-demo`，Distro 协议异步复制到了所有节点。

### 1.3 停掉 nacos2

```powershell
docker stop nacos2
```

刷新控制台 → **集群管理 → 节点列表**。

✅ nacos2 变成 `DOWN`。

### 1.4 观察：业务照常

刷新控制台 → **服务管理 → 服务列表**。

✅ 三个 Demo **全部仍然健康**。

再查一下：

```powershell
curl "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=user-demo-nacos-20260609"
```

✅ 实例还在。

> **这就是 AP：注册中心挂了 1/3，剩下的节点依然完整对外提供注册 + 查询。Distro 协议允许节点间短暂不一致，优先生可不用。**

### 1.5 恢复 nacos2

```powershell
docker start nacos2
```

等 5-10 秒，控制台 → 集群管理 → 节点列表 → nacos2 恢复 `UP`。

---

## 2. 实验二：配置中心 CP 模式

### 2.1 正常发布一个配置

控制台 `http://localhost:8848/nacos` → **配置管理 → 配置列表** → 点 `+` 新建：

| 字段 | 填 |
|---|---|
| Data ID | `test-cp-demo` |
| Group | `DEFAULT_GROUP` |
| 配置内容 | `hello cp` |

点右下角"发布"。

✅ 发布成功。

### 2.2 停掉 nacos2 + nacos3

```powershell
docker stop nacos2 nacos3
```

控制台 → **集群管理 → 节点列表**。

✅ 只剩 nacos1 一个节点。

### 2.3 再发配置

控制台 → 配置管理 → 再点 `+`：

| 字段 | 填 |
|---|---|
| Data ID | `test-cp-demo-2` |
| 配置内容 | `hello cp again` |

点"发布"。

✅ **发布失败 / 超时 / 页面一直转圈。**

> **这就是 CP：配置中心走 Raft 协议，3 节点需要 ≥2 个同意才能写入。现在只剩 1 个节点，拿不到多数派，直接拒绝写入。**
>
> 宁可不可用，也不允许配置写乱。

### 2.4 恢复

```powershell
docker start nacos2 nacos3
```

等 5-10 秒，三个节点恢复 `UP`。再发 `test-cp-demo-2` → ✅ 成功。

---

## 3. AP vs CP 一眼看懂

| | 注册中心 | 配置中心 |
|---|---|---|
| 模式 | **AP** | **CP** |
| 协议 | Distro（异步复制） | Raft（强一致） |
| 停 1 台 Nacos | ✅ 业务照常 | ✅ 配置照常读写 |
| 停 2 台 Nacos | ✅ 服务照常注册 | ❌ 配置发不出去 |
| 设计倾向 | 可用优先 | 一致优先 |

---

## 4. 收尾

三个 Demo 的 PowerShell 窗口各自 `Ctrl+C` 关掉。

Nacos 集群一般不用停，留着下次实验继续用。要停的话：

```powershell
cd d:\project\work\date\2026-06-07\registry-20260608\nacos
docker compose -f cluster-hostname.yaml down
```

---

## 5. 一句话记住

> **注册中心 AP：挂节点还能用。**
> **配置中心 CP：挂多了写不进去。**
>
> Nacos 在同一套集群里同时跑着 AP（Distro）和 CP（Raft），各管各的事。
