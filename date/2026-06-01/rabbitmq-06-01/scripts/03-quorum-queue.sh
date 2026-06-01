#!/usr/bin/env bash
# ============================================================================
# 03-quorum-queue.sh —— 创建仲裁队列
# 前置：01-cluster-setup.sh 已运行
# 用法：sudo bash 03-quorum-queue.sh
# ============================================================================
set -euo pipefail

echo "============================================"
echo " 仲裁队列创建"
echo "============================================"

# ---------- 1. 确保 3 节点在线 ----------
for node in rabbitmq-node1 rabbitmq-node2 rabbitmq-node3; do
  RUNNING=$(docker inspect ${node} --format='{{.State.Running}}' 2>&1 || echo "false")
  if [ "${RUNNING}" != "true" ]; then
    docker start ${node} 2>&1 || true
  fi
  docker exec ${node} rabbitmqctl start_app 2>&1 || true
done
sleep 3
echo "✓ 3 节点均在线"

# ---------- 2. 创建仲裁队列 ----------
echo ""
echo "创建仲裁队列 (x-queue-type: quorum)..."

docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 \
  declare queue name=quorum.order.process durable=true \
  arguments='{"x-queue-type":"quorum"}' 2>&1 || true

docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 \
  declare queue name=quorum.payment.result durable=true \
  arguments='{"x-queue-type":"quorum"}' 2>&1 || true

echo ""
echo "============================================"
echo " ✅ 仲裁队列已创建"
echo ""
echo " 队列列表:"
echo "   quorum.order.process   (订单处理)"
echo "   quorum.payment.result  (支付结果)"
echo ""
echo " 验证 Raft 成员:"
echo "   docker exec rabbitmq-node1 rabbitmq-queues members quorum.order.process"
echo "============================================"
