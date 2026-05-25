#!/usr/bin/env bash
# ========================================================================================
# MongoDB Docker 安装脚本 (MongoDB Docker Installer)
# ========================================================================================
#
# 功能说明:
#   本脚本用于通过 Docker 快速部署 MongoDB NoSQL 文档数据库。
#   MongoDB 是一个基于文档的分布式数据库，具有高性能、高可用和易扩展的特点。
#
# 主要特性:
#   ✓ 文档模型: 基于 JSON/BSON 文档存储
#   ✓ 高性能: 内存映射存储引擎
#   ✓ 自动分片: 支持水平扩展和数据分片
#   ✓ 复制集: 支持高可用性和故障转移
#   ✓ 聚合管道: 强大的数据聚合和分析功能
#   ✓ 地理空间: 内置地理空间索引和查询
#
# 配置参数:
#   MONGO_ROOT_USER       - Root 用户名 (默认: admin)
#   MONGO_ROOT_PASSWORD   - Root 密码 (默认: admin123)
#   MONGO_PORT            - 服务端口 (默认: 27017)
#   MONGO_DATABASE        - 默认数据库 (默认: admin)
#
# 端口说明:
#   27017 - MongoDB 默认服务端口
#
# 连接示例:
#   mongosh --host localhost --port 27017 --username admin --password admin123 --authenticationDatabase admin
#   或使用 MongoDB Compass GUI 工具连接
#
# 特点:
#   - 无模式设计，支持灵活的数据结构
#   - 内置 GridFS 支持大文件存储
#   - 支持全文搜索和地理空间查询
#
# 注意: 单实例部署，生产环境建议副本集架构
# 作者: 系统运维脚本 | 版本: v1.0 | 更新时间: 2024-01
# ========================================================================================

# Ensure we are running under bash (Ubuntu /bin/sh 不支持 pipefail)
if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_ROOT="${MONGO_DATA_ROOT:-/root/mongo}"
CONTAINER_NAME="${MONGO_CONTAINER_NAME:-mongo}"
MONGO_VERSION="${MONGO_VERSION:-8.0}"
MONGO_ROOT_USER="${MONGO_ROOT_USER:-admin}"
MONGO_ROOT_PASSWORD="${MONGO_ROOT_PASSWORD:-admin123}"
MONGO_PORT="${MONGO_PORT:-27017}"
MONGO_DATABASE="${MONGO_DATABASE:-admin}"
LOG_FILE="${LOG_FILE:-${DATA_ROOT}/install_mongo.log}"

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

log_info "=== MongoDB Docker 安装开始 ==="
log_info "参数: DATA_ROOT=${DATA_ROOT}, CONTAINER_NAME=${CONTAINER_NAME}, MONGO_VERSION=${MONGO_VERSION}"
log_info "端口: ${MONGO_PORT}, 数据库: ${MONGO_DATABASE}"
log_info "用户名: ${MONGO_ROOT_USER}, 密码: ${MONGO_ROOT_PASSWORD}"
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
  log_info "=== MongoDB Docker 安装结束（已存在，无需处理） ==="
  exit 0
fi

# 1. 创建目录
log_info "创建目录结构: ${DATA_ROOT}/{conf,data,log,init}"
ensure_dir "${DATA_ROOT}"
ensure_dir "${DATA_ROOT}/conf"
ensure_dir "${DATA_ROOT}/data"
ensure_dir "${DATA_ROOT}/log"
ensure_dir "${DATA_ROOT}/init"

# 2. 准备配置文件
CONF_PATH="${DATA_ROOT}/conf/mongod.conf"

# 2.1 创建 mongod.conf 配置文件
if [[ -f "${CONF_PATH}" ]]; then
  log_warn "检测到已有配置文件 ${CONF_PATH}，将更新以修复兼容性问题。"
  # 备份原配置
  cp "${CONF_PATH}" "${CONF_PATH}.backup.$(date +%Y%m%d_%H%M%S)" 2>/dev/null || true
  # 移除可能有问题的 journal 配置
  sed -i '/^[[:space:]]*journal:/,/^[[:space:]]*enabled:/d' "${CONF_PATH}" 2>/dev/null || true
  sed -i '/^[[:space:]]*journal:/d' "${CONF_PATH}" 2>/dev/null || true
  sed -i '/storage\.journal\.enabled/d' "${CONF_PATH}" 2>/dev/null || true
  
  # 修复日志配置，移除 destination 配置（MongoDB 默认输出到 stdout）
  # MongoDB 8.0 不支持 console，不配置 destination 时默认输出到 stdout
  if grep -q "destination:" "${CONF_PATH}" 2>/dev/null; then
    # 移除 destination 行
    sed -i '/destination:/d' "${CONF_PATH}" 2>/dev/null || true
    # 移除日志文件路径配置
    sed -i '/path:.*mongod\.log/d' "${CONF_PATH}" 2>/dev/null || true
    log_info "已更新配置文件，移除了 destination 配置（使用默认 stdout 输出）"
  fi
  
  # 移除 pidFilePath 配置（Docker 容器中不需要，避免权限问题）
  if grep -q "pidFilePath:" "${CONF_PATH}" 2>/dev/null; then
    sed -i '/pidFilePath:/d' "${CONF_PATH}" 2>/dev/null || true
    log_info "已更新配置文件，移除了 pidFilePath 配置"
  fi
  
  log_info "已更新配置文件，移除了不兼容的配置"
else
  log_info "写入默认配置到 ${CONF_PATH}"
  cat > "${CONF_PATH}" <<EOF
# MongoDB 配置文件

# 存储配置
storage:
  dbPath: /data/db
  # journal 在 MongoDB 8.0+ 中默认启用，无需显式配置
  # 如果需要禁用，使用: journal.enabled: false

# 系统日志配置
# 不配置 destination，MongoDB 默认输出到 stdout（可用 docker logs 查看）
# 这样可以避免文件权限问题
systemLog:
  logAppend: true
# 如果需要文件日志，取消下面的注释并确保目录权限正确
# systemLog:
#   destination: file
#   logAppend: true
#   path: /var/log/mongodb/mongod.log

# 网络配置
net:
  port: 27017
  bindIp: 0.0.0.0
  maxIncomingConnections: 100

# 进程管理
processManagement:
  fork: false
  # pidFilePath 在 Docker 容器中通常不需要，移除以避免权限问题
  # pidFilePath: /var/run/mongodb/mongod.pid

# 安全配置（通过环境变量设置）
# security:
#   authorization: enabled

# 操作分析配置
operationProfiling:
  slowOpThresholdMs: 100
  mode: slowOp

# 复制集配置（单节点模式，不启用）
# replication:
#   replSetName: rs0
EOF
fi

# 2.2 创建初始化脚本（可选，用于创建额外的数据库和用户）
INIT_SCRIPT="${DATA_ROOT}/init/init-mongo.js"
if [[ -f "${INIT_SCRIPT}" ]]; then
  log_warn "检测到已有初始化脚本 ${INIT_SCRIPT}，将直接复用。"
else
  log_info "创建初始化脚本 ${INIT_SCRIPT}"
  cat > "${INIT_SCRIPT}" <<'EOF'
// MongoDB 初始化脚本
// 此脚本在容器首次启动时执行（如果挂载了 /docker-entrypoint-initdb.d/）

// 切换到 admin 数据库
db = db.getSiblingDB('admin');

// 创建额外的用户（可选）
// db.createUser({
//   user: 'appuser',
//   pwd: 'apppassword',
//   roles: [
//     { role: 'readWrite', db: 'myapp' }
//   ]
// });

// 创建应用数据库（可选）
// db = db.getSiblingDB('myapp');
// db.createCollection('users');

print('MongoDB 初始化脚本执行完成');
EOF
fi

log_info "设置目录与配置文件权限"
chmod 755 "${DATA_ROOT}/data"
chmod 755 "${DATA_ROOT}/log"
chmod 644 "${CONF_PATH}" "${INIT_SCRIPT}"

# 注意：日志输出到 stdout，不需要创建日志文件
# 如果需要文件日志，可以创建日志文件并设置权限
# touch "${DATA_ROOT}/log/mongod.log" 2>/dev/null || true
# chmod 666 "${DATA_ROOT}/log/mongod.log" 2>/dev/null || true

# 3. 检查并拉取镜像（若本地无镜像）
log_info "检查并拉取镜像 mongo:${MONGO_VERSION}"
if ! docker image inspect "mongo:${MONGO_VERSION}" >/dev/null 2>&1; then
  log_info "本地不存在镜像 mongo:${MONGO_VERSION}，开始拉取..."
  if docker pull "mongo:${MONGO_VERSION}"; then
    log_info "镜像 mongo:${MONGO_VERSION} 拉取成功"
  else
    log_error "镜像拉取失败"
    exit 1
  fi
else
  log_info "镜像 mongo:${MONGO_VERSION} 已存在，跳过拉取"
fi

# 4. 启动容器
log_info "启动容器 ${CONTAINER_NAME}"

CONTAINER_ID=$(docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart=always \
  --privileged=true \
  -p "${MONGO_PORT}:27017" \
  -e TZ=Asia/Shanghai \
  -e MONGO_INITDB_ROOT_USERNAME="${MONGO_ROOT_USER}" \
  -e MONGO_INITDB_ROOT_PASSWORD="${MONGO_ROOT_PASSWORD}" \
  -e MONGO_INITDB_DATABASE="${MONGO_DATABASE}" \
  -v "${CONF_PATH}:/etc/mongod.conf" \
  -v "${DATA_ROOT}/data:/data/db" \
  -v "${DATA_ROOT}/log:/var/log/mongodb" \
  -v "${DATA_ROOT}/init:/docker-entrypoint-initdb.d" \
  "mongo:${MONGO_VERSION}" \
  --config /etc/mongod.conf)

log_info "容器已创建，ID: ${CONTAINER_ID}"
log_info "等待容器启动..."
sleep 5

# 显示容器启动日志（使用 set +e 避免管道命令触发 ERR trap）
log_info "容器启动日志："
set +e
docker logs "${CONTAINER_NAME}" 2>&1 | head -30 | while IFS= read -r line || [ -n "$line" ]; do
  log_info "  $line"
done
set -e

if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
  log_info "容器 ${CONTAINER_NAME} 启动成功。"
  log_info "端口映射: ${MONGO_PORT} -> 27017"
  log_info "用户名: ${MONGO_ROOT_USER}"
  log_info "密码: ${MONGO_ROOT_PASSWORD}"
  log_info "数据库: ${MONGO_DATABASE}"
else
  log_error "容器未在预期时间内启动，请检查日志：docker logs ${CONTAINER_NAME}"
  exit 1
fi

# 5. 等待 MongoDB 服务就绪并验证
log_info "等待 MongoDB 服务就绪..."
MAX_RETRIES=60
RETRY_COUNT=0
MONGO_READY=false

# 使用 set +e 避免 docker exec 失败触发 ERR trap
set +e
while [[ $RETRY_COUNT -lt $MAX_RETRIES ]]; do
  # 首先检查容器是否还在运行
  if ! docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
    log_error "容器 ${CONTAINER_NAME} 已停止运行"
    log_error "请检查日志：docker logs ${CONTAINER_NAME}"
    set -e
    exit 1
  fi
  
  # 检查 MongoDB 是否就绪（先尝试无认证连接，再尝试认证连接）
  # MongoDB 8.0 在初始化用户之前可能不需要认证，初始化完成后才需要认证
  if docker exec "${CONTAINER_NAME}" mongosh --quiet --eval "db.adminCommand('ping')" >/dev/null 2>&1 || \
     docker exec "${CONTAINER_NAME}" mongosh --quiet \
    -u "${MONGO_ROOT_USER}" \
    -p "${MONGO_ROOT_PASSWORD}" \
    --authenticationDatabase "${MONGO_DATABASE}" \
    --eval "db.adminCommand('ping')" >/dev/null 2>&1 || \
     docker exec "${CONTAINER_NAME}" mongo --quiet --eval "db.adminCommand('ping')" >/dev/null 2>&1 || \
     docker exec "${CONTAINER_NAME}" mongo --quiet \
    -u "${MONGO_ROOT_USER}" \
    -p "${MONGO_ROOT_PASSWORD}" \
    --authenticationDatabase "${MONGO_DATABASE}" \
    --eval "db.adminCommand('ping')" >/dev/null 2>&1; then
    MONGO_READY=true
    log_info "MongoDB 服务已就绪"
    break
  fi
  RETRY_COUNT=$((RETRY_COUNT + 1))
  if [[ $((RETRY_COUNT % 10)) -eq 0 ]]; then
    log_info "等待 MongoDB 服务启动中... (${RETRY_COUNT}/${MAX_RETRIES})"
  fi
  sleep 2
done
set -e

if [[ "${MONGO_READY}" != "true" ]]; then
  log_warn "MongoDB 服务在预期时间内未就绪，但容器已启动"
  log_warn "请手动检查：docker logs ${CONTAINER_NAME}"
  log_warn "容器可能仍在初始化中，请稍后手动验证连接"
else
  # 测试 MongoDB 连接和认证
  log_info "测试 MongoDB 连接..."
  
  # 尝试使用 mongosh（MongoDB 6+）
  MONGO_CLI="mongosh"
  set +e
  if ! docker exec "${CONTAINER_NAME}" mongosh --version >/dev/null 2>&1; then
    MONGO_CLI="mongo"
  fi
  set -e
  
  # 测试连接和认证（使用 set +e 避免命令失败导致脚本退出）
  set +e
  CONNECTION_SUCCESS=false
  
  # 先尝试无认证连接（适用于初始化阶段）
  if docker exec "${CONTAINER_NAME}" ${MONGO_CLI} --quiet \
    --eval "db.adminCommand('ping')" >/dev/null 2>&1; then
    CONNECTION_SUCCESS=true
    log_info "✓ MongoDB 连接测试成功（无认证模式）"
  # 再尝试认证连接（适用于初始化完成后）
  elif docker exec "${CONTAINER_NAME}" ${MONGO_CLI} --quiet \
    -u "${MONGO_ROOT_USER}" \
    -p "${MONGO_ROOT_PASSWORD}" \
    --authenticationDatabase "${MONGO_DATABASE}" \
    --eval "db.adminCommand('ping')" >/dev/null 2>&1; then
    CONNECTION_SUCCESS=true
    log_info "✓ MongoDB 连接和认证测试成功"
    
    # 获取 MongoDB 版本信息
    MONGO_VERSION_INFO=$(docker exec "${CONTAINER_NAME}" ${MONGO_CLI} --quiet \
      -u "${MONGO_ROOT_USER}" \
      -p "${MONGO_ROOT_PASSWORD}" \
      --authenticationDatabase "${MONGO_DATABASE}" \
      --eval "db.version()" 2>/dev/null | tr -d '\r\n' || echo "")
    if [[ -n "${MONGO_VERSION_INFO}" ]]; then
      log_info "  MongoDB 版本: ${MONGO_VERSION_INFO}"
    fi
  fi
  
  if [[ "${CONNECTION_SUCCESS}" != "true" ]]; then
    log_warn "MongoDB 连接测试失败，但服务可能仍在初始化中"
    log_warn "请稍后手动测试：docker exec -it ${CONTAINER_NAME} ${MONGO_CLI} -u ${MONGO_ROOT_USER} -p ${MONGO_ROOT_PASSWORD} --authenticationDatabase ${MONGO_DATABASE}"
  fi
  set -e
fi

log_info "=== MongoDB Docker 安装完成 ==="
log_info ""
log_info "使用说明："
log_info "1. 连接 MongoDB："
log_info "   本地连接: mongosh mongodb://${MONGO_ROOT_USER}:${MONGO_ROOT_PASSWORD}@192.168.3.100:${MONGO_PORT}/${MONGO_DATABASE}"
log_info "   容器内连接: docker exec -it ${CONTAINER_NAME} mongosh -u ${MONGO_ROOT_USER} -p ${MONGO_ROOT_PASSWORD} --authenticationDatabase ${MONGO_DATABASE}"
log_info ""
log_info "2. 连接字符串（MongoDB URI）："
log_info "   mongodb://${MONGO_ROOT_USER}:${MONGO_ROOT_PASSWORD}@192.168.3.100:${MONGO_PORT}/${MONGO_DATABASE}?authSource=${MONGO_DATABASE}"
log_info ""
log_info "3. Spring Boot 配置示例："
log_info "   spring:"
log_info "     data:"
log_info "       mongodb:"
log_info "         uri: mongodb://${MONGO_ROOT_USER}:${MONGO_ROOT_PASSWORD}@192.168.3.100:${MONGO_PORT}/${MONGO_DATABASE}"
log_info ""
log_info "4. 常用命令："
log_info "   查看日志: docker logs ${CONTAINER_NAME}"
log_info "   查看日志（实时）: docker logs -f ${CONTAINER_NAME}"
log_info "   进入容器: docker exec -it ${CONTAINER_NAME} bash"
log_info "   进入 MongoDB Shell: docker exec -it ${CONTAINER_NAME} mongosh -u ${MONGO_ROOT_USER} -p ${MONGO_ROOT_PASSWORD} --authenticationDatabase ${MONGO_DATABASE}"
log_info "   查看数据库列表: docker exec -it ${CONTAINER_NAME} mongosh -u ${MONGO_ROOT_USER} -p ${MONGO_ROOT_PASSWORD} --authenticationDatabase ${MONGO_DATABASE} --eval 'show dbs'"
log_info "   查看用户列表: docker exec -it ${CONTAINER_NAME} mongosh -u ${MONGO_ROOT_USER} -p ${MONGO_ROOT_PASSWORD} --authenticationDatabase ${MONGO_DATABASE} --eval 'db.getUsers()'"
log_info ""
log_info "5. 数据目录："
log_info "   ${DATA_ROOT}/data"
log_info ""
log_info "6. 配置文件："
log_info "   ${CONF_PATH}"
log_info ""
log_info "7. 停止/启动容器："
log_info "   docker stop ${CONTAINER_NAME}"
log_info "   docker start ${CONTAINER_NAME}"
log_info ""
log_info "8. 创建新用户（可选）："
log_info "   docker exec -it ${CONTAINER_NAME} mongosh -u ${MONGO_ROOT_USER} -p ${MONGO_ROOT_PASSWORD} --authenticationDatabase ${MONGO_DATABASE}"
log_info "   然后执行："
log_info "   use myapp"
log_info "   db.createUser({"
log_info "     user: 'appuser',"
log_info "     pwd: 'apppassword',"
log_info "     roles: [{ role: 'readWrite', db: 'myapp' }]"
log_info "   })"
log_info ""
log_info "9. 创建新数据库（可选）："
log_info "   docker exec -it ${CONTAINER_NAME} mongosh -u ${MONGO_ROOT_USER} -p ${MONGO_ROOT_PASSWORD} --authenticationDatabase ${MONGO_DATABASE}"
log_info "   然后执行："
log_info "   use myapp"
log_info "   db.test.insertOne({ name: 'test' })"

