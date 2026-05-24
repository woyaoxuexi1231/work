# Docker 从容器基础到生产实践教程

> 目标：理解容器化原理，掌握 Docker 镜像构建、容器管理、网络存储、Compose 编排，以及与 K8s 的衔接
> 前置条件：已安装 Docker（`docker --version` 可用）

---

## 目录

- [一、Docker 到底解决了什么问题](#一docker-到底解决了什么问题)
- [二、核心概念](#二核心概念)
- [三、镜像管理 —— 构建与分发](#三镜像管理--构建与分发)
- [四、容器管理 —— 运行与调试](#四容器管理--运行与调试)
- [五、Dockerfile 最佳实践](#五dockerfile-最佳实践)
- [六、网络 —— 容器间通信](#六网络--容器间通信)
- [七、存储 —— 数据持久化](#七存储--数据持久化)
- [八、Docker Compose —— 多容器编排](#八docker-compose--多容器编排)
- [九、与 Kubernetes 的衔接](#九与-kubernetes-的衔接)
- [十、生产最佳实践](#十生产最佳实践)
- [附录：常用命令速查](#附录常用命令速查)

---

## 一、Docker 到底解决了什么问题

### 1.1 "在我机器上是好的" 困境

```
传统部署：
  开发者:  "我本地跑得好好的！"
  运维:    "服务器上报 500，依赖版本不对"
  原因:    开发环境 vs 生产环境不一致（OS/库/语言版本）

Docker 方案：
  开发者:  "我构建了一个镜像"
  运维:    "我直接跑这个镜像，环境完全一致"
  原因:    镜像包含应用 + 所有依赖，在不同机器上行为一致
```

### 1.2 容器 vs 虚拟机

```
┌─────────────────────┐    ┌─────────────────────┐
│   VM 虚拟机           │    │   容器               │
│                      │    │                      │
│  ┌──────┐ ┌──────┐  │    │  ┌──────┐ ┌──────┐  │
│  │ AppA │ │ AppB │  │    │  │ AppA │ │ AppB │  │
│  ├──────┤ ├──────┤  │    │  ├──────┤ ├──────┤  │
│  │ Libs │ │ Libs │  │    │  │ Libs │ │ Libs │  │
│  ├──────┤ ├──────┤  │    │  └──┬───┘ └──┬───┘  │
│  │Guest │ │Guest │  │    │     │        │      │
│  │ OS A │ │ OS B │  │    │  ┌──▼────────▼──┐   │
│  ├──────┴──────┘  │    │  │  Docker 引擎   │   │
│  │ Hypervisor      │    │  └───────┬───────┘   │
│  ├─────────────────┤    │  ┌───────▼───────┐   │
│  │ 宿主机 OS        │    │  │ 宿主机 OS      │   │
│  └─────────────────┘    │  └───────────────┘   │
└─────────────────────┘    └─────────────────────┘
  启动: 分钟级                   启动: 秒级
  大小: GB 级                    大小: MB 级
  资源: 完整 OS 开销             资源: 共享宿主机内核
  隔离: 硬件级隔离                隔离: 进程级隔离
```

### 1.3 Docker 的三大核心价值

| 价值 | 一句话 |
|------|--------|
| **环境一致性** | 镜像在哪跑都一样，告别环境不一致 |
| **轻量快速** | 秒级启动，资源开销远小于 VM |
| **标准化交付** | 构建 → 测试 → 部署 用同一份 Dockerfile |

---

## 二、核心概念

### 2.1 三要素关系

```
镜像 (Image)      = 应用的"安装包"（只读模板）
   │
   │ docker run
   ▼
容器 (Container)  = 镜像的一个"运行实例"（可读写）
   │
  docker push / docker pull
   ▼
仓库 (Registry)   = 存镜像的地方（Docker Hub / 私有仓库）
```

类比理解：

| Docker | 传统软件 | 类比 |
|--------|---------|------|
| Image | .exe 安装包 | 静态文件，不变 |
| Container | 正在运行的程序 | 每个实例独立，可读写 |
| Dockerfile | 安装说明书 | 每一步做什么 |
| Registry | 应用商店 | 下载/上传镜像 |

### 2.2 第一个命令

```bash
# 查看 Docker 信息
docker version
docker info

# 第一个容器
docker run hello-world

# 输出内容解读：
# 1. Docker 客户端连接 Docker 守护进程
# 2. 守护进程检查本地没有 hello-world 镜像
# 3. 从 Docker Hub 拉取
# 4. 从镜像创建容器并运行
# 5. 输出 Hello from Docker! 后退出
```

---

## 三、镜像管理 —— 构建与分发

### 3.1 镜像命名规范

```
[仓库地址/]镜像名[:标签]

示例:
nginx                   → Docker Hub 官方镜像，标签 latest
nginx:1.25-alpine       → 指定版本 + 轻量基础镜像
my-registry.cn/app:v1   → 私有仓库
my-registry.cn/app@sha256:xxx  → 用摘要引用（最精确，防篡改）
```

**永远不要在 production 用 `:latest`** —— 你不知道拉下来的是什么版本。

### 3.2 常用镜像操作

```bash
# 搜索镜像
docker search nginx --limit 10

# 拉取镜像
docker pull nginx:1.25-alpine
docker pull python:3.12-slim

# 查看本地镜像
docker images

# 查看镜像分层
docker history nginx:1.25-alpine

# 删除镜像
docker rmi nginx:1.25-alpine

# 导出/导入镜像（离线环境）
docker save nginx:1.25-alpine -o nginx.tar
docker load -i nginx.tar
```

### 3.3 动手：第一个自定义镜像

```bash
mkdir ~/docker-demo && cd ~/docker-demo
```

创建 `app.py`：

```python
# app.py
from http.server import HTTPServer, BaseHTTPRequestHandler

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-type', 'text/html')
        self.end_headers()
        self.wfile.write(b'<h1>Hello from Docker!</h1>')

HTTPServer(('0.0.0.0', 8080), Handler).serve_forever()
```

创建 `Dockerfile`：

```dockerfile
# 使用官方 Python 镜像作为基础
FROM python:3.12-slim

# 设置工作目录
WORKDIR /app

# 复制应用代码到镜像
COPY app.py .

# 暴露端口（文档性质，实际端口映射在 run 时指定）
EXPOSE 8080

# 容器启动时运行的命令
CMD ["python", "app.py"]
```

```bash
# 构建镜像
docker build -t my-python-app:v1 .

# 查看构建过程（每行都是一个 Layer）
docker images my-python-app

# 运行容器
docker run -d -p 8080:8080 --name my-app my-python-app:v1

# 测试
curl http://localhost:8080
# 输出: <h1>Hello from Docker!</h1>

# 查看容器日志
docker logs my-app

# 停止并删除
docker stop my-app && docker rm my-app
```

### 3.4 镜像分层原理

```
┌─────────────────────┐
│ Layer 6: CMD        │  ← 可写容器层（容器删除后丢失）
│ Layer 5: COPY app.py│
│ Layer 4: WORKDIR    │
│ Layer 3: apt install│  ← 镜像层（只读，共享）
│ Layer 2: OS 基础包   │
│ Layer 1: base image │
└─────────────────────┘
```

关键点：

- 每个 `RUN` / `COPY` / `ADD` 指令创建一个新层
- 层会被**缓存** —— 如果 Dockerfile 前面的层没变，构建时直接复用缓存
- **经常变化的指令放在 Dockerfile 后面**，最大化缓存命中

---

## 四、容器管理 —— 运行与调试

### 4.1 容器的生命周期

```
Created → Running → Paused
               ↓         ↓
            Stopped    Removed
```

```bash
# ── 创建并运行 ──
docker run -d --name web nginx:1.25-alpine
# -d: 后台运行 (detach)
# --name: 指定容器名
# -p 8080:80: 端口映射 (宿主机:容器)

# ── 查看 ──
docker ps                    # 运行中的容器
docker ps -a                 # 所有容器（包括已停止的）
docker stats                 # 实时资源使用

# ── 启动/停止/重启 ──
docker stop web              # 优雅停止（发 SIGTERM，10 秒后 SIGKILL）
docker start web             # 启动已停止的容器
docker restart web           # 重启
docker pause web             # 暂停（冻结进程）
docker unpause web

# ── 进入容器 ──
docker exec -it web /bin/sh       # 在运行中的容器执行命令
docker exec -it web cat /etc/nginx/nginx.conf

# ── 删除 ──
docker rm web                    # 删除已停止的容器
docker rm -f web                 # 强制删除运行中的容器
docker container prune           # 删除所有已停止的容器
```

### 4.2 端口映射详解

```bash
# 格式: -p <宿主机IP:宿主机端口>:<容器端口>

# 随机宿主机端口
docker run -d -p 80 nginx
docker ps
# PORTS: 0.0.0.0:32768->80/tcp

# 指定宿主机端口
docker run -d -p 8080:80 nginx

# 指定宿主机 IP + 端口
docker run -d -p 127.0.0.1:8080:80 nginx   # 仅本机可访问

# 多个端口
docker run -d -p 8080:80 -p 8443:443 nginx

# UDP 端口
docker run -d -p 5353:53/udp nginx
```

### 4.3 日志与调试

```bash
# 查看日志
docker logs web                    # 全部日志
docker logs --tail 100 web         # 最后 100 行
docker logs -f web                 # 实时跟踪 (类似 tail -f)
docker logs --since 5m web         # 最近 5 分钟
docker logs -t web                 # 加时间戳

# 查看容器详情
docker inspect web                 # 完整 JSON 信息
docker inspect web | jq '.[0].NetworkSettings.IPAddress'  # 容器 IP

# 查看容器内进程
docker top web

# 资源使用
docker stats                       # 实时，类似 top
docker stats --no-stream           # 单次快照

# 容器内文件复制
docker cp web:/etc/nginx/nginx.conf ./nginx.conf
docker cp ./my-config.conf web:/etc/nginx/conf.d/
```

### 4.4 交互式运行 vs 后台运行

```bash
# 交互式（前台）——调试用
docker run -it --rm ubuntu:22.04 /bin/bash
# -i: 交互模式 (stdin 打开)
# -t: 分配伪终端 (好看)
# --rm: 退出后自动删除容器

# 后台（守护式）——生产用
docker run -d --restart=always nginx
# -d: 后台运行
# --restart=always: 容器退出时自动重启
```

---

## 五、Dockerfile 最佳实践

### 5.1 常用指令速查

| 指令 | 作用 | 示例 |
|------|------|------|
| `FROM` | 指定基础镜像 | `FROM node:20-alpine` |
| `WORKDIR` | 设置工作目录 | `WORKDIR /app` |
| `COPY` | 复制文件进镜像 | `COPY package.json .` |
| `RUN` | 在构建时执行命令 | `RUN npm install` |
| `ENV` | 设置环境变量 | `ENV NODE_ENV=production` |
| `EXPOSE` | 声明容器端口 | `EXPOSE 3000` |
| `CMD` | 容器启动命令（默认） | `CMD ["node", "server.js"]` |
| `ENTRYPOINT` | 容器启动命令（不可覆盖） | `ENTRYPOINT ["python"]` |
| `ARG` | 构建参数 | `ARG VERSION=latest` |
| `HEALTHCHECK` | 健康检查 | `HEALTHCHECK CMD curl -f http://localhost` |
| `USER` | 指定运行用户 | `USER node` |

### 5.2 CMD vs ENTRYPOINT

```dockerfile
# CMD：可以被 docker run 参数覆盖
CMD ["node", "app.js"]
docker run my-image              # 执行 node app.js
docker run my-image node test.js # 执行 node test.js（覆盖）

# ENTRYPOINT：固定，不可覆盖
ENTRYPOINT ["node"]
CMD ["app.js"]
docker run my-image              # 执行 node app.js
docker run my-image test.js      # 执行 node test.js（追加）
```

### 5.3 多阶段构建

**解决的问题**：构建时需要编译工具链（Go/Java），运行时不需要。多阶段构建让最终镜像只包含运行所需文件。

```dockerfile
# ── 第一阶段：构建 ──
FROM golang:1.22 AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o server

# ── 第二阶段：运行 ──
FROM alpine:3.19
WORKDIR /app
COPY --from=builder /app/server .  # 只复制编译产物
EXPOSE 8080
CMD ["./server"]
```

对比：

| 方式 | 镜像大小 |
|------|---------|
| 单阶段（含 Go SDK） | ~1.2 GB |
| 多阶段（Alpine + 二进制） | ~15 MB |

```bash
# 演示效果
docker build -t my-go-app:multi -f Dockerfile.multi .
docker images my-go-app
```

### 5.4 .dockerignore

类似 `.gitignore`，防止不必要文件进入构建上下文：

```gitignore
# .dockerignore
node_modules
.git
*.md
Dockerfile
.gitignore
dist/*.map
__pycache__
.env
```

每个 `COPY . .` 前 Docker 先把构建上下文打包发给 daemon。**`node_modules` 不排除的话，几百 MB 数据每次构建都要传输**，极其慢。

### 5.5 缓存优化（重要）

Docker 构建时会缓存每一层。**把不常变的指令放前面，常变的放后面**：

```dockerfile
# ❌ 低效：代码变了，npm install 也要重跑
COPY . .
RUN npm install

# ✅ 高效：先 copy package.json，装依赖，再 copy 代码
COPY package.json package-lock.json ./
RUN npm install          # 只要 package.json 不变，这一层命中缓存
COPY . .                 # 代码变了，仅这一层及以后重建
```

### 5.6 完整示例：Python Web 应用

```dockerfile
# Dockerfile.python
FROM python:3.12-slim

# 设置环境变量
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# 先装依赖（利用缓存）
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 再复制代码
COPY . .

# 非 root 用户运行（安全）
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app
USER appuser

EXPOSE 8000
CMD ["gunicorn", "app:app", "--bind", "0.0.0.0:8000"]
```

```bash
docker build -t my-python-app:prod -f Dockerfile.python .
```

---

## 六、网络 —— 容器间通信

### 6.1 网络模式

```bash
# 查看 Docker 网络
docker network ls

# 三种内置网络
# bridge  - 默认，容器间通过 IP 通信（隔离性好）
# host    - 共享宿主机网络（性能好，但有端口冲突风险）
# none    - 无网络
```

| 模式 | 命令示例 | 适用场景 |
|------|---------|---------|
| **bridge**（默认） | `docker run nginx` | 单机多容器 |
| **host** | `docker run --network host nginx` | 性能敏感型 |
| **none** | `docker run --network none nginx` | 安全隔离 |

### 6.2 用户自定义网络（推荐）

**默认 bridge 网络的痛点**：容器只能通过 IP 通信，容器重启 IP 会变。

```bash
# 创建自定义网络
docker network create my-net

# 两个容器接入同一网络
docker run -d --name web --network my-net nginx:1.25-alpine
docker run -d --name db --network my-net mysql:8.0

# 在 web 容器中通过容器名访问 db
docker exec web ping db        # ✅ 能通！DNS 自动解析
docker exec db ping web        # ✅ 也能通
```

**自定义网络提供自动 DNS 解析** —— 同网络下的容器可以用容器名通信。这其实就是 K8s Service 服务发现的 Docker 版原型。

### 6.3 端口映射完整测试

```bash
# 启动一个多端口的容器
docker run -d \
  --name api \
  -p 8080:80 \
  -p 8443:443 \
  nginx:1.25-alpine

# 网络连通性
curl http://localhost:8080

# 查看端口映射
docker port api
# 输出: 80/tcp -> 0.0.0.0:8080
#       443/tcp -> 0.0.0.0:8443
```

### 6.4 容器间通信总结

```
同一个自定义网络:
  web ──── ping db ────→ ✅ (DNS 自动解析)
  db  ──── ping web ───→ ✅

跨网络（默认隔离）:
  web ──── ping db ───→ ❌ 不通
  需要 docker network connect 加入

外部访问:
  宿主机:8080 ───→ web:80
  外部机器 → 宿主机IP:8080
```

---

## 七、存储 —— 数据持久化

### 7.1 问题

容器删除后，内部数据全部丢失：

```bash
docker run --name test alpine echo "data" > /data/test.txt
docker rm test
# 数据丢了！
```

### 7.2 三种存储方案

| 方案 | 命令 | 谁管理 | 适用场景 |
|------|------|--------|---------|
| **Volume**（推荐） | `-v my-vol:/data` | Docker | 数据库、持久数据 |
| **Bind Mount** | `-v /host/path:/data` | 用户 | 开发热重载、配置文件 |
| **tmpfs** | `--tmpfs /data` | 内存 | 临时敏感数据 |

### 7.3 Volume（推荐）

```bash
# 创建 volume
docker volume create app-data

# 查看
docker volume ls
docker volume inspect app-data

# 使用 volume
docker run -d \
  --name mysql \
  -v app-data:/var/lib/mysql \    # 数据持久化
  -e MYSQL_ROOT_PASSWORD=root123 \
  mysql:8.0

# 即使删除容器，volume 仍在
docker rm -f mysql
docker volume ls  # app-data 还在

# 新容器可以复用
docker run -d \
  --name mysql-new \
  -v app-data:/var/lib/mysql \
  mysql:8.0
```

### 7.4 Bind Mount（开发常用）

```bash
# 将当前目录挂载到容器的 /app
# 修改宿主机文件，容器内立刻生效（热重载）
docker run -d \
  -v $(pwd):/app \
  -p 3000:3000 \
  node:20-alpine \
  node app.js

# 只读挂载（防止容器修改宿主机文件）
docker run -d \
  -v $(pwd)/config:/etc/config:ro \
  nginx
```

### 7.5 数据共享示例

```bash
# 创建数据 volume
docker volume create shared-data

# 写入数据
docker run --rm -v shared-data:/data alpine sh -c "echo 'Hello from container A' > /data/hello.txt"

# 另一个容器读取
docker run --rm -v shared-data:/data alpine cat /data/hello.txt
# 输出: Hello from container A
```

### 7.6 备份与恢复

```bash
# 备份
docker run --rm \
  -v app-data:/source:ro \
  -v $(pwd):/backup \
  alpine tar czf /backup/app-data-backup.tar.gz -C /source .

# 恢复
docker run --rm \
  -v app-data:/target \
  -v $(pwd):/backup \
  alpine tar xzf /backup/app-data-backup.tar.gz -C /target
```

---

## 八、Docker Compose —— 多容器编排

### 8.1 为什么需要 Compose？

一个典型的 Web 应用需要多个容器：

```
前端 (React/Vue) + 后端 (API) + 数据库 (MySQL) + 缓存 (Redis)
```

如果用 docker run 一个个启动：

```bash
# 手动启动 4 个容器，还要创建网络、调整顺序、记住参数...
docker network create my-app
docker run -d --network my-app --name db mysql:8.0
docker run -d --network my-app --name redis redis:7-alpine
docker run -d --network my-app --name api --env DB_URL=... my-api:v1
docker run -d --network my-app --name web -p 8080:80 my-web:v1
```

**Docker Compose** 用一个 YAML 描述所有服务，`docker compose up` 一键启动。

### 8.2 动手：完整的 Web 应用

```yaml
# docker-compose.yml
version: "3.8"

services:
  # ── 数据库 ──
  db:
    image: mysql:8.0
    container_name: demo-db
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: appdb
      MYSQL_USER: app
      MYSQL_PASSWORD: app123
    volumes:
      - db-data:/var/lib/mysql
    ports:
      - "3306:3306"
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ── Redis 缓存 ──
  redis:
    image: redis:7-alpine
    container_name: demo-redis
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s

  # ── 后端 API ──
  api:
    build:
      context: ./api
      dockerfile: Dockerfile
    container_name: demo-api
    environment:
      DB_HOST: db                # ← 服务名就是主机名！
      DB_USER: app
      DB_PASSWORD: app123
      DB_NAME: appdb
      REDIS_HOST: redis
    ports:
      - "8080:8080"
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_healthy

  # ── 前端 ──
  web:
    build:
      context: ./web
      dockerfile: Dockerfile
    container_name: demo-web
    ports:
      - "80:80"
    depends_on:
      - api

volumes:
  db-data:
  redis-data:
```

### 8.3 常用 Compose 命令

```bash
# 启动所有服务（-d 后台）
docker compose up -d

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f
docker compose logs -f api       # 只看某个服务

# 执行命令
docker compose exec api /bin/sh
docker compose exec db mysql -u root -p

# 重启单个服务
docker compose restart api

# 重新构建并启动（代码改了之后）
docker compose up -d --build

# 查看资源
docker compose top

# 停止但不删除
docker compose stop

# 停止并删除（volume 默认不删）
docker compose down
docker compose down -v    # 连 volume 一起删（数据丢失！慎用）

# 只重建某个服务
docker compose up -d --force-recreate --no-deps api
```

### 8.4 多环境配置

```bash
# docker-compose.yml —— 公共配置
# docker-compose.override.yml —— 开发环境覆盖（自动加载）
# docker-compose.prod.yml —— 生产环境覆盖（需指定）

# 开发环境：自动加载 override
docker compose up -d

# 生产环境
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

```yaml
# docker-compose.prod.yml（生产覆盖）
version: "3.8"
services:
  api:
    environment:
      NODE_ENV: production
    restart: always
    deploy:
      replicas: 3           # Swarm 模式下副本数

  web:
    restart: always
    deploy:
      replicas: 2
```

### 8.5 Compose 常用技巧

```yaml
# 开发环境热重载
services:
  api:
    volumes:
      - ./api:/app          # 代码挂载，修改即时生效
    command: npm run dev    # 开发模式（nodemon 热重载）

# 资源限制
services:
  api:
    deploy:
      resources:
        limits:
          cpus: "0.5"
          memory: "256M"
        reservations:
          cpus: "0.25"
          memory: "128M"
```

---

## 九、与 Kubernetes 的衔接

### 9.1 概念对照表

很多 K8s 概念在 Docker 中都有对应原型：

| Docker | Kubernetes | 关系 |
|--------|-----------|------|
| `docker run` | Pod | 运行一个容器单元 |
| `docker-compose.yml` | Deployment + Service YAML | 声明式编排 |
| Docker 网络 (自定义 bridge) | Service | 容器间 DNS 解析 |
| `-v volume:/data` | PersistentVolumeClaim | 持久化存储 |
| `HEALTHCHECK` | livenessProbe / readinessProbe | 健康检查 |
| `--restart=always` | restartPolicy: Always | 自愈策略 |
| `docker build -t` | 镜像构建（外部） | 镜像 + 仓库 |
| Docker Compose（单机） | K8s（集群） | 编排规模不同 |
| Dockerfile | 无（构建时用） | 镜像定义 |
| `docker login` | imagePullSecrets | 镜像仓库认证 |

### 9.2 从 Compose 到 K8s 的思维转变

```yaml
# Docker Compose 视角 - 每个服务一个容器
services:
  api:
    build: ./api
    ports:
      - "8080:8080"
    environment:
      DB_HOST: db
    depends_on:
      - db
  db:
    image: mysql:8.0
    volumes:
      - db-data:/var/lib/mysql
```

```yaml
# Kubernetes 视角 - 每个组件独立抽象
apiVersion: apps/v1
kind: Deployment          # 管理 Pod 的生命周期
metadata:
  name: api
spec:
  replicas: 3             # 多副本！
  selector: ...
  template:
    spec:
      containers:
      - name: api
        image: my-api:v1  # 已经构建好的镜像
---
apiVersion: v1
kind: Service             # 稳定的网络入口
metadata:
  name: api-svc
spec:
  selector:
    app: api
  ports:
  - port: 8080
---
apiVersion: v1
kind: ConfigMap           # 配置独立管理
metadata:
  name: api-config
---
apiVersion: v1
kind: PersistentVolumeClaim # 存储独立管理
```

**核心区别**：

| Docker Compose | Kubernetes |
|---------------|-----------|
| 单机 | 集群（多节点） |
| 容器名即 DNS | Service 提供 DNS |
| ports 暴露端口 | Service + Ingress |
| depends_on 控制顺序 | 无依赖顺序（用健康检查） |
| 重启策略简单 | 控制器（Deployment/StatefulSet） |
| 无自动伸缩 | HPA 自动伸缩 |

### 9.3 本地开发：Docker → K8s 的无缝过渡

开发时：用 Docker Compose（简单快速）
部署时：用 K8s YAML（生产级能力）

```bash
# 推荐流程
本地开发:  docker compose up -d --build   # 即时热重载
测试:      docker compose run --rm test
打包:      docker build -t my-app:${TAG} .
推送:      docker push registry/my-app:${TAG}
部署:      kubectl set image deployment/my-app app=registry/my-app:${TAG}
```

### 9.4 K8s 中使用的镜像质量要求

你在 K8s 中跑的镜像，应该满足：

- [ ] 镜像足够小（Alpine 或 Distroless 基础镜像）
- [ ] 不使用 root 用户运行（`USER` 指令）
- [ ] 有 HEALTHCHECK
- [ ] 正确设置 WORKDIR、暴露端口
- [ ] 标签有版本号（不用 `:latest`）
- [ ] 多阶段构建，不包含编译工具链

---

## 十、生产最佳实践

### 10.1 Dockerfile 安全

```dockerfile
# ❌ 不安全
FROM ubuntu:22.04
RUN apt update && apt install -y python3
COPY app.py /app/
CMD ["python3", "/app/app.py"]
# 问题: root 用户、镜像大、系统包版本不可控

# ✅ 安全
FROM python:3.12-slim
RUN useradd -m -u 1000 appuser
WORKDIR /app
COPY --chown=appuser:appuser app.py .
USER appuser                     # 非 root 运行
EXPOSE 8000
CMD ["python", "app.py"]
# 优点: 非 root、基础镜像小、版本锁定
```

### 10.2 镜像瘦身技巧

```dockerfile
# 1. 选择小的基础镜像
FROM alpine:3.19               # ~5 MB
FROM python:3.12-slim          # ~120 MB（推荐平衡点）
# FROM python:3.12             # ~1000 MB（太大了）

# 2. 多阶段构建
# 3. RUN 指令合并
RUN apt update && apt install -y \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*    # 清理 apt 缓存

# 4. 不要安装不必要的包
# 5. .dockerignore 排除无用文件
```

各基础镜像大小对比：

| 基础镜像 | 大小 |
|---------|------|
| `scratch`（空镜像） | 0 B |
| `alpine:3.19` | ~5 MB |
| `debian:12-slim` | ~80 MB |
| `ubuntu:22.04` | ~180 MB |
| `python:3.12-slim` | ~120 MB |
| `python:3.12` | ~1000 MB |
| `node:20` | ~1000 MB |
| `node:20-alpine` | ~120 MB |

### 10.3 日志处理

```dockerfile
# 应用日志必须写到 stdout/stderr，不是文件！
# Docker 会收集 stdout/stderr，写到文件会被浪费且无法使用 docker logs

# ❌ 错误：写日志到文件
RUN echo "log" >> /var/log/app.log

# ✅ 正确：写日志到 stdout
CMD ["node", "server.js"]  # 应用 console.log 默认到 stdout
```

### 10.4 常见陷阱

| 陷阱 | 表现 | 解决 |
|------|------|------|
| **使用 `:latest`** | 部署后版本不确定 | 指定精确版本：`nginx:1.25.3` |
| **容器内用 root** | 安全漏洞，挂载文件权限问题 | `USER appuser` |
| **不设置 --restart** | 容器挂了不自动恢复 | `--restart=unless-stopped` |
| **忽略 .dockerignore** | 构建上下文过大，构建慢 | 加上 `.dockerignore` |
| **每行一个 RUN** | 镜像层数过多，体积大 | 合并 RUN 指令 |
| **不清理 apt 缓存** | 镜像臃肿 | `rm -rf /var/lib/apt/lists/*` |
| **忘记 -d** | Ctrl+C 后容器停止 | 后台运行加 `-d` |
| **开发和生产用同一 Dockerfile** | 生产镜像含调试工具 | 多阶段构建或多 Dockerfile |

### 10.5 日常命令速查

```bash
# ── 清理 ──
docker system prune              # 清理停止的容器、无用网络、悬空镜像
docker system prune -a           # 更彻底，包括未使用的镜像
docker system df                 # 查看磁盘占用

# ── 日常调试 ──
docker logs -f --tail 100 <容器>
docker exec -it <容器> /bin/sh
docker inspect <容器> | jq '.[].State'         # 容器状态
docker inspect <容器> | jq '.[].Config.Env'    # 环境变量

# ── 镜像管理 ──
docker images --digests          # 查看镜像摘要
docker image prune               # 删除悬空镜像
docker tag old:tag new:tag       # 打标签
docker push registry/img:tag     # 推送

# ── 网络 ──
docker network ls
docker network inspect bridge
docker container inspect <容器> | jq '.[].NetworkSettings.Networks'

# ── 资源限制 ──
docker run --memory="256m" --cpus="0.5" nginx

# ── 端口占用 ──
docker port <容器>
```

---

## 附录：常用命令速查

### 镜像操作

```bash
docker pull <镜像>                        # 拉取
docker push <镜像>                        # 推送
docker build -t <名> <目录>               # 构建
docker images                             # 列表
docker rmi <镜像>                         # 删除
docker tag <镜像> <新名>                  # 打标签
docker save <镜像> -o file.tar            # 导出
docker load -i file.tar                   # 导入
docker history <镜像>                     # 查看分层
```

### 容器操作

```bash
docker run -d --name <名> <镜像>          # 后台运行
docker run -it --rm <镜像> /bin/sh        # 交互运行（用完即删）
docker ps                                 # 运行中的容器
docker ps -a                              # 所有容器
docker stop <容器>                        # 停止
docker start <容器>                       # 启动
docker restart <容器>                     # 重启
docker rm <容器>                          # 删除
docker rm -f <容器>                       # 强制删除
docker logs -f <容器>                     # 日志
docker exec -it <容器> /bin/sh            # 进入
docker cp <容器>:<路径> <本地路径>        # 复制出来
docker cp <本地路径> <容器>:<路径>        # 复制进去
docker inspect <容器>                     # 详情
docker stats                              # 资源监控
docker top <容器>                         # 进程查看
```

### Compose

```bash
docker compose up -d                      # 启动
docker compose down                       # 停止并删除
docker compose logs -f                    # 日志
docker compose ps                         # 状态
docker compose exec <服务> /bin/sh        # 进入某个服务
docker compose restart <服务>             # 重启某个服务
docker compose up -d --build              # 构建并启动
docker compose pull                       # 拉取最新镜像
```

---

> 📁 教程中的 Dockerfile 和 docker-compose.yml 示例可在同目录下创建实践
> 🔗 下一站：将教程中的 Docker 镜像部署到你的 K8s 集群中
