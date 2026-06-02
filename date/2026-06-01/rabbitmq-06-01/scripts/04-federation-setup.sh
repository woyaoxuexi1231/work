#!/usr/bin/env bash
# ============================================================================
# 04-federation-setup.sh —— Federation 联邦插件：从零搭建到验证
# 前置：01-cluster-setup.sh 已运行
# 用法：sudo bash 04-federation-setup.sh
#
# 场景模拟：用两个 vhost 模拟"两个独立集群"
#   upstream   → 上游机房（订单系统所在）
#   downstream → 下游机房（仓储系统所在）
#   Federation → 下游自动获得上游 Exchange 的消息副本
# ============================================================================
set -euo pipefail

echo "============================================"
echo " Federation 联邦插件 —— 完整部署"
echo "============================================"
echo ""
echo " 场景: upstream(上游机房) → downstream(下游机房)"
echo " 机制: 下游 Exchange 透明镜像上游 Exchange 的消息"
echo " 分区时: 连接断开 → 消息堆积上游 → 恢复后自动 drain"
echo ""

# ========== 步骤1: 检查集群 + 启用插件 ==========
echo "━━━ 步骤1: 启用 Federation 插件 ━━━"
for node in rabbitmq-node1 rabbitmq-node2 rabbitmq-node3; do
  echo -n "  ${node}: "
  docker exec ${node} rabbitmq-plugins enable rabbitmq_federation 2>&1 | grep -q "started" && echo "✓" || echo "已启用"
  docker exec ${node} rabbitmq-plugins enable rabbitmq_federation_management 2>&1 | grep -q "started" && echo "    management ✓" || true
done

# ========== 步骤2: 创建两个 vhost 模拟两个集群 ==========
echo ""
echo "━━━ 步骤2: 创建 vhost 模拟上游/下游集群 ━━━"
echo "  upstream   = 上游机房（订单系统）"
echo "  downstream = 下游机房（仓储系统）"
echo ""

docker exec rabbitmq-node1 rabbitmqctl add_vhost upstream 2>&1 || true
docker exec rabbitmq-node1 rabbitmqctl add_vhost downstream 2>&1 || true
docker exec rabbitmq-node1 rabbitmqctl set_permissions -p upstream admin ".*" ".*" ".*" 2>&1
docker exec rabbitmq-node1 rabbitmqctl set_permissions -p downstream admin ".*" ".*" ".*" 2>&1
echo "  ✓ upstream / downstream 已创建，admin 有全部权限"

# ========== 步骤3: 在上游创建 Exchange ==========
echo ""
echo "━━━ 步骤3: 在上游创建 Exchange ━━━"

docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 -V upstream \
  declare exchange name=fed.order type=topic durable=true 2>&1 || true

echo "  ✓ upstream: fed.order (topic Exchange) 已创建"
echo ""
echo "  【原理验证点1】上游不需要任何特殊配置。"
echo "  上游的人往 fed.order 正常发消息，完全不知道 Federation 的存在。"

# ========== 步骤4: 配置 Federation 上游 ==========
echo ""
echo "━━━ 步骤4: 定义 Federation 上游（下游 → 上游的连接信息）━━━"

docker exec rabbitmq-node1 rabbitmqctl set_parameter -p downstream \
  federation-upstream fed-up \
  '{"uri":"amqp://admin:admin123@rabbitmq-node1:5672/upstream","expires":3600000}' \
  2>&1

echo "  ✓ 上游 fed-up 已定义"
echo "  连接: downstream → rabbitmq-node1:5672/upstream"
echo ""
echo "  【原理验证点2】这个 URI 就是 Federation 插件建立 AMQP 连接的地址。"
echo "  WAN 断开时，这个连接断开，Federation 自动重连（默认 5 秒）。"

# ========== 步骤5: 设置联邦策略 ==========
echo ""
echo "━━━ 步骤5: 设置联邦策略（哪些 Exchange 要镜像）━━━"

docker exec rabbitmq-node1 rabbitmqctl set_policy -p downstream \
  federate-fed-ex \
  '^fed\.' \
  '{"federation-upstream":"fed-up"}' \
  --priority 1 --apply-to exchanges 2>&1

echo "  ✓ 策略已设置: downstream 中名为 fed.* 的 Exchange 自动联邦"
echo ""
echo "  【原理验证点3】下游只需创建同名 Exchange（fed.order），"
echo "  Federation 插件自动在上游创建内部队列、拉取消息、发布到下游。"
echo "  下游消费者完全无感知 —— 它只是从本地的 fed.order 消费。"

# ========== 步骤6: 在下游创建同名 Exchange（触发联邦） ==========
echo ""
echo "━━━ 步骤6: 在下游创建同名 Exchange（触发联邦链路）━━━"

docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 -V downstream \
  declare exchange name=fed.order type=topic durable=true 2>&1 || true

echo "  ✓ downstream: fed.order (topic Exchange) 已创建"
echo ""
echo "  去管理界面 → Federation → Status 查看链路状态:"
echo "  http://192.168.3.100:15672/#/federation"

echo ""
echo "============================================"
echo " ✅ Federation 联邦部署完成"
echo ""
echo " 测试方法:"
echo "   curl http://localhost:8080/federation/test"
echo ""
echo " 链路结构:"
echo "   upstream/fed.order ──(Federation 拉取)──→ downstream/fed.order"
echo ""
echo " 关键特性:"
echo "   • 单向: upstream → downstream"
echo "   • 异步: 不要求两端同时在线"
echo "   • 堆积: 链路断开 → 消息在上游内部队列堆积 → 恢复后自动 drain"
echo "   • 透明: 上下游各自操作本地 Exchange，不知道对方存在"
echo "============================================"
