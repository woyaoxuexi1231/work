#!/usr/bin/env bash
# ========================================================================================
# Docker Redis Cluster 安装脚本
# 6 节点（3 主 + 3 从），端口 7000-7005，总线 17000-17005
# ========================================================================================
set -euo pipefail

REDIS_VERSION="${REDIS_VERSION:-7.2.5}"
REDIS_PASSWORD="${REDIS_PASSWORD:-123456}"
DATA_ROOT="${DATA_ROOT:-/root/redis-cluster-docker}"
HOST_IP="${HOST_IP:-192.168.3.100}"
NETWORK_NAME="redis-cluster-net"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log_info() { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }

if [[ $EUID -ne 0 ]]; then exec sudo -E bash "$0" "$@"; fi

log_info "=== Docker Redis Cluster 安装 ==="
log_info "节点: 7000-7005（3 主 + 3 从）"

# 清理
for i in 0 1 2 3 4 5; do
  docker stop "redis-cluster-${i}" 2>/dev/null || true
  docker rm "redis-cluster-${i}" 2>/dev/null || true
done
docker network rm "${NETWORK_NAME}" 2>/dev/null || true

docker network create "${NETWORK_NAME}"
docker pull "redis:${REDIS_VERSION}"

# 启动 6 个节点
for i in 0 1 2 3 4 5; do
  port=$((7000 + i))
  bus_port=$((17000 + i))
  dir="${DATA_ROOT}/node-${i}"
  mkdir -p "${dir}"

  cat > "${dir}/redis.conf" << EOF
port 7000
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 5000
cluster-announce-ip ${HOST_IP}
cluster-announce-port ${port}
cluster-announce-bus-port ${bus_port}
requirepass ${REDIS_PASSWORD}
masterauth ${REDIS_PASSWORD}
protected-mode no
daemonize no
appendonly yes
logfile ""
dir /data
EOF

  log_info "启动节点 ${i} (端口 ${port})..."
  docker run -d \
    --name "redis-cluster-${i}" \
    --network "${NETWORK_NAME}" \
    --restart=always \
    --privileged=true \
    -p "${port}:7000" \
    -p "${bus_port}:17000" \
    -v /etc/localtime:/etc/localtime:ro \
    -e TZ=Asia/Shanghai \
    -v "${dir}/redis.conf":/usr/local/etc/redis/redis.conf \
    -v "${dir}":/data \
    "redis:${REDIS_VERSION}" \
    redis-server /usr/local/etc/redis/redis.conf
done

sleep 5

# 创建集群（3 主 + 3 从，槽位自动分配）
log_info "创建集群..."
NODES=""
for i in 0 1 2 3 4 5; do
  NODES="${NODES} ${HOST_IP}:$((7000 + i))"
done

docker exec redis-cluster-0 redis-cli -a "${REDIS_PASSWORD}" --cluster create ${NODES} --cluster-replicas 1 --cluster-yes

sleep 3

echo ""
log_info "安装完成！"
echo ""
log_info "节点列表:"
for i in 0 1 2 3 4 5; do
  echo "  节点${i}: redis-cli -c -h ${HOST_IP} -p $((7000 + i)) -a ${REDIS_PASSWORD}"
done
echo ""
log_info "查看集群状态:"
echo "  redis-cli -c -h ${HOST_IP} -p 7000 -a ${REDIS_PASSWORD} CLUSTER NODES"
echo "  redis-cli -c -h ${HOST_IP} -p 7000 -a ${REDIS_PASSWORD} CLUSTER SLOTS"
