#!/usr/bin/env bash
# ============================================================================
# 01-cluster-setup.sh —— RabbitMQ 3 节点集群部署
# 运行环境：192.168.3.100 Ubuntu + Docker
# 用法：chmod +x 01-cluster-setup.sh && ./01-cluster-setup.sh
# ============================================================================
set -euo pipefail

# ---------- 配置 ----------
NETWORK="rmq-cluster-net"
COOKIE="reasonix-cluster-secret-2024"
RABBITMQ_IMAGE="rabbitmq:3.12-management"
NODES=("rabbitmq-node1" "rabbitmq-node2" "rabbitmq-node3")
PORTS_AMQP=(5672 5673 5674)
PORTS_MGMT=(15672 15673 15674)
PORTS_EPMD=(4369 4370 4371)  # EPMD 端口，节点发现用

echo "============================================"
echo " RabbitMQ 3-Node Cluster Setup"
echo " Machine: 192.168.3.100"
echo "============================================"

# ---------- 1. 创建 Docker 网络 ----------
echo ""
echo "[1/5] 创建 Docker 网络: ${NETWORK}"
docker network rm ${NETWORK} 2>/dev/null || true
docker network create ${NETWORK} --subnet=172.28.0.0/16
echo "  ✓ 网络创建成功"

# ---------- 2. 启动 3 个节点 ----------
echo ""
echo "[2/5] 启动 3 个 RabbitMQ 容器"

for i in "${!NODES[@]}"; do
  NODE="${NODES[$i]}"
  AMQP_PORT="${PORTS_AMQP[$i]}"
  MGMT_PORT="${PORTS_MGMT[$i]}"
  EPMD_PORT="${PORTS_EPMD[$i]}"
  IP="172.28.0.$((10 + i))"

  echo "  启动 ${NODE} (IP: ${IP}, AMQP: ${AMQP_PORT}, MGMT: ${MGMT_PORT})"

  docker rm -f ${NODE} 2>/dev/null || true

  docker run -d \
    --name ${NODE} \
    --hostname ${NODE} \
    --network ${NETWORK} \
    --ip ${IP} \
    -p ${AMQP_PORT}:5672 \
    -p ${MGMT_PORT}:15672 \
    -p ${EPMD_PORT}:4369 \
    -e RABBITMQ_ERLANG_COOKIE="${COOKIE}" \
    -e RABBITMQ_DEFAULT_USER=admin \
    -e RABBITMQ_DEFAULT_PASS=admin123 \
    ${RABBITMQ_IMAGE}

  echo "    ✓ ${NODE} 已启动"
done

# ---------- 3. 等待所有节点就绪 ----------
echo ""
echo "[3/5] 等待所有节点 RabbitMQ 应用就绪..."

wait_for_rabbit() {
  local name=$1
  local port=$2
  echo -n "  等待 ${name} (端口 ${port})..."
  for i in $(seq 1 60); do
    if docker exec ${name} rabbitmqctl status 2>/dev/null | grep -q "Runtime"; then
      echo " ✓"
      return 0
    fi
    sleep 2
    echo -n "."
  done
  echo " ✗ 超时!"
  return 1
}

for i in "${!NODES[@]}"; do
  wait_for_rabbit "${NODES[$i]}" "${PORTS_MGMT[$i]}"
done

# ---------- 4. 组建集群 ----------
echo ""
echo "[4/5] 组建集群 (node2, node3 → join node1)"

# node2 加入 node1
echo "  node2 → join_cluster rabbit@rabbitmq-node1"
docker exec rabbitmq-node2 rabbitmqctl stop_app
docker exec rabbitmq-node2 rabbitmqctl reset
docker exec rabbitmq-node2 rabbitmqctl join_cluster rabbit@rabbitmq-node1
docker exec rabbitmq-node2 rabbitmqctl start_app

# node3 加入 node1
echo "  node3 → join_cluster rabbit@rabbitmq-node1"
docker exec rabbitmq-node3 rabbitmqctl stop_app
docker exec rabbitmq-node3 rabbitmqctl reset
docker exec rabbitmq-node3 rabbitmqctl join_cluster rabbit@rabbitmq-node1
docker exec rabbitmq-node3 rabbitmqctl start_app

sleep 3

# ---------- 5. 验证集群状态 ----------
echo ""
echo "[5/5] 验证集群状态"
echo ""

docker exec rabbitmq-node1 rabbitmqctl cluster_status

echo ""
echo "============================================"
echo " ✅ 集群搭建完成!"
echo ""
echo " 管理界面入口:"
echo "   http://192.168.3.100:15672  (node1)"
echo "   http://192.168.3.100:15673  (node2)"
echo "   http://192.168.3.100:15674  (node3)"
echo ""
echo " AMQP 连接地址:"
echo "   192.168.3.100:5672  (node1)"
echo "   192.168.3.100:5673  (node2)"
echo "   192.168.3.100:5674  (node3)"
echo ""
echo " 用户名/密码: admin / admin123"
echo ""
echo " 常用命令:"
echo "   docker exec rabbitmq-node1 rabbitmqctl cluster_status"
echo "   docker exec rabbitmq-node2 rabbitmqctl stop_app   # 模拟节点宕机"
echo "   docker exec rabbitmq-node2 rabbitmqctl start_app  # 恢复节点"
echo "============================================"
