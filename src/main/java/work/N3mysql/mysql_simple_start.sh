#!/usr/bin/env bash
# ========================================================================================
# Docker MySQL 双实例安装脚本
# 启动两个 MySQL 容器：主库 3306，从库 3307
# ========================================================================================

if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"
MYSQL_VERSION="${MYSQL_VERSION:-8.0.35}"
MASTER_PORT="${MASTER_PORT:-3306}"
SLAVE_PORT="${SLAVE_PORT:-3307}"
DATA_ROOT="${DATA_ROOT:-/root/mysql-docker}"

MASTER_NAME="mysql-master"
SLAVE_NAME="mysql-slave"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

ensure_dir() {
  mkdir -p "$1"
}

# 检查 root 权限
if [[ $EUID -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    exec sudo -E bash "$0" "$@"
  else
    log_error "需要 root 权限"
    exit 1
  fi
fi

# 检查 Docker
if ! command -v docker >/dev/null 2>&1; then
  log_error "Docker 未安装"
  exit 1
fi

log_info "=== Docker MySQL 双实例安装 ==="
log_info "MySQL 版本: ${MYSQL_VERSION}"
log_info "主库端口: ${MASTER_PORT}"
log_info "从库端口: ${SLAVE_PORT}"
log_info "Root 密码: ${MYSQL_ROOT_PASSWORD}"
echo ""

# 创建目录结构
log_info "创建目录结构..."
ensure_dir "${DATA_ROOT}/master/data"
ensure_dir "${DATA_ROOT}/master/log"
ensure_dir "${DATA_ROOT}/slave/data"
ensure_dir "${DATA_ROOT}/slave/log"

# 拉取镜像
log_info "拉取 MySQL 镜像..."
docker pull "mysql:${MYSQL_VERSION}"

# 停止并删除旧容器
log_info "清理旧容器..."
docker stop "${MASTER_NAME}" 2>/dev/null || true
docker stop "${SLAVE_NAME}" 2>/dev/null || true
docker rm "${MASTER_NAME}" 2>/dev/null || true
docker rm "${SLAVE_NAME}" 2>/dev/null || true

# 删除旧数据（如果需要全新初始化）
if [[ "${1:-}" == "clean" ]]; then
  log_warn "清理旧数据..."
  rm -rf "${DATA_ROOT}/master/data"/* "${DATA_ROOT}/slave/data"/*
  ensure_dir "${DATA_ROOT}/master/data"
  ensure_dir "${DATA_ROOT}/slave/data"
fi

# 设置权限
chmod -R 777 "${DATA_ROOT}"

# 启动主库
log_info "启动主库 (端口 ${MASTER_PORT})..."
docker run -d \
  --name "${MASTER_NAME}" \
  --restart=always \
  --privileged=true \
  -p ${MASTER_PORT}:3306 \
  -e "MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}" \
  -e TZ=Asia/Shanghai \
  -v "${DATA_ROOT}/master/data":/var/lib/mysql \
  -v "${DATA_ROOT}/master/log":/var/log/mysql \
  "mysql:${MYSQL_VERSION}" \
  --server-id=1 \
  --log-bin=mysql-bin \
  --binlog-format=ROW \
  --default-authentication-plugin=mysql_native_password

# 启动从库
log_info "启动从库 (端口 ${SLAVE_PORT})..."
docker run -d \
  --name "${SLAVE_NAME}" \
  --restart=always \
  --privileged=true \
  -p ${SLAVE_PORT}:3306 \
  -e "MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}" \
  -e TZ=Asia/Shanghai \
  -v "${DATA_ROOT}/slave/data":/var/lib/mysql \
  -v "${DATA_ROOT}/slave/log":/var/log/mysql \
  "mysql:${MYSQL_VERSION}" \
  --server-id=2 \
  --default-authentication-plugin=mysql_native_password

echo ""
log_info "等待 MySQL 启动..."
sleep 15

# 检查状态
MASTER_STATUS=$(docker ps --filter "name=${MASTER_NAME}" --format "{{.Status}}" 2>/dev/null || echo "")
SLAVE_STATUS=$(docker ps --filter "name=${SLAVE_NAME}" --format "{{.Status}}" 2>/dev/null || echo "")

echo ""
echo "============================================"
log_info "安装完成！"
echo "============================================"
echo ""
log_info "容器状态:"
echo "  主库: ${MASTER_STATUS:-未运行}"
echo "  从库: ${SLAVE_STATUS:-未运行}"
echo ""
log_info "连接信息:"
echo "  主库: mysql -h localhost -P ${MASTER_PORT} -u root -p${MYSQL_ROOT_PASSWORD}"
echo "  从库: mysql -h localhost -P ${SLAVE_PORT} -u root -p${MYSQL_ROOT_PASSWORD}"
echo ""
log_info "查看日志: docker logs ${MASTER_NAME}"
log_info "查看日志: docker logs ${SLAVE_NAME}"
echo ""
log_info "============================================"
echo ""
log_warn "请查看 mysql_master_slave_guide.md 配置主从复制"
echo ""
