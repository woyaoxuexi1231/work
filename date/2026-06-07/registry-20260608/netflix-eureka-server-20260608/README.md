# Eureka Server 集群

基于 Spring Cloud Netflix Eureka Server，支持 **Windows Desktop 本地运行** 和 **Docker 容器部署**。

## Windows Desktop 快速启动（推荐）

### 一键启动

```powershell
.\start-cluster.ps1
```

脚本会自动编译并启动 3 个 Eureka 节点（3 个独立 PowerShell 窗口），无需 Docker。

### 手动启动

```powershell
mvn clean package -DskipTests -q

# 三个独立窗口各执行一条：
java -Xms128m -Xmx256m -jar target/netflix-eureka-server.jar --spring.profiles.active=peer1
java -Xms128m -Xmx256m -jar target/netflix-eureka-server.jar --spring.profiles.active=peer2
java -Xms128m -Xmx256m -jar target/netflix-eureka-server.jar --spring.profiles.active=peer3
```

### 访问

| 节点 | URL |
|------|-----|
| peer1 | http://127.0.0.1:12001/ |
| peer2 | http://127.0.0.1:12002/ |
| peer3 | http://127.0.0.1:12003/ |

### 测试集群故障

1. 打开 peer1 Dashboard → `DS Replicas` 应显示 peer2、peer3
2. 关掉 peer3 的 PowerShell 窗口（模拟宕机）
3. 等待 30-60 秒，刷新 peer1 Dashboard
4. **peer3 应该从 DS Replicas 中消失** ✅

> 注意：peer3 停了之后，peer1/peer2 日志里会出现连接 peer3 失败的 WARN，这是正常的。

---

## Docker 部署

### 架构

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

### 前置条件

| 工具 | 版本要求 |
|------|----------|
| JDK | 1.8+ |
| Maven | 3.6+ |
| Docker | 20.10+ | |

## 快速开始

### 1. 构建镜像

```powershell
.\build_eureka.ps1
```

> 脚本会依次执行 `mvn clean package -DskipTests` 和 `docker build -t eureka-server:latest .`

### 2. 启动集群

```powershell
.\install_eureka.ps1
```

### 3. 访问 Dashboard

| 节点 | URL |
|------|-----|
| eureka1 | http://host.docker.internal:8761/ |
| eureka2 | http://host.docker.internal:8762/ |
| eureka3 | http://host.docker.internal:8763/ |

## 自定义配置

### 更改外部端口

```powershell
$env:EUREKA_PORT1=18761
$env:EUREKA_PORT2=18762
$env:EUREKA_PORT3=18763
.\install_eureka.ps1
```

### 自定义镜像标签

```powershell
# 构建时指定标签
.\build_eureka.ps1 eureka-server:v1.0

# 启动时指定镜像
$env:EUREKA_IMAGE="eureka-server:v1.0"
.\install_eureka.ps1
```

## 常用命令

```powershell
# 查看节点日志
docker logs -f eureka1
docker logs -f eureka2

# 停止集群
docker stop eureka1 eureka2 eureka3

# 启动集群
docker start eureka1 eureka2 eureka3

# 彻底删除集群
docker rm -f eureka1 eureka2 eureka3
docker network rm eureka-net
```

## 自我保护模式测试

Eureka 的自我保护机制用于防止网络分区时误剔除健康实例：

1. 打开 http://host.docker.internal:8761/ 观察 Dashboard
2. 停止一个节点：`docker stop eureka3`
3. 观察剩余节点的 Dashboard，会出现红字警告进入自我保护模式
4. 恢复节点：`docker start eureka3`

## 配置文件说明

| 文件 | 用途 |
|------|------|
| `application-docker.yml` | Docker 环境配置，通过环境变量驱动 |
| `application-peer1/2/3.yml` | 本地开发集群配置（端口 12001/12002/12003） |
| `application-dev.yml` | 单机开发配置（端口 10001） |

### Docker 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SPRING_PROFILES_ACTIVE` | `docker` | Spring 激活配置 |
| `SERVER_PORT` | `8761` | 容器内部端口 |
| `EUREKA_HOSTNAME` | `eureka1` | Eureka 实例主机名 |
| `EUREKA_DEFAULT_ZONE` | 3 节点地址 | 集群其他节点地址 |
| `JAVA_OPTS` | `-Xms256m -Xmx512m` | JVM 参数 |
