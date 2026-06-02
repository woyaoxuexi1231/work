#!/usr/bin/env bash
# ============================================================================
# 05-shovel-setup.sh —— Shovel 插件：从零搭建到验证
# 前置：01-cluster-setup.sh 已运行
# 用法：sudo bash 05-shovel-setup.sh
#
# 场景模拟：用两个 vhost 模拟"两个独立集群"
#   upstream   → 源端（订单系统所在，队列 orders.source）
#   downstream → 目标端（仓储系统所在，Exchange ex.order）
#   Shovel    → 精确搬运：从源 Queue 消费 → 发布到目标 Exchange
# ============================================================================
set -euo pipefail

echo "============================================"
echo " Shovel 插件 —— 完整部署"
echo "============================================"
echo ""
echo " 场景: upstream/orders.source → downstream/ex.order"
echo " 机制: 专用 AMQP 客户端，从源 Queue 消费，发布到目标 Exchange"
echo " 分区时: 连接断开 → 消息留在源队列(未 ack) → 恢复后继续搬运"
echo ""

# ========== 步骤1: 启用插件 ==========
echo "━━━ 步骤1: 启用 Shovel 插件 ━━━"
for node in rabbitmq-node1 rabbitmq-node2 rabbitmq-node3; do
  echo -n "  ${node}: "
  docker exec ${node} rabbitmq-plugins enable rabbitmq_shovel 2>&1 | grep -q "started" && echo "✓" || echo "已启用"
  docker exec ${node} rabbitmq-plugins enable rabbitmq_shovel_management 2>&1 | grep -q "started" && echo "    management ✓" || true
done

# ========== 步骤2: 准备 vhost + 源队列 + 目标 Exchange ==========
echo ""
echo "━━━ 步骤2: 准备源端和目标端 ━━━"

# vhost
docker exec rabbitmq-node1 rabbitmqctl add_vhost upstream 2>&1 || true
docker exec rabbitmq-node1 rabbitmqctl add_vhost downstream 2>&1 || true
docker exec rabbitmq-node1 rabbitmqctl set_permissions -p upstream admin ".*" ".*" ".*" 2>&1 || true
docker exec rabbitmq-node1 rabbitmqctl set_permissions -p downstream admin ".*" ".*" ".*" 2>&1 || true
echo "  ✓ vhost: upstream / downstream"

# 源队列（上游）
docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 -V upstream \
  declare queue name=orders.source durable=true 2>&1 || true
echo "  ✓ 源队列: upstream/orders.source"
echo ""
echo "  【原理验证点1】源队列只是一个普通队列，不需要任何特殊配置。"
echo "  Shovel 就是一个特殊的消费者，从它里面取消息。"

# 目标 Exchange（下游）
docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 -V downstream \
  declare exchange name=ex.order type=topic durable=true 2>&1 || true
echo "  ✓ 目标 Exchange: downstream/ex.order"

# ========== 步骤3: 创建目标队列并绑定 ==========
echo ""
echo "━━━ 步骤3: 创建目标队列（让消息有处可去）━━━"

docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 -V downstream \
  declare queue name=warehouse.inbound durable=true 2>&1 || true

docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 -V downstream \
  declare binding source=ex.order destination=warehouse.inbound \
  routing_key=order.new 2>&1 || true

echo "  ✓ downstream/warehouse.inbound 已绑定到 ex.order (routing_key=order.new)"

# ========== 步骤4: 配置 Shovel ==========
echo ""
echo "━━━ 步骤4: 配置 Shovel 搬运参数 ━━━"

docker exec rabbitmq-node1 rabbitmqctl set_parameter -p upstream \
  shovel orders-shovel \
  '{
    "src-uri": "amqp://admin:admin123@rabbitmq-node1:5672/upstream",
    "src-queue": "orders.source",
    "dest-uri": "amqp://admin:admin123@rabbitmq-node1:5672/downstream",
    "dest-exchange": "ex.order",
    "dest-exchange-key": "order.new",
    "ack-mode": "on-confirm",
    "reconnect-delay": 5
  }' 2>&1

echo "  ✓ Shovel 已配置"
echo ""
echo "  【原理验证点2】ack-mode: on-confirm = 端到端可靠传递。"
echo "  Shovel 只有等目标 Exchange 返回 confirm 后，才向源队列 ack。"
echo "  如果目标不可达 → 消息留在源队列(unacked) → 不会丢。"
echo ""
echo "  【原理验证点3】reconnect-delay: 5s = 专线断开后每 5 秒重试。"
echo "  重试期间消息在源队列堆积，恢复后 Shovel 继续搬运。"

echo ""
echo "============================================"
echo " ✅ Shovel 搬运通道部署完成"
echo ""
echo " 测试方法:"
echo "   curl http://localhost:8080/shovel/test"
echo ""
echo " 搬运链路:"
echo "   upstream/orders.source ──(Shovel, on-confirm)──→ downstream/ex.order → warehouse.inbound"
echo ""
echo " 关键特性:"
echo "   • 精确: 绑定到特定 Queue → Exchange"
echo "   • 可靠: on-confirm 模式端到端不丢"
echo "   • 堆积: 目标不可达 → 源队列堆积 → 恢复后继续搬运"
echo "   • 可控: 可为每条链路独立配置 ack-mode / reconnect-delay"
echo "============================================"
