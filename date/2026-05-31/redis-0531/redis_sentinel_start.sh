#!/usr/bin/env bash
# ========================================================================================
# Docker Redis Sentinel（哨兵）安装脚本
# 3 个 Redis 实例（1 主 + 2 从）+ 3 个 Sentinel 实例
# 端口：6379(主) 6380(从) 6381(从)，Sentinel：26379 26380 26381
# ========================================================================================

if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

REDIS_VERSION="${REDIS_VERSION:-7.2.5}"
REDIS_PASSWORD="${REDIS_PASSWORD:-123456}"
DATA_ROOT="${DATA_ROOT:-/root/redis-sentinel-docker}"

# 本机 IP（Sentinel 通过宿主机暴露端口连接，直接用本机 IP 避免容器 DNS 问题）
HOST_IP="${HOST_IP:-192.168.3.100}"

# 网络名称
NETWORK_NAME="redis-sentinel-net"

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

log_info "=== Docker Redis Sentinel（哨兵）安装 ==="
log_info "Redis 版本: ${REDIS_VERSION}"
log_info "密码: ${REDIS_PASSWORD}"
log_info "Redis: 6379(主) 6380(从) 6381(从)"
log_info "Sentinel: 26379 26380 26381"
echo ""

# 清理旧容器和网络
log_info "清理旧容器..."
for name in redis-master redis-slave-1 redis-slave-2 sentinel-1 sentinel-2 sentinel-3; do
  docker stop "$name" 2>/dev/null || true
  docker rm "$name" 2>/dev/null || true
done
docker network rm "${NETWORK_NAME}" 2>/dev/null || true

# 创建网络
log_info "创建 Docker 网络..."
docker network create "${NETWORK_NAME}"

# 创建目录和配置
log_info "创建目录结构..."

MASTER_HOST="redis-master"

# 拉取镜像
log_info "拉取 Redis 镜像..."
docker pull "redis:${REDIS_VERSION}"

# ==================== 启动 Redis 实例 ====================

# 主库配置
log_info "生成主库配置..."
ensure_dir "${DATA_ROOT}/master/data"
cat > "${DATA_ROOT}/master/redis.conf" << EOF
port 6379
requirepass ${REDIS_PASSWORD}
masterauth ${REDIS_PASSWORD}
protected-mode no
daemonize no
appendonly yes
logfile ""
dir /data
EOF

# 从库 1 配置
log_info "生成从库 1 配置..."
ensure_dir "${DATA_ROOT}/slave-1/data"
cat > "${DATA_ROOT}/slave-1/redis.conf" << EOF
port 6379
replicaof ${MASTER_HOST} 6379
masterauth ${REDIS_PASSWORD}
requirepass ${REDIS_PASSWORD}
protected-mode no
daemonize no
appendonly yes
logfile ""
dir /data
EOF

# 从库 2 配置
log_info "生成从库 2 配置..."
ensure_dir "${DATA_ROOT}/slave-2/data"
cat > "${DATA_ROOT}/slave-2/redis.conf" << EOF
port 6379
replicaof ${MASTER_HOST} 6379
masterauth ${REDIS_PASSWORD}
requirepass ${REDIS_PASSWORD}
protected-mode no
daemonize no
appendonly yes
logfile ""
dir /data
EOF

# 启动主库
log_info "启动 Redis 主库 (端口 6379)..."
docker run -d \
  --name redis-master \
  --network "${NETWORK_NAME}" \
  --restart=always \
  --privileged=true \
  -p 6379:6379 \
  -v /etc/localtime:/etc/localtime:ro \
  -e TZ=Asia/Shanghai \
  -v "${DATA_ROOT}/master/redis.conf":/usr/local/etc/redis/redis.conf \
  -v "${DATA_ROOT}/master/data":/data \
  "redis:${REDIS_VERSION}" \
  redis-server /usr/local/etc/redis/redis.conf

# 启动从库 1
log_info "启动 Redis 从库 1 (端口 6380)..."
docker run -d \
  --name redis-slave-1 \
  --network "${NETWORK_NAME}" \
  --restart=always \
  --privileged=true \
  -p 6380:6379 \
  -v /etc/localtime:/etc/localtime:ro \
  -e TZ=Asia/Shanghai \
  -v "${DATA_ROOT}/slave-1/redis.conf":/usr/local/etc/redis/redis.conf \
  -v "${DATA_ROOT}/slave-1/data":/data \
  "redis:${REDIS_VERSION}" \
  redis-server /usr/local/etc/redis/redis.conf

# 启动从库 2
log_info "启动 Redis 从库 2 (端口 6381)..."
docker run -d \
  --name redis-slave-2 \
  --network "${NETWORK_NAME}" \
  --restart=always \
  --privileged=true \
  -p 6381:6379 \
  -v /etc/localtime:/etc/localtime:ro \
  -e TZ=Asia/Shanghai \
  -v "${DATA_ROOT}/slave-2/redis.conf":/usr/local/etc/redis/redis.conf \
  -v "${DATA_ROOT}/slave-2/data":/data \
  "redis:${REDIS_VERSION}" \
  redis-server /usr/local/etc/redis/redis.conf

# ==================== 启动 Sentinel 实例 ====================

log_info "等待 Redis 实例启动..."
sleep 5

# Sentinel 配置：通过宿主机暴露端口连接，用本机 IP 避免容器 DNS 校验问题
for i in 1 2 3; do
  sentinel_name="sentinel-${i}"
  sentinel_port=$((26378 + i))
  ensure_dir "${DATA_ROOT}/${sentinel_name}"

  cat > "${DATA_ROOT}/${sentinel_name}/sentinel.conf" << EOF
port 26379
sentinel monitor mymaster ${HOST_IP} 6379 2
sentinel auth-pass mymaster ${REDIS_PASSWORD}
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 10000
sentinel parallel-syncs mymaster 1
protected-mode no
daemonize no
logfile ""
dir /data
EOF

  log_info "启动 Sentinel ${sentinel_name} (端口 ${sentinel_port})..."
  docker run -d \
    --name "${sentinel_name}" \
    --network "${NETWORK_NAME}" \
    --restart=always \
    --privileged=true \
    -p "${sentinel_port}:26379" \
    -v /etc/localtime:/etc/localtime:ro \
    -e TZ=Asia/Shanghai \
    -v "${DATA_ROOT}/${sentinel_name}/sentinel.conf":/usr/local/etc/redis/sentinel.conf \
    -v "${DATA_ROOT}/${sentinel_name}":/data \
    "redis:${REDIS_VERSION}" \
    redis-sentinel /usr/local/etc/redis/sentinel.conf
done

echo ""
log_info "等待所有服务启动..."
sleep 10

# ==================== 检查状态 ====================
echo ""
echo "============================================"
log_info "安装完成！"
echo "============================================"
echo ""

log_info "Redis 实例:"
for name in redis-master redis-slave-1 redis-slave-2; do
  STATUS=$(docker ps --filter "name=${name}" --format "{{.Status}}" 2>/dev/null || echo "未运行")
  echo "  ${name}: ${STATUS}"
done

log_info "Sentinel 实例:"
for name in sentinel-1 sentinel-2 sentinel-3; do
  STATUS=$(docker ps --filter "name=${name}" --format "{{.Status}}" 2>/dev/null || echo "未运行")
  echo "  ${name}: ${STATUS}"
done

echo ""
log_info "连接信息:"
echo "  Redis 主库:           redis-cli -h localhost -p 6379 -a ${REDIS_PASSWORD}"
echo "  Redis 从库 1:         redis-cli -h localhost -p 6380 -a ${REDIS_PASSWORD}"
echo "  Redis 从库 2:         redis-cli -h localhost -p 6381 -a ${REDIS_PASSWORD}"
echo "  Sentinel 1:           redis-cli -h localhost -p 26379"
echo "  Sentinel 2:           redis-cli -h localhost -p 26380"
echo "  Sentinel 3:           redis-cli -h localhost -p 26381"
echo ""
log_info "查看 Sentinel 状态:"
echo "  redis-cli -h localhost -p 26379 sentinel master mymaster"
echo "  redis-cli -h localhost -p 26379 sentinel slaves mymaster"
echo ""
log_warn "请查看 redis_sentinel_guide.md 了解更多操作指南"
echo ""
