#!/usr/bin/env bash
# ========================================================================================
# Docker RabbitMQ 集群安装脚本（经典集群模式）
# 3 个 RabbitMQ 节点组成集群（队列数据不自动复制，需手动配置策略）
# 端口：5672/15672 5673/15673 5674/15674
# ========================================================================================

if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

RABBITMQ_VERSION="${RABBITMQ_VERSION:-3.12-management}"
RABBITMQ_DEFAULT_USER="${RABBITMQ_DEFAULT_USER:-admin}"
RABBITMQ_DEFAULT_PASS="${RABBITMQ_DEFAULT_PASS:-123456}"
DATA_ROOT="${DATA_ROOT:-/root/rabbitmq-cluster-docker}"

NETWORK_NAME="rabbitmq-cluster-net"

# 节点配置
NODES=(
  "rabbitmq-c1:5672:15672"
  "rabbitmq-c2:5673:15673"
  "rabbitmq-c3:5674:15674"
)

ERLANG_COOKIE="RABBITMQ_CLUSTER_COOKIE_SECRET"

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

log_info "=== Docker RabbitMQ 集群安装 ==="
log_info "RabbitMQ 版本: ${RABBITMQ_VERSION}"
log_info "管理用户: ${RABBITMQ_DEFAULT_USER}"
log_info "密码: ${RABBITMQ_DEFAULT_PASS}"
log_info "节点: 5672/15672  5673/15673  5674/15674"
echo ""

# 清理旧容器和网络
log_info "清理旧容器..."
for name in rabbitmq-c1 rabbitmq-c2 rabbitmq-c3; do
  docker stop "$name" 2>/dev/null || true
  docker rm "$name" 2>/dev/null || true
done
docker network rm "${NETWORK_NAME}" 2>/dev/null || true

# 创建网络
log_info "创建 Docker 网络..."
docker network create "${NETWORK_NAME}"

# 拉取镜像
log_info "拉取 RabbitMQ 镜像..."
docker pull "rabbitmq:${RABBITMQ_VERSION}"

# ==================== 启动节点 ====================

for node_entry in "${NODES[@]}"; do
  IFS=':' read -r node_name amqp_port mgmt_port <<< "$node_entry"
  
  ensure_dir "${DATA_ROOT}/${node_name}/data"
  ensure_dir "${DATA_ROOT}/${node_name}/log"
  
  log_info "启动 ${node_name} (AMQP:${amqp_port} 管理:${mgmt_port})..."
  docker run -d \
    --name "${node_name}" \
    --network "${NETWORK_NAME}" \
    --hostname "${node_name}" \
    --restart=always \
    --privileged=true \
    -p "${amqp_port}:5672" \
    -p "${mgmt_port}:15672" \
    -e RABBITMQ_DEFAULT_USER="${RABBITMQ_DEFAULT_USER}" \
    -e RABBITMQ_DEFAULT_PASS="${RABBITMQ_DEFAULT_PASS}" \
    -e RABBITMQ_ERLANG_COOKIE="${ERLANG_COOKIE}" \
    -v "${DATA_ROOT}/${node_name}/data":/var/lib/rabbitmq \
    -v "${DATA_ROOT}/${node_name}/log":/var/log/rabbitmq \
    "rabbitmq:${RABBITMQ_VERSION}"
done

log_info "等待节点启动（RabbitMQ 启动较慢）..."
sleep 30

# ==================== 组建集群 ====================

log_info "组建 RabbitMQ 集群..."

# c2 加入 c1
log_info "rabbitmq-c2 加入 rabbitmq-c1..."
docker exec rabbitmq-c2 bash -c "
  rabbitmqctl stop_app && \
  rabbitmqctl reset && \
  rabbitmqctl join_cluster rabbit@rabbitmq-c1 && \
  rabbitmqctl start_app
"

# c3 加入 c1
log_info "rabbitmq-c3 加入 rabbitmq-c1..."
docker exec rabbitmq-c3 bash -c "
  rabbitmqctl stop_app && \
  rabbitmqctl reset && \
  rabbitmqctl join_cluster rabbit@rabbitmq-c1 && \
  rabbitmqctl start_app
"

sleep 5

# ==================== 配置 Quorum Queue 策略（推荐替代镜像队列） ====================

log_info "配置推荐策略..."

# 启用 Quorum Queue 特性标志
docker exec rabbitmq-c1 rabbitmqctl enable_feature_flag all 2>/dev/null || true

# 全局默认：使用 Quorum Queue（推荐，高可用 + 自动故障转移）
docker exec rabbitmq-c1 rabbitmqctl set_policy quorum-default "^quorum\." \
  '{"queue-master-locator":"min-masters"}' \
  --priority 5 --apply-to queues 2>/dev/null || true

# 如果不需要策略，可以跳过
log_info "集群模式：队列数据默认只在创建节点上"
log_info "使用 Quorum Queue 或配置镜像策略来实现高可用"

# 删除可能残留的默认策略
docker exec rabbitmq-c1 rabbitmqctl clear_policy ha-all 2>/dev/null || true

# ==================== 检查状态 ====================
echo ""
echo "============================================"
log_info "安装完成！"
echo "============================================"
echo ""

log_info "集群状态:"
docker exec rabbitmq-c1 rabbitmqctl cluster_status | grep -A 20 "Cluster name"

echo ""
log_info "当前策略:"
docker exec rabbitmq-c1 rabbitmqctl list_policies 2>/dev/null || echo "  无策略"

echo ""
log_info "连接信息:"
echo "  RabbitMQ AMQP:  amqp://localhost:5672  (用户: ${RABBITMQ_DEFAULT_USER}  密码: ${RABBITMQ_DEFAULT_PASS})"
echo "  RabbitMQ AMQP:  amqp://localhost:5673"
echo "  RabbitMQ AMQP:  amqp://localhost:5674"
echo ""
echo "  管理界面:"
echo "  http://localhost:15672  (用户: ${RABBITMQ_DEFAULT_USER}  密码: ${RABBITMQ_DEFAULT_PASS})"
echo "  http://localhost:15673"
echo "  http://localhost:15674"
echo ""
log_info "容器状态:"
for node_entry in "${NODES[@]}"; do
  IFS=':' read -r node_name amqp_port mgmt_port <<< "$node_entry"
  STATUS=$(docker ps --filter "name=${node_name}" --format "{{.Status}}" 2>/dev/null || echo "未运行")
  echo "  ${node_name}: ${STATUS}"
done
echo ""
log_warn "============================================"
log_warn "注意：经典集群只共享元数据（交换机、队列定义、绑定）"
log_warn "      队列数据 NOT 自动复制！请使用以下方式之一："
log_warn "      1. Quorum Queue（推荐）：声明队列时设置 x-queue-type: quorum"
log_warn "      2. 镜像队列（传统）：运行镜像队列脚本或手动添加策略"
log_warn "      3. 应用层双写：生产者同时发送到多个队列"
log_warn "============================================"
echo ""
log_warn "请查看 rabbitmq_cluster_guide.md 了解更多操作指南"
echo ""
