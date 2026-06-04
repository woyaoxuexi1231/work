# Docker 组件安装脚本编写规范 (Skill)

当你需要新建一个 Docker 组件的安装脚本时，严格遵循以下规范。

---

## 一、文件命名

```
component_install_<组件名>_docker.sh
```

> 示例：`component_install_mysql_docker.sh`、`component_install_redis_docker.sh`

---

## 二、公共库（已有，直接 source 用）

`lib/common.sh` 提供了以下函数，**脚本里不要再重复定义**：

| 函数 | 用途 | 参数 |
|------|------|------|
| `log_info "msg"` | 输出 INFO 日志 | 字符串 |
| `log_warn "msg"` | 输出 WARN 日志 | 字符串 |
| `log_error "msg"` | 输出 ERROR 日志 | 字符串 |
| `check_docker` | 检查 Docker 是否安装且运行，否则报错退出 | 无 |
| `check_container_exists "name"` | 检查容器是否存在，存在返回 0 | 容器名 |
| `cleanup_container "name"` | 强制删除已有容器（用于重建） | 容器名 |
| `pull_image "image:tag"` | 拉取镜像（已存在则跳过） | 镜像全名 |
| `wait_for_container "name" 60` | 等待容器启动成功 | 容器名, 超时秒数 |
| `done_banner "MySQL"` | 打印完成分隔线 | 组件名 |

---

## 三、脚本结构（强制，七段式）

```bash
#!/usr/bin/env bash
# <组件名> <版本> | Port: <端口> | <凭据信息>
# export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
# ==== 前置检查 ====
# ==== 数据目录 ====
# ==== (配置文件) ====          ← 可选，仅组件需要自定义配置时加
# ==== 拉取镜像 ====
# ==== 启动容器 ====
# ==== 验证 ====                ← 可选
# ==== 完成 ====
```

所有数据统一挂载到 Windows 路径：

```
C:\Users\15434\Desktop\docker-data\<组件名>-data\
```

---

## 四、各段详细规则

### 4.1 配置段

用**单个大写字母**做变量名，减少重复：

```bash
# ==== 配置 ====
C="redis"                                    # 容器名
I="redis:${REDIS_VERSION:-7.2}"              # 镜像（带环境变量覆盖）
P="${REDIS_PORT:-6379}"                      # 端口（带环境变量覆盖）
PASS="${REDIS_PASSWORD:-123456}"             # 凭据（带环境变量覆盖）
DATA="${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}/redis-data"   # 数据目录
```

命名规则：

| 变量 | 含义 | 固定字母 |
|------|------|----------|
| `C` | Container name | C |
| `I` | Image | I |
| `P` | Primary port | P |
| `U` | Username | U |
| `PASS` | Password | PASS |
| `DATA` | Data mount path | DATA |

> 多端口场景：`AP`(AMQP), `MP`(Management), `CP`(Console), `HP`(HTTP), `DP`(DNS)

### 4.2 前置检查

```bash
# ==== 前置检查 ====
check_docker                                    # 1. Docker 运行检查
check_container_exists "${C}" && exit 0         # 2. 幂等：已存在则跳过
cleanup_container "${C}"                        # 3. 删除旧容器（重建场景）
```

### 4.3 数据目录

```bash
# ==== 数据目录 ====
mkdir -p "${DATA}/data" "${DATA}/log" || {      # 创建子目录
  log_error "无法创建 ${DATA}，检查 Docker Desktop 文件共享设置"
  exit 1
}
```

规则：
- 目录结构为 `${DATA}/data` + `${DATA}/log`，需要配置文件的加 `${DATA}/conf`
- Docker Desktop 的 File Sharing 必须包含 C 盘

### 4.4 配置文件（可选）

仅当组件需要自定义配置时添加。用 heredoc，注意权限：

```bash
# ==== 配置文件 ====
[[ -f "${DATA}/conf/my.cnf" ]] || cat > "${DATA}/conf/my.cnf" <<'CNF'
[client] default-character-set=utf8mb4
[mysqld] port=3306; character-set-server=utf8mb4
CNF
```

规则：
- 用 `[[ -f ... ]] ||` 避免每次覆盖已有配置
- heredoc 标记用单引号 `'CNF'` 防止变量展开
- 配置内容尽量精简，只留必要的

### 4.5 拉取镜像

```bash
# ==== 拉取镜像 ====
pull_image "${I}"
```

### 4.6 启动容器

```bash
# ==== 启动容器 ====
docker run -d --name "${C}" --restart=unless-stopped \
  -p ${P}:6379 \
  -e TZ=Asia/Shanghai \
  -v "${DATA}/data:/data" \
  "${I}" redis-server --requirepass "${PASS}" --appendonly yes
wait_for_container "${C}" 30
```

规则：
- **必须**加 `-e TZ=Asia/Shanghai`
- `--restart` 用 `unless-stopped`
- 每个 `-v`、`-e`、`-p` 单独一行
- 端口映射：左边宿主机 `${P}`，右边容器内固定端口
- `wait_for_container` 超时秒数根据组件启动速度调整（轻量 15-30s，重量 60-120s）

### 4.7 验证（可选）

```bash
# ==== 验证 ====
for i in $(seq 1 15); do
  docker exec "${C}" redis-cli -a "${PASS}" ping 2>/dev/null | grep -q PONG && break
  sleep 1
done
```

> 只在组件有快速健康检查命令时加。没有就省略。

### 4.8 完成

```bash
# ==== 完成 ====
done_banner "Redis | Port: ${P} | Pass: ${PASS} | Data: ${DATA}"
```

---

## 五、完整模板

### 无配置文件的简单组件（如 Redis、Mongo、ZK）

```bash
#!/usr/bin/env bash
# Redis 7.2 | Port: 6379 | Pass: 123456
# export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="redis"; I="redis:${REDIS_VERSION:-7.2}"; P="${REDIS_PORT:-6379}"
PASS="${REDIS_PASSWORD:-123456}"
DATA="${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}/redis-data"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

# ==== 数据目录 ====
mkdir -p "${DATA}/data" || { log_error "无法创建 ${DATA}"; exit 1; }

# ==== 拉取镜像 ====
pull_image "${I}"

# ==== 启动容器 ====
docker run -d --name "${C}" --restart=unless-stopped -p ${P}:6379 -e TZ=Asia/Shanghai \
  -v "${DATA}/data:/data" \
  "${I}" redis-server --requirepass "${PASS}" --appendonly yes
wait_for_container "${C}" 30

# ==== 验证 ====
for i in $(seq 1 15); do
  docker exec "${C}" redis-cli -a "${PASS}" ping 2>/dev/null | grep -q PONG && break
  sleep 1
done

# ==== 完成 ====
done_banner "Redis | Port: ${P} | Pass: ${PASS} | Data: ${DATA}"
```

### 带配置文件的组件（如 MySQL）

```bash
#!/usr/bin/env bash
# MySQL 8.1 | Port: 3306 | Pass: 123456
# export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="mysql"; I="mysql:${MYSQL_VERSION:-8.1}"; P="${MYSQL_PORT:-3306}"
PASS="${MYSQL_ROOT_PASSWORD:-123456}"
DATA="${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}/mysql-data"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

# ==== 数据目录 ====
mkdir -p "${DATA}/conf" "${DATA}/data" "${DATA}/log" || {
  log_error "无法创建 ${DATA}"; exit 1
}

# ==== 配置文件 ====
[[ -f "${DATA}/conf/my.cnf" ]] || cat > "${DATA}/conf/my.cnf" <<'CNF'
[client] default-character-set=utf8mb4
[mysqld] user=mysql; port=3306; character-set-server=utf8mb4; default-storage-engine=InnoDB
CNF

# ==== 拉取镜像 ====
pull_image "${I}"

# ==== 启动容器 ====
docker run -d --name "${C}" --restart=unless-stopped -p ${P}:3306 \
  -e "MYSQL_ROOT_PASSWORD=${PASS}" -e TZ=Asia/Shanghai \
  -v "${DATA}/conf/my.cnf:/etc/mysql/my.cnf" \
  -v "${DATA}/data:/var/lib/mysql" -v "${DATA}/log:/var/log/mysql" \
  "${I}"
wait_for_container "${C}" 60

# ==== 验证 ====
for i in $(seq 1 30); do
  docker exec "${C}" mysqladmin ping -h localhost --silent 2>/dev/null && break
  sleep 2
done

# ==== 完成 ====
done_banner "MySQL | Port: ${P} | Pass: ${PASS} | Data: ${DATA}"
```

### 需要后置安装的组件（如 Jenkins）

```bash
#!/usr/bin/env bash
# Jenkins LTS + JDK8 + Docker CLI | Port: 8080, 50000
# export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="jenkins"; I="jenkins/jenkins:lts"; P="${JENKINS_PORT:-8080}"
DATA="${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}/jenkins-data"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

# ==== 数据目录 ====
mkdir -p "${DATA}" || { log_error "无法创建 ${DATA}"; exit 1; }

# ==== 拉取镜像 ====
pull_image "${I}"

# ==== 启动容器 ====
docker run -d --name "${C}" --restart=unless-stopped -p ${P}:8080 \
  -e TZ=Asia/Shanghai -e DOCKER_HOST=tcp://host.docker.internal:2375 \
  -v "${DATA}:/var/jenkins_home" \
  "${I}"
wait_for_container "${C}" 120

# ==== 后置安装 ====
# Docker CLI
docker exec -u root "${C}" bash -c 'apt-get update -qq && apt-get install -y -qq docker.io && apt-get clean'

# JDK8
docker exec -u root "${C}" bash -c '
  mkdir -p /usr/lib/jvm; cd /tmp
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/8/ga/linux/x64/jdk/hotspot/normal/eclipse" -o jdk8.tar.gz
  tar xzf jdk8.tar.gz -C /usr/lib/jvm; rm -f jdk8.tar.gz
  JDK8_DIR=$(ls -d /usr/lib/jvm/jdk8* 2>/dev/null | head -1)
  ln -sfn "$JDK8_DIR" /usr/lib/jvm/java-8-openjdk-amd64
'

# ==== 验证 ====
docker exec "${C}" docker --version
docker exec "${C}" /usr/lib/jvm/java-8-openjdk-amd64/bin/java -version 2>&1 | head -1

# ==== 完成 ====
done_banner "Jenkins | http://localhost:${P}"
```

---

## 六、自检清单

生成新脚本后，逐项检查：

- [ ] 文件名：`component_install_<组件>_docker.sh`
- [ ] 第二行是 `# 组件名 版本 | Port: XXX | ...`
- [ ] `source "${SCRIPT_DIR}/lib/common.sh"` 正确引用公共库
- [ ] 配置变量用大写单字母：`C`、`I`、`P`、`PASS`、`DATA`
- [ ] `DATA` 路径为 `/c/Users/15434/Desktop/docker-data/<组件>-data`
- [ ] 镜像版本、端口、密码都支持环境变量覆盖（`${ENV_VAR:-默认值}`）
- [ ] 七段结构完整：配置 → 前置检查 → 数据目录 → (配置) → 拉取 → 启动 → (验证) → 完成
- [ ] `docker run` 包含 `-e TZ=Asia/Shanghai`
- [ ] 每个 `-v`、`-e`、`-p` 单独一行
- [ ] `wait_for_container` 超时秒数合理
- [ ] 日志只输出关键信息，不打印连接示例、使用说明等长篇内容
- [ ] CRLF 问题由 `lib/common.sh` 自动修复，无需处理

---

## 七、环境变量覆盖规则

所有可配置参数必须支持环境变量覆盖：

```
${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}  # 数据根目录
${MYSQL_PORT:-3306}                                       # 端口
${MYSQL_ROOT_PASSWORD:-123456}                            # 密码
${MYSQL_VERSION:-8.1}                                     # 镜像版本
```

这样用户可以按需定制：

```bash
MYSQL_PORT=3307 MYSQL_ROOT_PASSWORD=myPass bash component_install_mysql_docker.sh
```
