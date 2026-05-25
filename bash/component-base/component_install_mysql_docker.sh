#!/usr/bin/env bash
# ========================================================================================
# MySQL Docker 安装脚本 (MySQL Docker Installer)
# ========================================================================================
#
# 功能说明:
#   本脚本用于通过 Docker 快速部署 MySQL 关系型数据库管理系统。
#   MySQL 是世界上最流行的开源关系型数据库，被广泛应用于各种应用场景。
#
# 主要特性:
#   ✓ ACID 事务: 保证数据一致性和完整性
#   ✓ 高性能: 优化的查询引擎和存储引擎
#   ✓ 丰富功能: 存储过程、触发器、视图等
#   ✓ 权限管理: 细粒度的用户权限控制
#   ✓ 复制支持: 支持主从复制和高可用架构
#
# 配置参数:
#   MYSQL_ROOT_PASSWORD - Root 用户密码 (默认: 123456)
#   MYSQL_PORT          - 服务端口 (默认: 3306)
#   MYSQL_DATA_ROOT     - 数据目录 (默认: /root/mysql)
#
# 端口说明:
#   3306 - MySQL 默认服务端口
#
# 自动创建:
#   - test 数据库用于测试
#   - 完整的配置文件优化性能
#
# 连接示例:
#   mysql -h localhost -P 3306 -u root -p123456
#   或使用任意 MySQL 客户端连接
#
# 注意: 单实例部署，生产环境建议主从或集群架构
# 作者: 系统运维脚本 | 版本: v1.0 | 更新时间: 2024-01
# ========================================================================================

# Ensure we are running under bash (Ubuntu /bin/sh 不支持 pipefail)
if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_ROOT="${MYSQL_DATA_ROOT:-/root/mysql}"
CONTAINER_NAME="${MYSQL_CONTAINER_NAME:-mysql}"
MYSQL_VERSION="${MYSQL_VERSION:-8.1}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"
LOG_FILE="${LOG_FILE:-${DATA_ROOT}/install_mysql.log}"

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

log_info "=== MySQL Docker 安装开始 ==="
log_info "参数: DATA_ROOT=${DATA_ROOT}, CONTAINER_NAME=${CONTAINER_NAME}, MYSQL_VERSION=${MYSQL_VERSION}"
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
  log_info "=== MySQL Docker 安装结束（已存在，无需处理） ==="
  exit 0
fi

# 1. 创建目录
log_info "创建目录结构: ${DATA_ROOT}/{conf,data,log,binlog,mysql-files}"
ensure_dir "${DATA_ROOT}"
ensure_dir "${DATA_ROOT}/conf"
ensure_dir "${DATA_ROOT}/data"
ensure_dir "${DATA_ROOT}/log"
ensure_dir "${DATA_ROOT}/binlog"
ensure_dir "${DATA_ROOT}/mysql-files"

# 2. 准备配置文件
CNF_PATH="${DATA_ROOT}/conf/my.cnf"
if [[ -f "${CNF_PATH}" ]]; then
  log_warn "检测到已有配置文件 ${CNF_PATH}，将直接复用。"
else
  log_info "写入默认配置到 ${CNF_PATH}"
  cat > "${CNF_PATH}" <<'EOF'
[client]
default-character-set=utf8mb4

[mysql]
default-character-set=utf8mb4

[mysqld]
# 基础配置
user=mysql
port=3306
server-id=1
character-set-server=utf8mb4
collation_server=utf8mb4_bin
max_connections=4000

# 连接超时配置
interactive_timeout=28800
wait_timeout=28800
max_connect_errors=1000

# 日志配置
log-output=FILE
slow-query-log=ON
slow-query-log-file=/var/log/mysql/slow.log
long_query_time=1
log-queries-not-using-indexes=OFF
log_timestamps=System

# 二进制日志配置（用于主从复制）
binlog_format=row
binlog_expire_logs_seconds=604800
max_binlog_size=128M
binlog_checksum=NONE
binlog_row_image=full
binlog_cache_size=8M

# InnoDB 配置
default-storage-engine=InnoDB
innodb_strict_mode=true
innodb_autoinc_lock_mode=2
innodb_file_per_table=true
innodb_flush_method=O_DIRECT
innodb_flush_log_at_trx_commit=1
innodb_log_file_size=1G
innodb_log_files_in_group=3
innodb-log-buffer-size=8M
innodb_purge_threads=4
innodb_rollback_on_timeout=true
innodb_fast_shutdown=0
innodb-autoextend-increment=100

# 表缓存配置
table_definition_cache=16384
table_open_cache=16384
open-files-limit=65535
innodb_open_files=65535

# 缓冲区配置
join_buffer_size=134217728
read_buffer_size=8388608
read_rnd_buffer_size=8388608
sort_buffer_size=2097152
group_concat_max_len=102400

# 事务配置
autocommit=1
transaction_isolation=REPEATABLE-READ
innodb_lock_wait_timeout=50
innodb_print_all_deadlocks=OFF

# 其他配置
skip-name-resolve
log_bin_trust_function_creators=true
back-log=500
innodb-status-file=TRUE
default-tmp-storage-engine=MEMORY
explicit_defaults_for_timestamp=true
sync_binlog=1
sync_relay_log=1
create_admin_listener_thread=ON
EOF
fi

log_info "设置目录与配置文件权限"
chmod 777 "${DATA_ROOT}/data" "${DATA_ROOT}/log"
chmod 644 "${CNF_PATH}"

# 3. 检查并拉取镜像（若本地无镜像）
log_info "检查并拉取镜像 mysql:${MYSQL_VERSION}"
if ! docker image inspect "mysql:${MYSQL_VERSION}" >/dev/null 2>&1; then
  log_info "本地不存在镜像 mysql:${MYSQL_VERSION}，开始拉取..."
  if docker pull "mysql:${MYSQL_VERSION}"; then
    log_info "镜像 mysql:${MYSQL_VERSION} 拉取成功"
  else
    log_error "镜像拉取失败"
    exit 1
  fi
else
  log_info "镜像 mysql:${MYSQL_VERSION} 已存在，跳过拉取"
fi

# 4. 启动容器
log_info "启动容器 ${CONTAINER_NAME}"
CONTAINER_ID=$(docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart=always \
  --privileged=true \
  -p 3306:3306 \
  -e "MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}" \
  -e TZ=Asia/Shanghai \
  -v "${CNF_PATH}":/etc/mysql/my.cnf \
  -v "${DATA_ROOT}/data":/var/lib/mysql \
  -v "${DATA_ROOT}/log":/var/log/mysql \
  -v "${DATA_ROOT}/mysql-files":/var/lib/mysql-files \
  "mysql:${MYSQL_VERSION}")

log_info "容器已创建，ID: ${CONTAINER_ID}"
log_info "等待容器启动..."
sleep 5

# 显示容器启动日志
log_info "容器启动日志："
docker logs "${CONTAINER_NAME}" 2>&1 | head -20 | while IFS= read -r line; do
  log_info "  $line"
done

if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
  log_info "容器 ${CONTAINER_NAME} 启动成功。"
  log_info "root 密码: ${MYSQL_ROOT_PASSWORD}，端口映射: 3306 -> 3306"
else
  log_error "容器未在预期时间内启动，请检查日志：docker logs ${CONTAINER_NAME}"
  exit 1
fi

# 5. 等待 MySQL 服务就绪并创建 test 数据库
log_info "等待 MySQL 服务就绪..."
MAX_RETRIES=30
RETRY_COUNT=0
MYSQL_READY=false

while [[ $RETRY_COUNT -lt $MAX_RETRIES ]]; do
  if docker exec "${CONTAINER_NAME}" mysqladmin ping -h localhost --silent 2>/dev/null; then
    MYSQL_READY=true
    log_info "MySQL 服务已就绪"
    break
  fi
  RETRY_COUNT=$((RETRY_COUNT + 1))
  if [[ $((RETRY_COUNT % 5)) -eq 0 ]]; then
    log_info "等待 MySQL 服务启动中... (${RETRY_COUNT}/${MAX_RETRIES})"
  fi
  sleep 2
done

if [[ "${MYSQL_READY}" != "true" ]]; then
  log_warn "MySQL 服务在预期时间内未就绪，但将继续尝试创建数据库"
fi

# 创建 test 数据库
log_info "创建 test 数据库..."
if docker exec "${CONTAINER_NAME}" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "CREATE DATABASE IF NOT EXISTS \`test\` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;" 2>&1; then
  log_info "✓ test 数据库创建成功"
  
  # 验证数据库是否创建成功
  if docker exec "${CONTAINER_NAME}" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "SHOW DATABASES LIKE 'test';" 2>/dev/null | grep -q "test"; then
    log_info "✓ test 数据库验证成功"
  else
    log_warn "test 数据库可能未创建成功，请手动检查"
  fi
else
  log_error "test 数据库创建失败，请检查 MySQL 日志：docker logs ${CONTAINER_NAME}"
  log_warn "可以稍后手动创建数据库："
  log_warn "  docker exec -it ${CONTAINER_NAME} mysql -uroot -p${MYSQL_ROOT_PASSWORD}"
  log_warn "  CREATE DATABASE IF NOT EXISTS \`test\` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;"
fi

log_info "=== MySQL Docker 安装完成 ==="

