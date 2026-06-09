# Nacos 三节点集群部署教程（MySQL 外置于宿主机）

> 本教程基于官方 `cluster-hostname.yaml` 改造，**复用你已经通过 `install_mysql.ps1` 部署的 MySQL**，
> 只用 `docker compose` 起 3 节点 Nacos 集群。
>
> 适用环境：Windows + Docker Desktop。

---

## 1. 最终架构

```
┌──────────────────────────────────────────────────────────┐
│                     宿主机 (Windows)                      │
│                                                          │
│  MySQL 容器: mysql                                       │
│   └─ 3306                                                │
│                                                          │
│  Nacos 集群（docker compose 启动）:                       │
│   ├─ nacos1 : 8848 (容器内)  -> 宿主机 8848  /  9848    │
│   ├─ nacos2 : 8848 (容器内)  -> 宿主机 8849  /  9849    │
│   └─ nacos3 : 8848 (容器内)  -> 宿主机 8850  /  9850    │
│                                                          │
│  持久化目录:                                              │
│   C:\Users\code\Desktop\docker-data\nacos-cluster\logs   │
│   C:\Users\code\Desktop\docker-data\nacos-cluster\data   │
└──────────────────────────────────────────────────────────┘
```

每个 nacos 节点开放 3 个端口：

| 节点  | 8848 (HTTP) | 9848 (gRPC client) | 9849 (gRPC raft) |
|-------|-------------|--------------------|------------------|
| nacos1| 8848        | 9848               | 9849             |
| nacos2| 8849        | 9849               | 9850             |
| nacos3| 8850        | 9851               | 9852             |

> ⚠️ Nacos 2.x 引入了 gRPC，**每个节点必须同时开放 8848 + 9848 + 9849 三个端口**。
> 9848 是 gRPC client 连接端口（应用连），9849 是 gRPC raft 通信端口（节点间 Raft 选举）。

---

## 2. 文件清单

| 文件 | 作用 |
|------|------|
| `install_mysql.ps1` | 部署 MySQL 8.1 容器（先跑这个） |
| `sql.sql` | Nacos 集群初始化表结构（12 张表） |
| `nacos-hostname.env` | Nacos 节点环境变量（数据源、集群列表、鉴权） |
| `cluster-hostname.yaml` | docker compose 编排文件（3 节点） |
| `install_nacos_cluster.ps1` | **一键部署脚本**（本教程主角） |
| `lib/common.ps1` | 公共函数（日志、检查容器、清理、等待） |

---

## 3. 一键部署步骤

### Step 1：先部署 MySQL

如果还没跑过 MySQL 脚本，先执行：

```powershell
cd registry-20260608\nacos
.\install_mysql.ps1
```

成功后输出类似：

```
[INFO] === MySQL | Port: 3306 | Pass: 123456 | Data: C:\Users\code\Desktop\docker-data\mysql-data ready ===
```

### Step 2：执行集群部署脚本

```powershell
cd registry-20260608\nacos
.\install_nacos_cluster.ps1
```

脚本会自动完成以下事情：

1. **检查 Docker** 是否启动
2. **检查 MySQL 容器** `mysql` 是否存在并运行（不存在会报错让你先去跑 `install_mysql.ps1`）
3. **等待 MySQL 就绪**（`mysqladmin ping`）
4. **创建数据库** `nacos_devtest` 和用户 `nacos/nacos`（如果不存在，幂等）
5. **导入 `sql.sql`** 到 `nacos_devtest`（仅当库为空时执行，幂等）
6. **拉镜像** `nacos/nacos-server:v2.3.2`
7. **`docker compose` 启动 3 节点**
8. **轮询健康检查**（最多 3 分钟），等 Leader 选举完成
9. **打印集群信息**（端口、账号、验证命令）

### Step 3：验证集群

```powershell
# 1. 查看 3 个节点是否都 Up
docker ps | Select-String "nacos"

# 2. 查看集群节点列表
curl http://localhost:8848/nacos/v1/core/cluster/nodes

# 3. 浏览器打开控制台
#    http://localhost:8848/nacos
#    账号: nacos / nacos
```

期望看到 3 个节点的 IP 和状态，状态都是 `UP`。

---

## 4. 关键配置说明

### 4.1 `nacos-hostname.env` 改了什么

| 项 | 官方原值 | 改后值 | 原因 |
|----|----------|--------|------|
| `MYSQL_SERVICE_HOST` | `mysql` | `host.docker.internal` | MySQL 不再在 compose 里，而是部署在宿主机上。`host.docker.internal` 是 Docker Desktop 提供的宿主机回环域名 |
| `NACOS_AUTH_ENABLE` | （没有） | `true` | 开启鉴权，控制台必须登录 |
| `MYSQL_SERVICE_DB_PARAM` | 没带时区 | 加了 `serverTimezone=Asia/Shanghai` | MySQL 8 必带时区，否则报 `The server time zone value is unrecognized` |

### 4.2 `cluster-hostname.yaml` 改了什么

- **去掉了 `mysql` service** —— MySQL 已经在宿主机上由 `install_mysql.ps1` 部署
- **去掉了 `depends_on` + `healthcheck`** —— 不再依赖 compose 内 MySQL
- **每个节点都加了 `extra_hosts: host.docker.internal:host-gateway`** —— 让容器内能解析 `host.docker.internal`（Linux containerd 也兼容）
- **数据卷加了 `cluster-data`** —— Raft 元数据 + 配置快照持久化
- **9848/9849 端口号修正** —— 原官方文件 `nacos2` 把 9848 错配成 9868、9849 错配成 9850，这里按 `宿主机端口 = 节点序号 × 偏移` 错开避免冲突

### 4.3 MySQL 数据库怎么准备

脚本会**幂等**地执行下面这段 SQL（你可以手动跑一遍验证）：

```sql
CREATE DATABASE IF NOT EXISTS nacos_devtest
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

CREATE USER IF NOT EXISTS 'nacos'@'%' IDENTIFIED BY 'nacos';

GRANT ALL PRIVILEGES ON nacos_devtest.* TO 'nacos'@'%';

FLUSH PRIVILEGES;
```

然后从 `sql.sql` 导入表结构（Nacos 官方提供的 12 张表：`config_info` / `config_info_aggr` / `his_config_info` / `users` / `roles` / `permissions` ...）。

> 💡 `users` 表里默认有一条账号 `nacos` / 密码 `nacos`（BCrypt 加密），对应控制台登录。

---

## 5. 端口总览

| 服务 | 宿主机端口 | 用途 |
|------|------------|------|
| MySQL | 3306 | 持久化 |
| nacos1 | 8848 / 9848 / 9849 | HTTP / gRPC client / gRPC raft |
| nacos2 | 8849 / 9849 / 9850 | 同上（端口错开） |
| nacos3 | 8850 / 9851 / 9852 | 同上（端口错开） |

客户端应用连接时**任选一个 HTTP 端口**即可（建议用 `8848,8849,8850` 三个全配上，客户端会自动重试）：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        # 多个地址用逗号分隔，SDK 内部会轮询 / 失败重试
        server-addr: 127.0.0.1:8848,127.0.0.1:8849,127.0.0.1:8850
```

> 注意：**gRPC 端口（9848/9849/9850）SDK 是自动推导的**（HTTP 端口 + 1000/+1001），不用你手动配。

---

## 6. 常用管理命令

```powershell
# 查看集群节点
curl http://localhost:8848/nacos/v1/core/cluster/nodes

# 查看 Leader 是谁
curl http://localhost:8848/nacos/v2/core/cluster/node/self

# 停止集群（保留数据）
docker compose -f cluster-hostname.yaml -p nacos-cluster stop

# 启动已停止的集群
docker compose -f cluster-hostname.yaml -p nacos-cluster start

# 销毁集群（数据卷保留）
docker compose -f cluster-hostname.yaml -p nacos-cluster down

# 彻底清理（删容器 + 删数据卷）
docker compose -f cluster-hostname.yaml -p nacos-cluster down -v
Remove-Item -Recurse -Force "$env:USERPROFILE\Desktop\docker-data\nacos-cluster"
```

---

## 7. 常见问题排查

### Q1：启动后控制台 404 / 503

```powershell
# 看 nacos1 日志，等看到 "Nacos started successfully in cluster mode"
docker logs -f nacos1 | Select-String "started successfully"
```

启动到集群就绪大约 30~60 秒（要等 Raft 选 Leader），期间访问会 503，**不是错误**。

### Q2：报 `UnknownHostException: host.docker.internal`

`extra_hosts` 没生效。Docker Desktop for Windows 默认会注入这个域名，但如果你用的是
WSL2 backend 或自定义网络，**改成宿主机真实 IP** 即可，比如：

```powershell
# 查宿主机 IP
ipconfig | Select-String "IPv4"
```

得到 `192.168.3.100` 后，编辑 `nacos-hostname.env`：

```env
MYSQL_SERVICE_HOST=192.168.3.100
```

### Q3：报 `Access denied for user 'nacos'@'xxx'`

容器连到 MySQL 了但密码不对。检查 `nacos-hostname.env` 里的 `MYSQL_SERVICE_USER` /
`MYSQL_SERVICE_PASSWORD` 是否和预期一致。脚本默认是 `nacos/nacos`，并且脚本会**自动**用 root 帮你创建这个账号。

如果想自己手动改：

```powershell
docker exec -it mysql mysql -uroot -p123456
```

```sql
ALTER USER 'nacos'@'%' IDENTIFIED BY '你的新密码';
FLUSH PRIVILEGES;
```

再把 `nacos-hostname.env` 里的 `MYSQL_SERVICE_PASSWORD` 同步改掉，重启 nacos：

```powershell
docker compose -f cluster-hostname.yaml -p nacos-cluster restart
```

### Q4：`get config:fail` / `receive invalid redirect request from peer 172.x.x.x`

这是**应用端**连接集群时遇到的典型问题：Nacos 节点之间用 hostname 通信没问题，
但应用收到重定向时拿到的是容器内网 IP（172.x.x.x），从宿主机/办公网访问不到。

解决：
1. 优先用**多地址**写法（`8848,8849,8850`），SDK 会从你能连上的节点拿到响应
2. 实在不行就在 `application.yml` 里只写 `127.0.0.1:8848`（单地址），绕过重定向

### Q5：删除容器后再跑脚本，提示 "容器已存在"

脚本检测到 `nacos1/nacos2/nacos3` 容器存在（停止状态）会跳过。想重新创建：

```powershell
docker rm -f nacos1 nacos2 nacos3
# 同时清掉 volumes 才能彻底重建
docker volume prune
.\install_nacos_cluster.ps1
```

> 注意：`docker compose down -v` 会把 data 目录挂载卷也删掉，会清空 Raft 元数据（**配置信息仍在 MySQL 里**，不影响）。

### Q6：`users` 表里没有 `nacos` 账号

`sql.sql` 末尾有这条 INSERT：

```sql
INSERT INTO users (username, password, enabled) VALUES
  ('nacos', '$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu', TRUE);

INSERT INTO roles (username, role) VALUES ('nacos', 'ROLE_ADMIN');
```

密码 `nacos` 经过 BCrypt 加密后就是这个 hash，对应登录用 `nacos/nacos`。

---

## 8. 跑通后下一步

集群起来后可以参考 `AP-CP实验教程.md` 玩 AP/CP 模式实验，或者直接启动那 3 个 demo：

```powershell
cd registry-20260608\nacos\user-demo-nacos-20260609
mvn spring-boot:run

# 再开一个窗口
cd registry-20260608\nacos\order-demo-nacos-20260609
mvn spring-boot:run

# 再开一个窗口
cd registry-20260608\nacos\product-demo-nacos-20260609
mvn spring-boot:run
```

打开 `http://localhost:8848/nacos` → 服务列表，能看到这 3 个服务都注册上来了。
