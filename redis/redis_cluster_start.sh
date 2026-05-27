#!/usr/bin/env bash
# ========================================================================================
# Docker Redis Cluster（集群）安装脚本
# 6 个 Redis 节点：3 主 + 3 从，每个主节点自动分配一个从节点
# 端口：7000 7001 7002 7003 7004 7005
# ========================================================================================

if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

REDIS_VERSION="${REDIS_VERSION:-7.2.5}"
REDIS_PASSWORD="${REDIS_PASSWORD:-123456}"
DATA_ROOT="${DATA_ROOT:-/root/redis-cluster-docker}"

# 集群网络
NETWORK_NAME="redis-cluster-net"

# 节点配置
NODES=(
  "node-7000:7000"
  "node-7001:7001"
  "node-7002:7002"
  "node-7003:7003"
  "node-7004:7004"
  "node-7005:7005"
)

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

log_info "=== Docker Redis Cluster（集群）安装 ==="
log_info "Redis 版本: ${REDIS_VERSION}"
log_info "密码: ${REDIS_PASSWORD}"
log_info "集群节点: 7000 7001 7002 7003 7004 7005"
echo ""

# 清理旧容器和网络
log_info "清理旧容器..."
for node in node-7000 node-7001 node-7002 node-7003 node-7004 node-7005; do
  docker stop "$node" 2>/dev/null || true
  docker rm "$node" 2>/dev/null || true
done
docker network rm "${NETWORK_NAME}" 2>/dev/null || true

# 创建网络
log_info "创建 Docker 网络..."
docker network create "${NETWORK_NAME}"

# 拉取镜像
log_info "拉取 Redis 镜像..."
docker pull "redis:${REDIS_VERSION}"

# 生成配置并启动节点
for node_entry in "${NODES[@]}"; do
  IFS=':' read -r node_name node_port <<< "$node_entry"
  
  ensure_dir "${DATA_ROOT}/${node_name}/data"
  
  log_info "生成 ${node_name} 配置..."
  cat > "${DATA_ROOT}/${node_name}/redis.conf" << EOF
port ${node_port}
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 5000
cluster-announce-ip \$(hostname -i)
cluster-announce-port ${node_port}
cluster-announce-bus-port 1${node_port}
appendonly yes
requirepass ${REDIS_PASSWORD}
masterauth ${REDIS_PASSWORD}
protected-mode no
daemonize no
logfile ""
dir /data
EOF

  log_info "启动 ${node_name} (端口 ${node_port})..."
  docker run -d \
    --name "${node_name}" \
    --network "${NETWORK_NAME}" \
    --restart=always \
    --privileged=true \
    -p "${node_port}:${node_port}" \
    -p "1${node_port}:1${node_port}" \
    -v "${DATA_ROOT}/${node_name}/redis.conf":/usr/local/etc/redis/redis.conf \
    -v "${DATA_ROOT}/${node_name}/data":/data \
    "redis:${REDIS_VERSION}" \
    redis-server /usr/local/etc/redis/redis.conf
done

log_info "等待所有节点启动..."
sleep 10

# ==================== 创建集群 ====================
log_info "创建 Redis Cluster..."

# 获取节点 IP 列表
NODE_IPS=""
for node_entry in "${NODES[@]}"; do
  IFS=':' read -r node_name node_port <<< "$node_entry"
  IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "${node_name}")
  NODE_IPS="${NODE_IPS} ${IP}:${node_port}"
done
NODE_IPS=$(echo "$NODE_IPS" | xargs)

log_info "节点地址: ${NODE_IPS}"

# 创建集群（3 主 3 从，每个主自动分配 1 个从）
docker exec node-7000 redis-cli -a "${REDIS_PASSWORD}" --cluster create ${NODE_IPS} \
  --cluster-replicas 1 --cluster-yes

echo ""
log_info "等待集群就绪..."
sleep 5

# ==================== 检查状态 ====================
echo ""
echo "============================================"
log_info "安装完成！"
echo "============================================"
echo ""

log_info "集群节点状态:"
for node_entry in "${NODES[@]}"; do
  IFS=':' read -r node_name node_port <<< "$node_entry"
  STATUS=$(docker ps --filter "name=${node_name}" --format "{{.Status}}" 2>/dev/null || echo "未运行")
  echo "  ${node_name} (端口 ${node_port}): ${STATUS}"
done

echo ""
log_info "集群信息:"
docker exec node-7000 redis-cli -a "${REDIS_PASSWORD}" cluster info | head -5

echo ""
log_info "集群节点:"
docker exec node-7000 redis-cli -a "${REDIS_PASSWORD}" cluster nodes

echo ""
log_info "连接信息:"
echo "  节点 7000:  redis-cli -h localhost -p 7000 -a ${REDIS_PASSWORD} -c"
echo "  节点 7001:  redis-cli -h localhost -p 7001 -a ${REDIS_PASSWORD} -c"
echo "  节点 7002:  redis-cli -h localhost -p 7002 -a ${REDIS_PASSWORD} -c"
echo "  节点 7003:  redis-cli -h localhost -p 7003 -a ${REDIS_PASSWORD} -c"
echo "  节点 7004:  redis-cli -h localhost -p 7004 -a ${REDIS_PASSWORD} -c"
echo "  节点 7005:  redis-cli -h localhost -p 7005 -a ${REDIS_PASSWORD} -c"
echo ""
log_info "注意：连接集群模式需要加 -c 参数"
echo ""
log_warn "请查看 redis_cluster_guide.md 了解更多操作指南"
echo ""
