#!/usr/bin/env bash
# ============================================================================
# 05-shovel-setup.sh —— 启用 Shovel 插件并配置搬运通道
# 前置：01-cluster-setup.sh 已运行
# 用法：sudo bash 05-shovel-setup.sh
#
# 原理：Shovel 是一个专用 AMQP 客户端，从源 Queue 消费消息，
#       发布到目标 Exchange。分区时消息留在源队列，恢复后继续搬运。
#       ack-mode: on-confirm → 目标确认后才 ack 源，端到端不丢。
# ============================================================================
set -euo pipefail

echo "============================================"
echo " Shovel 插件配置"
echo "============================================"

# ---------- 1. 检查集群 ----------
OUT=$(docker exec rabbitmq-node1 rabbitmqctl cluster_status 2>&1 || true)
if ! echo "${OUT}" | grep -q "rabbit@rabbitmq-node"; then
  echo "✗ 集群未就绪，请先运行 01-cluster-setup.sh"
  exit 1
fi
echo "✓ 集群状态正常"

# ---------- 2. 启用 Shovel 插件 ----------
echo ""
echo "[1/3] 启用 shovel 插件..."

for node in rabbitmq-node1 rabbitmq-node2 rabbitmq-node3; do
  echo -n "  ${node} ... "
  docker exec ${node} rabbitmq-plugins enable rabbitmq_shovel 2>&1 | grep -q "started" && echo "✓" || echo "已启用"
  docker exec ${node} rabbitmq-plugins enable rabbitmq_shovel_management 2>&1 | grep -q "started" && echo "  (management) ✓" || true
done

# ---------- 3. 准备 vhost + 源队列 ----------
echo ""
echo "[2/3] 准备源端和目标端..."

# 复用 Federation 创建的 vhost，或新建
docker exec rabbitmq-node1 rabbitmqctl add_vhost upstream 2>&1 || true
docker exec rabbitmq-node1 rabbitmqctl add_vhost downstream 2>&1 || true
docker exec rabbitmq-node1 rabbitmqctl set_permissions -p upstream admin ".*" ".*" ".*" 2>&1 || true
docker exec rabbitmq-node1 rabbitmqctl set_permissions -p downstream admin ".*" ".*" ".*" 2>&1 || true

# 在上游创建源队列
docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 -V upstream \
  declare queue name=orders.source durable=true 2>&1 || true

echo "  ✓ orders.source 已创建（upstream vhost）"

# ---------- 4. 配置 Shovel ----------
echo ""
echo "[3/3] 配置 Shovel 搬运通道..."

# Shovel: 从 upstream 的 orders.source → downstream 的 ex.order
# ack-mode: on-confirm → 目标确认后才 ack 源（端到端可靠）
docker exec rabbitmq-node1 rabbitmqctl set_parameter -p upstream \
  shovel orders-shovel \
  '{
    "src-uri": "amqp://admin:admin123@rabbitmq-node1:5672/upstream",
    "src-queue": "orders.source",
    "dest-uri": "amqp://admin:admin123@rabbitmq-node1:5672/downstream",
    "dest-exchange": "ex.order",
    "ack-mode": "on-confirm",
    "reconnect-delay": 5
  }' 2>&1

echo ""
echo "============================================"
echo " ✅ Shovel 搬运通道已配置"
echo ""
echo " 工作方式:"
echo "   orders.source (upstream) → ex.order (downstream)"
echo "   ack-mode: on-confirm → 目标 confirm 后才 ack 源"
echo "   reconnect-delay: 5s → 断连后每 5 秒重试"
echo ""
echo " 验证:"
echo "   管理界面 → Admin → Shovel Status"
echo "   http://192.168.3.100:15672/#/shovels"
echo ""
echo " 测试:"
echo "   1. 往 upstream 的 orders.source 发消息"
echo "   2. 在 downstream 绑定队列到 ex.order 并消费"
echo "   3. 消息自动从源搬运到目标"
echo "============================================"
