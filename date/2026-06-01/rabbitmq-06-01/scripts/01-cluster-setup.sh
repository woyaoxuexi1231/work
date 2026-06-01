#!/usr/bin/env bash
# ============================================================================
# 01-cluster-setup.sh —— RabbitMQ 3 节点集群部署
# 运行环境：192.168.3.100 Ubuntu + Docker
# 用法：sudo bash 01-cluster-setup.sh
# ============================================================================
set -euo pipefail

NETWORK="rmq-cluster-net"
COOKIE="reasonix-cluster-secret-2024"
IMAGE="rabbitmq:3.12-management"

echo "============================================"
echo " RabbitMQ 3-Node Cluster Setup"
echo "============================================"

# ---------- 1. 创建 Docker 网络 ----------
echo "[1/4] 创建网络: ${NETWORK}"
docker network rm ${NETWORK} 2>&1 || true
docker network create ${NETWORK} --subnet=172.28.0.0/16

# ---------- 2. 启动 3 个节点 ----------
echo "[2/4] 启动 3 个容器..."

for i in 1 2 3; do
  docker rm -f rabbitmq-node${i} 2>&1 || true
  docker run -d \
    --name rabbitmq-node${i} \
    --hostname rabbitmq-node${i} \
    --network ${NETWORK} \
    --ip 172.28.0.$((9 + i)) \
    -p $((5671 + i)):5672 \
    -p $((15671 + i)):15672 \
    -e RABBITMQ_ERLANG_COOKIE="${COOKIE}" \
    -e RABBITMQ_DEFAULT_USER=admin \
    -e RABBITMQ_DEFAULT_PASS=admin123 \
    ${IMAGE}
done

# ---------- 3. 等待就绪 ----------
echo "[3/4] 等待节点就绪..."
for i in 1 2 3; do
  echo -n "  等待 rabbitmq-node${i}..."
  for j in $(seq 1 60); do
    OUT=$(docker exec rabbitmq-node${i} rabbitmqctl status 2>&1 || true)
    if echo "${OUT}" | grep -q "Runtime"; then echo " ✓"; break; fi
    sleep 2; echo -n "."
  done
done

# ---------- 4. 组建集群 ----------
echo "[4/4] 组建集群..."

for i in 2 3; do
  echo "  rabbitmq-node${i} → join rabbitmq-node1"
  docker exec rabbitmq-node${i} rabbitmqctl stop_app 2>&1
  docker exec rabbitmq-node${i} rabbitmqctl reset 2>&1
  docker exec rabbitmq-node${i} rabbitmqctl join_cluster rabbit@rabbitmq-node1 2>&1
  docker exec rabbitmq-node${i} rabbitmqctl start_app 2>&1
done

sleep 3

echo ""
echo "============================================"
echo " ✅ 集群搭建完成!"
echo ""
echo " 管理界面:"
echo "   http://192.168.3.100:15672  (node1)"
echo "   http://192.168.3.100:15673  (node2)"
echo "   http://192.168.3.100:15674  (node3)"
echo ""
echo " AMQP:"
echo "   192.168.3.100:5672 / 5673 / 5674"
echo "   用户名/密码: admin / admin123"
echo "============================================"
