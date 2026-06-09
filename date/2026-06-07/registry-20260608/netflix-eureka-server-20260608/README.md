# Eureka Server 集群 (Docker)

基于 Spring Cloud Netflix Eureka Server，Docker 一键部署 3 节点集群。

## 架构

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

- 3 个节点互相注册，形成集群
- Docker 容器间通过容器名（eureka1/eureka2/eureka3）通信，无需配 hosts

## 前置条件

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

## 集群故障测试

当前已关闭自我保护模式，可直接观察节点下线：

1. 打开 http://host.docker.internal:8761/ 观察 Dashboard → `DS Replicas` 应显示 eureka2、eureka3
2. 停止 eureka3：`docker stop eureka3`
3. 等待约 30-60 秒，刷新 Dashboard
4. **eureka3 应从 DS Replicas 中消失** ✅
5. 恢复：`docker start eureka3`

> 注意：当前 `enable-self-preservation: false`，适合测试。生产环境建议开启自我保护防止网络分区误判。

## 配置文件说明

| 文件 | 用途 |
|------|------|
| `application-docker.yml` | Docker 环境配置（自我保护已关闭，剔除间隔 5s） |
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
