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

> ⚠️ 网上很多人说"停 2/3 节点后配置发不出去"——这是**理论上的 Raft 行为**，但 Nacos 的 JRaft 实现允许 leader 在降级单节点时继续写入（通过 MySQL 保证数据不丢）。你刚刚也亲测确认了：停 nacos2+nacos3 后仍然能发配置。
>
> 所以本实验换个角度——不是看"写不写得进去"，而是看"数据丢不丢"。**配置中心的 CP 本质是：配置走 Raft + MySQL 双写，数据强一致不丢失。**

### 2.1 正常发布一个配置

控制台 `http://localhost:8848/nacos` → **配置管理 → 配置列表** → 点 `+` 新建：

| 字段 | 填 |
|---|---|
| Data ID | `test-cp-demo` |
| Group | `DEFAULT_GROUP` |
| 配置内容 | `before crash` |

点右下角"发布"。✅ 成功。

### 2.2 停掉全部 3 个 Nacos

```powershell
docker stop nacos1 nacos2 nacos3
```

```powershell
docker ps --filter "name=nacos"
```

✅ 全部空了，Nacos 集群彻底挂了。

### 2.3 去 MySQL 里找配置

```powershell
docker exec -it <你的 mysql 容器> mysql -uroot -p123456 -e "SELECT data_id, content FROM nacos_config.config_info WHERE data_id='test-cp-demo'"
```

✅ 输出：`test-cp-demo  |  before crash`

> 配置**还在 MySQL 里**。Nacos 全挂了，配置不丢。

### 2.4 重启 Nacos，验证配置恢复

```powershell
docker start nacos1 nacos2 nacos3
```

等 15 秒，打开 `http://localhost:8848/nacos` → **配置管理 → 配置列表**。

✅ `test-cp-demo` 还在，内容 `before crash`，完好无损。

### 2.5 对比：注册中心的 AP 实例呢？

三个 Demo 早就停了。重启 Nacos 后，去**服务管理 → 服务列表**看。

✅ 三个服务**全没了**。

> **这就是 AP vs CP 最核心的区别：**
> - **CP 的配置**落 MySQL，Nacos 全挂了重启，配置还在
> - **AP 的临时实例**只在内存，Nacos 重启就丢

### 2.6 那 CP 什么时候真正"写不了"？

停节点不是 CP 的"拒绝写入"场景。真正触发 Raft 多数派拒绝写入的是**网络分区**——节点都活着但互相连不上，此时少数派节点检测到自己不是 leader / 没有 follower 响应，会拒绝写入。

在本机用 Docker Desktop 做网络分区比较麻烦，核心理解就是：**CP 保证数据不丢，AP 不保证**。

---

## 3. AP vs CP 一眼看懂

| | 注册中心（AP） | 配置中心（CP） |
|---|---|---|
| 协议 | Distro（异步复制） | Raft + MySQL（强一致） |
| 数据存在哪 | Nacos 节点内存 | **MySQL 持久化** |
| Nacos 全部重启后 | ❌ 临时实例全丢 | ✅ 配置完好无损 |
| 设计倾向 | 可用优先 | 一致 + 持久 优先 |

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

> **注册中心 AP：Nacos 全挂了重启，服务实例全丢（内存）。**
> **配置中心 CP：Nacos 全挂了重启，配置一份不少（MySQL）。**
>
> Nacos 在同一套集群里同时跑着 AP（Distro 内存）和 CP（Raft + MySQL），各管各的事。
