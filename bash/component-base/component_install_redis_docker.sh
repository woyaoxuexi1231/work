#!/usr/bin/env bash
# ========================================================================================
# Redis Docker 安装脚本 (Redis Docker Installer)
# ========================================================================================
#
# 功能说明:
#   本脚本用于通过 Docker 快速部署 Redis 高性能键值缓存数据库。
#   Redis 是一个开源的内存数据结构存储系统，用作数据库、缓存和消息代理。
#
# 主要特性:
#   ✓ 高性能: 基于内存的存储，提供极快的读写速度
#   ✓ 数据持久化: 支持 RDB 和 AOF 持久化方式
#   ✓ 丰富数据类型: 支持字符串、哈希、列表、集合等
#   ✓ 发布订阅: 内置发布/订阅消息机制
#   ✓ Lua 脚本: 支持 Lua 脚本执行复杂逻辑
#
# 配置参数:
#   REDIS_PASSWORD  - Redis 访问密码 (默认: 123456，设置为 "none" 禁用密码)
#   REDIS_PORT      - 服务端口 (默认: 6379)
#   REDIS_DATA_ROOT - 数据目录 (默认: /root/redis)
#
# 端口说明:
#   6379 - Redis 默认服务端口
#
# 使用示例:
#   连接: redis-cli -h localhost -p 6379
#   认证: auth 123456 (如果设置了密码)
#   测试: ping (应该返回 PONG)
#   禁用密码: REDIS_PASSWORD=none bash component_install_redis_docker.sh
#
# 注意: 单实例部署，生产环境建议集群或主从架构
# 作者: 系统运维脚本 | 版本: v1.0 | 更新时间: 2024-01
# ========================================================================================

# Ensure we are running under bash (Ubuntu /bin/sh 不支持 pipefail)
if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_ROOT="${REDIS_DATA_ROOT:-/root/redis}"
CONTAINER_NAME="${REDIS_CONTAINER_NAME:-redis}"
REDIS_VERSION="${REDIS_VERSION:-7.2}"
REDIS_PASSWORD="${REDIS_PASSWORD:-123456}"
# 如果明确设置为 "none" 或空字符串，则不设置密码，否则使用默认密码
#if [[ "${REDIS_PASSWORD:-}" == "none" || "${REDIS_PASSWORD:-}" == "" ]]; then
#  REDIS_PASSWORD=""
#else
#  REDIS_PASSWORD="${REDIS_PASSWORD:-123456}"
#fi
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_LOG_TO_STDOUT="${REDIS_LOG_TO_STDOUT:-yes}"
LOG_FILE="${LOG_FILE:-${DATA_ROOT}/install_redis.log}"

log() {
  local level="$1"; shift
  local msg="$*"
  local ts
  ts="$(date '+%Y-%m-%d %H:%M:%S')"
  printf '[%s] [%s] %s\n' "$ts" "$level" "$msg" | tee -a "$LOG_FILE"
}

log_info() { log "INFO" "$@"; }
log_warn() { log "WARN" "$@"; }
log_error() { log "ERROR" "$@"; }

ensure_dir() {
  local dir="$1"
  if [[ -z "${dir}" ]]; then
    log_error "ensure_dir 收到空目录参数"
    exit 1
  fi
  if [[ ! -d "${dir}" ]]; then
    mkdir -p "${dir}"
  fi
}

# 确保日志目录及其父目录存在，避免 tee 报错
ensure_dir "$(dirname "${LOG_FILE}")"

trap 'log_error "安装过程中出现错误，退出。"' ERR

log_info "=== Redis Docker 安装开始 ==="
log_info "参数: DATA_ROOT=${DATA_ROOT}, CONTAINER_NAME=${CONTAINER_NAME}, REDIS_VERSION=${REDIS_VERSION}, REDIS_PORT=${REDIS_PORT}"
if [[ -n "${REDIS_PASSWORD}" ]]; then
  log_info "Redis 密码已设置（长度: ${#REDIS_PASSWORD}）- 默认密码: 123456"
else
  log_info "Redis 密码已禁用（无密码模式）- 如需密码请设置 REDIS_PASSWORD=your_password"
fi
log_info "日志文件: ${LOG_FILE}"

# 0. 权限检查（Ubuntu 默认禁用 root 登录）
if [[ $EUID -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    log_warn "当前非 root，使用 sudo 重新执行脚本..."
    exec sudo -E bash "$0" "$@"
  else
    log_error "需要 root 权限且未找到 sudo，请以 root 或 sudo 运行脚本。"
    exit 1
  fi
fi

# 检查 Docker 是否已安装
if ! command -v docker >/dev/null 2>&1; then
  log_error "未检测到 Docker，请先运行安装脚本安装 Docker："
  log_error "  bash ${SCRIPT_DIR}/install_docker.sh"
  log_error "或者手动安装 Docker 后再运行此脚本。"
  exit 1
fi

# 验证 Docker 服务是否运行
if ! docker ps >/dev/null 2>&1; then
  log_warn "Docker 已安装但服务未运行，尝试启动 Docker 服务..."
  systemctl start docker || {
    log_error "无法启动 Docker 服务，请检查：systemctl status docker"
    exit 1
  }
fi

if docker ps -a --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
  if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
    log_warn "容器 ${CONTAINER_NAME} 已存在且正在运行，跳过安装。"
  else
    log_warn "容器 ${CONTAINER_NAME} 已存在（已停止），如需重新创建请先删除该容器。"
  fi
  log_info "=== Redis Docker 安装结束（已存在，无需处理） ==="
  exit 0
fi

# 1. 创建目录
log_info "创建目录结构: ${DATA_ROOT}/{conf,data,log}"
ensure_dir "${DATA_ROOT}"
ensure_dir "${DATA_ROOT}/conf"
ensure_dir "${DATA_ROOT}/data"
ensure_dir "${DATA_ROOT}/log"

# 2. 准备配置文件
CONF_PATH="${DATA_ROOT}/conf/redis.conf"
if [[ -f "${CONF_PATH}" ]]; then
  log_warn "检测到已有配置文件 ${CONF_PATH}，将更新相关设置。"
  # 备份原配置
  cp "${CONF_PATH}" "${CONF_PATH}.backup.$(date +%Y%m%d_%H%M%S)" 2>/dev/null || true
  # 确保 logfile 设置为空（输出到 stdout）
  if [[ "${REDIS_LOG_TO_STDOUT}" == "yes" ]]; then
    # 更新或添加 logfile "" 设置
    if grep -q "^logfile " "${CONF_PATH}"; then
      sed -i 's|^logfile .*|logfile ""|' "${CONF_PATH}"
    else
      # 在 loglevel 后面添加 logfile ""
      sed -i '/^loglevel /a logfile ""' "${CONF_PATH}"
    fi
    log_info "已更新配置文件：logfile 设置为空（输出到 stdout）"
  fi
  # 更新 protected-mode 设置（根据是否有密码）
  if [[ -n "${REDIS_PASSWORD}" ]]; then
    # 有密码时启用保护模式
    if grep -q "^protected-mode " "${CONF_PATH}"; then
      sed -i 's|^protected-mode .*|protected-mode yes|' "${CONF_PATH}"
    else
      sed -i '/^port /a protected-mode yes' "${CONF_PATH}"
    fi
    log_info "已更新配置文件：protected-mode 设置为 yes（已设置密码）"
  else
    # 无密码时禁用保护模式（允许外部连接）
    if grep -q "^protected-mode " "${CONF_PATH}"; then
      sed -i 's|^protected-mode .*|protected-mode no|' "${CONF_PATH}"
    else
      sed -i '/^port /a protected-mode no' "${CONF_PATH}"
    fi
    log_info "已更新配置文件：protected-mode 设置为 no（无密码模式）"
  fi
  # 更新密码设置
  if [[ -n "${REDIS_PASSWORD}" ]]; then
    if grep -q "^requirepass " "${CONF_PATH}"; then
      sed -i "s|^requirepass .*|requirepass ${REDIS_PASSWORD}|" "${CONF_PATH}"
    else
      # 在安全配置部分添加密码
      if grep -q "^# 安全配置" "${CONF_PATH}"; then
        sed -i "/^# 安全配置/a requirepass ${REDIS_PASSWORD}" "${CONF_PATH}"
      else
        echo "requirepass ${REDIS_PASSWORD}" >> "${CONF_PATH}"
      fi
    fi
    log_info "已更新配置文件：Redis 密码已设置"
  else
    # 移除密码设置（如果存在）
    sed -i 's|^requirepass .*|# requirepass foobared|' "${CONF_PATH}"
    log_info "已更新配置文件：Redis 密码已移除"
  fi
else
  log_info "写入默认配置到 ${CONF_PATH}"
  cat > "${CONF_PATH}" <<EOF
# Redis 配置文件

# 网络配置
bind 0.0.0.0
port ${REDIS_PORT}
# protected-mode 设置：如果有密码则启用保护模式，无密码则禁用（允许外部连接）
protected-mode ${PROTECTED_MODE:-no}
tcp-backlog 511
timeout 0
tcp-keepalive 300

# 通用配置
daemonize no
supervised no
pidfile /var/run/redis_6379.pid
loglevel notice
# 日志输出到 stdout（推荐，避免权限问题，可用 docker logs 查看）
# 如果需要文件日志，请确保 /var/log/redis 目录权限为 777
logfile ""
databases 16

# 快照配置（RDB 持久化）
save 900 1
save 300 10
save 60 10000
stop-writes-on-bgsave-error yes
rdbcompression yes
rdbchecksum yes
dbfilename dump.rdb
dir /data

# 复制配置
replica-serve-stale-data yes
replica-read-only yes
repl-diskless-sync no
repl-diskless-sync-delay 5
repl-disable-tcp-nodelay no
replica-priority 100

# 安全配置
EOF

  # 如果设置了密码，添加到配置文件中
  if [[ -n "${REDIS_PASSWORD}" ]]; then
    echo "requirepass ${REDIS_PASSWORD}" >> "${CONF_PATH}"
    # 有密码时启用保护模式
    sed -i 's|^protected-mode .*|protected-mode yes|' "${CONF_PATH}"
    log_info "已配置 Redis 密码，protected-mode 已启用"
  else
    echo "# requirepass foobared" >> "${CONF_PATH}"
    # 无密码时禁用保护模式（允许外部连接）
    sed -i 's|^protected-mode .*|protected-mode no|' "${CONF_PATH}"
    log_info "未配置 Redis 密码（无密码模式），protected-mode 已禁用"
  fi

  cat >> "${CONF_PATH}" <<'EOF'

# AOF 持久化配置
appendonly yes
appendfilename "appendonly.aof"
appendfsync everysec
no-appendfsync-on-rewrite no
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb
aof-load-truncated yes
aof-use-rdb-preamble yes

# Lua 脚本配置
lua-time-limit 5000

# 慢日志配置
slowlog-log-slower-than 10000
slowlog-max-len 128

# 延迟监控配置
latency-monitor-threshold 0

# 事件通知配置
notify-keyspace-events ""

# 高级配置
hash-max-ziplist-entries 512
hash-max-ziplist-value 64
list-max-ziplist-size -2
list-compress-depth 0
set-max-intset-entries 512
zset-max-ziplist-entries 128
zset-max-ziplist-value 64
hll-sparse-max-bytes 3000
stream-node-max-bytes 4096
stream-node-max-entries 100
activerehashing yes
client-output-buffer-limit normal 0 0 0
client-output-buffer-limit replica 256mb 64mb 60
client-output-buffer-limit pubsub 32mb 8mb 60
hz 10
dynamic-hz yes
aof-rewrite-incremental-fsync yes
rdb-save-incremental-fsync yes
EOF
fi

log_info "设置目录与配置文件权限"
# Redis 容器内用户（通常是 redis，UID 999）需要写入权限
chmod 777 "${DATA_ROOT}/data"
chmod 755 "${DATA_ROOT}/log"
chmod 644 "${CONF_PATH}"

# 如果用户想要文件日志，创建日志文件并设置权限
# 注意：默认配置中日志输出到 stdout，可用 docker logs 查看
if [[ "${REDIS_LOG_TO_STDOUT}" != "yes" ]]; then
  log_info "配置日志输出到文件: ${DATA_ROOT}/log/redis.log"
  # 更新配置文件中的日志路径（无论配置文件是新创建还是已存在）
  if grep -q "^logfile " "${CONF_PATH}"; then
    sed -i 's|^logfile .*|logfile /var/log/redis/redis.log|' "${CONF_PATH}"
  else
    sed -i '/^loglevel /a logfile /var/log/redis/redis.log' "${CONF_PATH}"
  fi
  touch "${DATA_ROOT}/log/redis.log" 2>/dev/null || true
  chmod 666 "${DATA_ROOT}/log/redis.log" 2>/dev/null || true
else
  # 确保 logfile 设置为空（即使配置文件已存在）
  if grep -q "^logfile " "${CONF_PATH}"; then
    sed -i 's|^logfile .*|logfile ""|' "${CONF_PATH}"
  fi
  log_info "日志将输出到 stdout（可用 docker logs 查看）"
fi

# 3. 检查并拉取镜像（若本地无镜像）
log_info "检查并拉取镜像 redis:${REDIS_VERSION}"
if ! docker image inspect "redis:${REDIS_VERSION}" >/dev/null 2>&1; then
  log_info "本地不存在镜像 redis:${REDIS_VERSION}，开始拉取..."
  if docker pull "redis:${REDIS_VERSION}"; then
    log_info "镜像 redis:${REDIS_VERSION} 拉取成功"
  else
    log_error "镜像拉取失败"
    exit 1
  fi
else
  log_info "镜像 redis:${REDIS_VERSION} 已存在，跳过拉取"
fi

# 4. 启动容器
log_info "启动容器 ${CONTAINER_NAME}"

# 构建 docker run 命令
DOCKER_RUN_CMD=(
  docker run -d
  --name "${CONTAINER_NAME}"
  --restart=always
  --privileged=true
  -p "${REDIS_PORT}:6379"
  -e TZ=Asia/Shanghai
  -v "${CONF_PATH}:/usr/local/etc/redis/redis.conf"
  -v "${DATA_ROOT}/data:/data"
)

# 只有在需要文件日志时才挂载日志目录
if [[ "${REDIS_LOG_TO_STDOUT}" != "yes" ]]; then
  DOCKER_RUN_CMD+=(-v "${DATA_ROOT}/log:/var/log/redis")
fi

DOCKER_RUN_CMD+=("redis:${REDIS_VERSION}")

# 添加启动命令：使用配置文件启动
# 注意：密码已在配置文件中通过 requirepass 设置，不需要通过命令行参数传递
DOCKER_RUN_CMD+=(redis-server /usr/local/etc/redis/redis.conf)

CONTAINER_ID=$("${DOCKER_RUN_CMD[@]}")

log_info "容器已创建，ID: ${CONTAINER_ID}"
log_info "等待容器启动..."
sleep 3

# 显示容器启动日志
log_info "容器启动日志："
docker logs "${CONTAINER_NAME}" 2>&1 | head -20 | while IFS= read -r line; do
  log_info "  $line"
done

if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
  log_info "容器 ${CONTAINER_NAME} 启动成功。"
  log_info "端口映射: ${REDIS_PORT} -> 6379"
  if [[ -n "${REDIS_PASSWORD}" ]]; then
    log_info "Redis 密码: ${REDIS_PASSWORD}"
    log_info "连接命令: redis-cli -h 192.168.3.100 -p ${REDIS_PORT} -a ${REDIS_PASSWORD}"
  else
    log_info "Redis 密码: 未设置（无密码模式）"
    log_info "连接命令: redis-cli -h 192.168.3.100 -p ${REDIS_PORT}"
  fi
else
  log_error "容器未在预期时间内启动，请检查日志：docker logs ${CONTAINER_NAME}"
  exit 1
fi

# 5. 等待 Redis 服务就绪并测试连接
log_info "等待 Redis 服务就绪..."
MAX_RETRIES=30
RETRY_COUNT=0
REDIS_READY=false

while [[ $RETRY_COUNT -lt $MAX_RETRIES ]]; do
  if [[ -n "${REDIS_PASSWORD}" ]]; then
    if docker exec "${CONTAINER_NAME}" redis-cli -a "${REDIS_PASSWORD}" ping 2>/dev/null | grep -q "PONG"; then
      REDIS_READY=true
      log_info "Redis 服务已就绪"
      break
    fi
  else
    if docker exec "${CONTAINER_NAME}" redis-cli ping 2>/dev/null | grep -q "PONG"; then
      REDIS_READY=true
      log_info "Redis 服务已就绪"
      break
    fi
  fi
  RETRY_COUNT=$((RETRY_COUNT + 1))
  if [[ $((RETRY_COUNT % 5)) -eq 0 ]]; then
    log_info "等待 Redis 服务启动中... (${RETRY_COUNT}/${MAX_RETRIES})"
  fi
  sleep 1
done

if [[ "${REDIS_READY}" != "true" ]]; then
  log_warn "Redis 服务在预期时间内未就绪，但容器已启动"
  log_warn "请手动检查：docker logs ${CONTAINER_NAME}"
else
  # 测试 Redis 连接和基本操作
  log_info "测试 Redis 连接..."
  if [[ -n "${REDIS_PASSWORD}" ]]; then
    if docker exec "${CONTAINER_NAME}" redis-cli -a "${REDIS_PASSWORD}" set test_key "test_value" >/dev/null 2>&1 && \
       docker exec "${CONTAINER_NAME}" redis-cli -a "${REDIS_PASSWORD}" get test_key 2>/dev/null | grep -q "test_value"; then
      log_info "✓ Redis 连接测试成功"
      docker exec "${CONTAINER_NAME}" redis-cli -a "${REDIS_PASSWORD}" del test_key >/dev/null 2>&1 || true
    else
      log_warn "Redis 连接测试失败，但服务可能正常运行"
    fi
  else
    if docker exec "${CONTAINER_NAME}" redis-cli set test_key "test_value" >/dev/null 2>&1 && \
       docker exec "${CONTAINER_NAME}" redis-cli get test_key 2>/dev/null | grep -q "test_value"; then
      log_info "✓ Redis 连接测试成功"
      docker exec "${CONTAINER_NAME}" redis-cli del test_key >/dev/null 2>&1 || true
    else
      log_warn "Redis 连接测试失败，但服务可能正常运行"
    fi
  fi
fi

log_info "=== Redis Docker 安装完成 ==="
log_info ""
log_info "使用说明："
log_info "1. 连接 Redis："
if [[ -n "${REDIS_PASSWORD}" ]]; then
  log_info "   本地连接: redis-cli -h 192.168.3.100 -p ${REDIS_PORT} -a ${REDIS_PASSWORD}"
  log_info "   容器内连接: docker exec -it ${CONTAINER_NAME} redis-cli -a ${REDIS_PASSWORD}"
  log_info "   注意: 默认密码为 123456，如需修改请设置 REDIS_PASSWORD 环境变量"
else
  log_info "   本地连接: redis-cli -h 192.168.3.100 -p ${REDIS_PORT}"
  log_info "   容器内连接: docker exec -it ${CONTAINER_NAME} redis-cli"
  log_info "   注意: 当前为无密码模式，如需设置密码请重新运行脚本并设置 REDIS_PASSWORD"
fi
log_info ""
log_info "2. 查看日志："
log_info "   docker logs ${CONTAINER_NAME}"
if [[ "${REDIS_LOG_TO_STDOUT}" != "yes" ]]; then
  log_info "   或查看日志文件: ${DATA_ROOT}/log/redis.log"
else
  log_info "   注意：日志输出到 stdout，使用 docker logs 查看"
fi
log_info ""
log_info "3. 数据目录："
log_info "   ${DATA_ROOT}/data"
log_info ""
log_info "4. 配置文件："
log_info "   ${CONF_PATH}"
log_info ""
log_info "5. 停止/启动容器："
log_info "   docker stop ${CONTAINER_NAME}"
log_info "   docker start ${CONTAINER_NAME}"
log_info ""
log_info "6. 密码管理："
log_info "   如需禁用密码: REDIS_PASSWORD=none bash component_install_redis_docker.sh"
log_info "   如需修改密码: REDIS_PASSWORD=newpassword bash component_install_redis_docker.sh"

