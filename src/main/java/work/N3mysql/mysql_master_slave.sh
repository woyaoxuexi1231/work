#!/usr/bin/env bash
# ========================================================================================
# MySQL Docker 主从架构安装脚本 (MySQL Master-Slave Replication)
# ========================================================================================
#
# 功能说明:
#   本脚本用于通过 Docker 快速部署 MySQL 主从复制架构。
#   主从复制实现数据实时同步，支持读写分离，提高系统可用性和性能。
#
# 架构说明:
#   - Master (主库): 接收所有写操作，记录 binlog
#   - Slave  (从库): 实时同步主库数据，提供读服务
#
# 主要特性:
#   ✓ 主从自动同步: 数据实时复制，保证一致性
#   ✓ 读写分离: 写主读从，提升性能
#   ✓ 高可用基础: 主从架构是高可用方案的基础
#   ✓ 故障切换: 从库可升级为主库
#
# 配置参数:
#   MYSQL_ROOT_PASSWORD - Root 用户密码 (默认: 123456)
#   MASTER_PORT         - 主库端口 (默认: 3306)
#   SLAVE_PORT          - 从库端口 (默认: 3307)
#   MASTER_DATA_ROOT    - 主库数据目录 (默认: /root/mysql-master)
#   SLAVE_DATA_ROOT     - 从库数据目录 (默认: /root/mysql-slave)
#   MYSQL_VERSION       - MySQL 版本 (默认: 8.1)
#
# 端口说明:
#   3306 - 主库 MySQL 默认端口
#   3307 - 从库 MySQL 端口
#
# 连接示例:
#   主库: mysql -h localhost -P 3306 -u root -p123456
#   从库: mysql -h localhost -P 3307 -u root -p123456
#
# 主从复制状态检查:
#   docker exec mysql-slave mysql -uroot -p123456 -e "SHOW SLAVE STATUS\G"
#
# 作者: 系统运维脚本 | 版本: v1.0 | 更新时间: 2024-01
# ========================================================================================

# Ensure we are running under bash
if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_ROOT="${DATA_ROOT:-/root/mysql-replication}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"
MASTER_PORT="${MASTER_PORT:-3306}"
SLAVE_PORT="${SLAVE_PORT:-3307}"
MASTER_CONTAINER_NAME="${MASTER_CONTAINER_NAME:-mysql-master}"
SLAVE_CONTAINER_NAME="${SLAVE_CONTAINER_NAME:-mysql-slave}"
MYSQL_VERSION="${MYSQL_VERSION:-8.1}"
LOG_FILE="${LOG_FILE:-${DATA_ROOT}/install_mysql_master_slave.log}"

MASTER_DATA_ROOT="${DATA_ROOT}/master"
SLAVE_DATA_ROOT="${DATA_ROOT}/slave"

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

# 确保日志目录存在
ensure_dir "$(dirname "${LOG_FILE}")"

trap 'log_error "安装过程中出现错误，退出。"' ERR

log_info "=== MySQL 主从架构安装开始 ==="
log_info "参数: MASTER_PORT=${MASTER_PORT}, SLAVE_PORT=${SLAVE_PORT}, MYSQL_VERSION=${MYSQL_VERSION}"
log_info "日志文件: ${LOG_FILE}"

# 0. 权限检查
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
  log_error "未检测到 Docker，请先安装 Docker。"
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

# 1. 创建目录结构
log_info "创建目录结构..."
ensure_dir "${DATA_ROOT}"
ensure_dir "${MASTER_DATA_ROOT}/conf"
ensure_dir "${MASTER_DATA_ROOT}/data"
ensure_dir "${MASTER_DATA_ROOT}/log"
ensure_dir "${MASTER_DATA_ROOT}/binlog"
ensure_dir "${SLAVE_DATA_ROOT}/conf"
ensure_dir "${SLAVE_DATA_ROOT}/data"
ensure_dir "${SLAVE_DATA_ROOT}/log"
ensure_dir "${SLAVE_DATA_ROOT}/relaylog"

# 2. 准备主库配置文件
MASTER_CNF_PATH="${MASTER_DATA_ROOT}/conf/my.cnf"
if [[ -f "${MASTER_CNF_PATH}" ]]; then
  log_warn "检测到已有主库配置文件 ${MASTER_CNF_PATH}，将直接复用。"
else
  log_info "写入主库配置到 ${MASTER_CNF_PATH}"
  cat > "${MASTER_CNF_PATH}" <<'EOF'
[client]
default-character-set=utf8mb4

[mysql]
default-character-set=utf8mb4

[mysqld]
# 基础配置
user=mysql
port=3306
server-id=1

# MySQL 8.x 认证插件配置（兼容旧版客户端）
default-authentication-plugin=mysql_native_password
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

# 二进制日志配置（主从复制核心）
log-bin=mysql-bin
binlog_format=row
binlog_expire_logs_seconds=604800
max_binlog_size=128M
binlog_checksum=NONE
binlog_row_image=full
binlog_cache_size=8M
sync_binlog=1

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

# 其他配置
skip-name-resolve
log_bin_trust_function_creators=true
back-log=500
explicit_defaults_for_timestamp=true
EOF
fi

# 3. 准备从库配置文件
SLAVE_CNF_PATH="${SLAVE_DATA_ROOT}/conf/my.cnf"
if [[ -f "${SLAVE_CNF_PATH}" ]]; then
  log_warn "检测到已有从库配置文件 ${SLAVE_CNF_PATH}，将直接复用。"
else
  log_info "写入从库配置到 ${SLAVE_CNF_PATH}"
  cat > "${SLAVE_CNF_PATH}" <<'EOF'
[client]
default-character-set=utf8mb4

[mysql]
default-character-set=utf8mb4

[mysqld]
# 基础配置
user=mysql
port=3307
server-id=2

# MySQL 8.x 认证插件配置（兼容旧版客户端）
default-authentication-plugin=mysql_native_password
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

# 从库复制配置
relay-log=relay-log
relay-log-index=relay-log.index
relay_log_purge=ON
relay_log_recovery=ON
read_only=ON
super_read_only=ON
log_slave_updates=ON

# 二进制日志（可选，用于级联复制）
log-bin=mysql-bin
binlog_format=row
binlog_expire_logs_seconds=604800
max_binlog_size=128M
binlog_checksum=NONE
binlog_row_image=full
binlog_cache_size=8M
sync_binlog=1

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

# 其他配置
skip-name-resolve
log_bin_trust_function_creators=true
back-log=500
explicit_defaults_for_timestamp=true
EOF
fi

log_info "设置目录与配置文件权限"
chmod -R 777 "${MASTER_DATA_ROOT}/data" "${MASTER_DATA_ROOT}/log" "${MASTER_DATA_ROOT}/binlog"
chmod -R 777 "${SLAVE_DATA_ROOT}/data" "${SLAVE_DATA_ROOT}/log" "${SLAVE_DATA_ROOT}/relaylog"
chmod 644 "${MASTER_CNF_PATH}" "${SLAVE_CNF_PATH}"

# 4. 检查并拉取镜像
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

# 5. 停止并删除已存在的容器
stop_and_remove_container() {
  local container_name="$1"
  if docker ps -a --format '{{.Names}}' | grep -qw "${container_name}"; then
    log_warn "容器 ${container_name} 已存在，停止并删除..."
    docker stop "${container_name}" >/dev/null 2>&1 || true
    docker rm "${container_name}" >/dev/null 2>&1 || true
  fi
}

stop_and_remove_container "${MASTER_CONTAINER_NAME}"
stop_and_remove_container "${SLAVE_CONTAINER_NAME}"

# 6. 启动主库
log_info "启动主库容器 ${MASTER_CONTAINER_NAME}..."
MASTER_CONTAINER_ID=$(docker run -d \
  --name "${MASTER_CONTAINER_NAME}" \
  --restart=always \
  --privileged=true \
  -p ${MASTER_PORT}:3306 \
  -e "MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}" \
  -e TZ=Asia/Shanghai \
  -v "${MASTER_CNF_PATH}":/etc/mysql/my.cnf \
  -v "${MASTER_DATA_ROOT}/data":/var/lib/mysql \
  -v "${MASTER_DATA_ROOT}/log":/var/log/mysql \
  -v "${MASTER_DATA_ROOT}/binlog":/var/log/mysql/binlog \
  "mysql:${MYSQL_VERSION}")

log_info "主库容器已创建，ID: ${MASTER_CONTAINER_ID}"
log_info "等待主库容器启动..."
sleep 10

# 7. 等待主库 MySQL 服务就绪
log_info "等待主库 MySQL 服务就绪..."
MAX_RETRIES=90
RETRY_COUNT=0
MASTER_READY=false

while [[ $RETRY_COUNT -lt $MAX_RETRIES ]]; do
  # 使用 root 用户和密码测试连接，同时检查服务状态
  if docker exec "${MASTER_CONTAINER_NAME}" mysqladmin ping -h localhost -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null; then
    # 额外验证：尝试执行一个简单查询
    if docker exec "${MASTER_CONTAINER_NAME}" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "SELECT 1" >/dev/null 2>&1; then
      MASTER_READY=true
      log_info "主库 MySQL 服务已就绪"
      break
    fi
  fi
  RETRY_COUNT=$((RETRY_COUNT + 1))
  if [[ $((RETRY_COUNT % 10)) -eq 0 ]]; then
    log_info "等待主库 MySQL 服务启动中... (${RETRY_COUNT}/${MAX_RETRIES})"
  fi
  sleep 3
done

if [[ "${MASTER_READY}" != "true" ]]; then
  log_error "主库 MySQL 服务在预期时间内未就绪，请检查日志：docker logs ${MASTER_CONTAINER_NAME}"
  exit 1
fi

# 8. 创建复制用户
log_info "创建主库复制用户..."
docker exec "${MASTER_CONTAINER_NAME}" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "
CREATE USER IF NOT EXISTS 'repl_user'@'%' IDENTIFIED WITH mysql_native_password BY 'repl_pass123';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'repl_user'@'%';
FLUSH PRIVILEGES;
" 2>&1

log_info "主库复制用户创建完成"

# 9. 获取主库状态
log_info "获取主库状态..."
MASTER_STATUS=$(docker exec "${MASTER_CONTAINER_NAME}" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "
SHOW MASTER STATUS;
" 2>/dev/null)

MASTER_LOG_FILE=$(echo "${MASTER_STATUS}" | grep -A1 "File" | tail -1)
MASTER_LOG_POS=$(echo "${MASTER_STATUS}" | grep -A1 "Position" | tail -1)

log_info "主库状态: log_file=${MASTER_LOG_FILE}, log_pos=${MASTER_LOG_POS}"

# 10. 启动从库
log_info "启动从库容器 ${SLAVE_CONTAINER_NAME}..."
SLAVE_CONTAINER_ID=$(docker run -d \
  --name "${SLAVE_CONTAINER_NAME}" \
  --restart=always \
  --privileged=true \
  -p ${SLAVE_PORT}:3307 \
  -e "MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}" \
  -e TZ=Asia/Shanghai \
  -v "${SLAVE_CNF_PATH}":/etc/mysql/my.cnf \
  -v "${SLAVE_DATA_ROOT}/data":/var/lib/mysql \
  -v "${SLAVE_DATA_ROOT}/log":/var/log/mysql \
  -v "${SLAVE_DATA_ROOT}/relaylog":/var/log/mysql/relaylog \
  "mysql:${MYSQL_VERSION}")

log_info "从库容器已创建，ID: ${SLAVE_CONTAINER_ID}"
log_info "等待从库容器启动..."
sleep 10

# 11. 等待从库 MySQL 服务就绪
log_info "等待从库 MySQL 服务就绪..."
RETRY_COUNT=0
SLAVE_READY=false
SLAVE_MAX_RETRIES=90

while [[ $RETRY_COUNT -lt $SLAVE_MAX_RETRIES ]]; do
  # 使用 root 用户和密码测试连接
  if docker exec "${SLAVE_CONTAINER_NAME}" mysqladmin ping -h localhost -P 3307 -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null; then
    # 额外验证：尝试执行一个简单查询
    if docker exec "${SLAVE_CONTAINER_NAME}" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -P 3307 -e "SELECT 1" >/dev/null 2>&1; then
      SLAVE_READY=true
      log_info "从库 MySQL 服务已就绪"
      break
    fi
  fi
  RETRY_COUNT=$((RETRY_COUNT + 1))
  if [[ $((RETRY_COUNT % 10)) -eq 0 ]]; then
    log_info "等待从库 MySQL 服务启动中... (${RETRY_COUNT}/${SLAVE_MAX_RETRIES})"
  fi
  sleep 3
done

if [[ "${SLAVE_READY}" != "true" ]]; then
  log_error "从库 MySQL 服务在预期时间内未就绪，请检查日志：docker logs ${SLAVE_CONTAINER_NAME}"
  exit 1
fi

# 额外等待确保从库完全初始化
log_info "等待从库完全初始化..."
sleep 5

# 12. 配置从库复制
log_info "配置从库复制连接主库..."
docker exec "${SLAVE_CONTAINER_NAME}" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "
CHANGE MASTER TO
  MASTER_HOST='host.docker.internal',
  MASTER_PORT=3306,
  MASTER_USER='repl_user',
  MASTER_PASSWORD='repl_pass123',
  MASTER_LOG_FILE='${MASTER_LOG_FILE}',
  MASTER_LOG_POS=${MASTER_LOG_POS},
  MASTER_CONNECT_RETRY=10,
  GET_MASTER_PUBLIC_KEY=1;
" 2>&1

# 13. 启动复制
log_info "启动从库复制..."
docker exec "${SLAVE_CONTAINER_NAME}" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "START SLAVE;" 2>&1

# 等待复制启动
sleep 5

# 14. 检查复制状态
log_info "检查从库复制状态..."
SLAVE_STATUS=$(docker exec "${SLAVE_CONTAINER_NAME}" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "SHOW SLAVE STATUS\G" 2>/dev/null)

if echo "${SLAVE_STATUS}" | grep -q "Slave_IO_Running: Yes" && echo "${SLAVE_STATUS}" | grep -q "Slave_SQL_Running: Yes"; then
  log_info "✓ 主从复制配置成功！"
  log_info "  - Slave_IO_Running: Yes"
  log_info "  - Slave_SQL_Running: Yes"
else
  IO_STATUS=$(echo "${SLAVE_STATUS}" | grep "Slave_IO_Running:" | awk '{print $2}')
  SQL_STATUS=$(echo "${SLAVE_STATUS}" | grep "Slave_SQL_Running:" | awk '{print $2}')
  LAST_ERROR=$(echo "${SLAVE_STATUS}" | grep "Last_Error:" | cut -d: -f2-)
  
  log_warn "主从复制状态检查:"
  log_warn "  - Slave_IO_Running: ${IO_STATUS}"
  log_warn "  - Slave_SQL_Running: ${SQL_STATUS}"
  if [[ -n "${LAST_ERROR}" ]]; then
    log_warn "  - Last_Error: ${LAST_ERROR}"
  fi
  log_warn "请检查网络配置和复制状态"
fi

# 15. 创建测试数据库
log_info "在主库创建测试数据库..."
docker exec "${MASTER_CONTAINER_NAME}" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "
CREATE DATABASE IF NOT EXISTS \`test_master_slave\` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE \`test_master_slave\`;
CREATE TABLE IF NOT EXISTS \`replication_test\` (
  \`id\` INT AUTO_INCREMENT PRIMARY KEY,
  \`content\` VARCHAR(255) NOT NULL,
  \`created_at\` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO \`replication_test\` (\`content\`) VALUES ('Test data from master - $(date +%Y-%m-%d\ %H:%M:%S)');
SELECT * FROM \`replication_test\` WHERE \`content\` LIKE 'Test data%';
" 2>&1

# 等待数据同步到从库
sleep 3

# 16. 验证数据同步
log_info "验证数据同步到从库..."
REPL_COUNT=$(docker exec "${SLAVE_CONTAINER_NAME}" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -N -e "
SELECT COUNT(*) FROM \`test_master_slave\`.\`replication_test\` WHERE \`content\` LIKE 'Test data%';
" 2>/dev/null)

if [[ "${REPL_COUNT}" -gt 0 ]]; then
  log_info "✓ 数据同步验证成功！从库已成功复制 ${REPL_COUNT} 条数据。"
else
  log_warn "数据同步验证未找到预期数据，可能需要更多时间同步"
fi

# 17. 输出安装结果
log_info "=============================================="
log_info "=== MySQL 主从架构安装完成 ==="
log_info "=============================================="
log_info ""
log_info "主库信息:"
log_info "  - 容器名称: ${MASTER_CONTAINER_NAME}"
log_info "  - 端口映射: ${MASTER_PORT} -> 3306"
log_info "  - 连接命令: mysql -h localhost -P ${MASTER_PORT} -u root -p${MYSQL_ROOT_PASSWORD}"
log_info "  - Binlog文件: ${MASTER_LOG_FILE}"
log_info "  - Binlog位置: ${MASTER_LOG_POS}"
log_info ""
log_info "从库信息:"
log_info "  - 容器名称: ${SLAVE_CONTAINER_NAME}"
log_info "  - 端口映射: ${SLAVE_PORT} -> 3307"
log_info "  - 连接命令: mysql -h localhost -P ${SLAVE_PORT} -u root -p${MYSQL_ROOT_PASSWORD}"
log_info ""
log_info "复制用户:"
log_info "  - 用户名: repl_user"
log_info "  - 密码: repl_pass123"
log_info ""
log_info "管理命令:"
log_info "  查看从库复制状态:"
log_info "    docker exec ${SLAVE_CONTAINER_NAME} mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e \"SHOW SLAVE STATUS\\G\""
log_info ""
log_info "  查看容器状态:"
log_info "    docker ps | grep mysql"
log_info ""
log_info "  查看容器日志:"
log_info "    docker logs ${MASTER_CONTAINER_NAME}"
log_info "    docker logs ${SLAVE_CONTAINER_NAME}"
log_info ""
log_info "  停止/启动容器:"
log_info "    docker stop ${MASTER_CONTAINER_NAME} ${SLAVE_CONTAINER_NAME}"
log_info "    docker start ${MASTER_CONTAINER_NAME} ${SLAVE_CONTAINER_NAME}"
log_info ""
log_info "  重新配置复制(如果需要):"
log_info "    docker exec ${SLAVE_CONTAINER_NAME} mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e \"STOP SLAVE; RESET SLAVE; START SLAVE;\""
log_info ""
log_info "=============================================="
