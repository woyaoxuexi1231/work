#!/usr/bin/env bash
# ============================================================================
# 04-federation-setup.sh —— 启用 Federation 插件并配置联邦
# 前置：01-cluster-setup.sh 已运行
# 用法：sudo bash 04-federation-setup.sh
#
# 原理：Federation 在 Exchange 级别建立单向消息链路。
#       下游 Exchange 透明地镜像上游 Exchange 的消息，
#       分区时消息堆积在下游，恢复后自动 drain。
# ============================================================================
set -euo pipefail

echo "============================================"
echo " Federation 联邦插件配置"
echo "============================================"

# ---------- 1. 检查集群 ----------
OUT=$(docker exec rabbitmq-node1 rabbitmqctl cluster_status 2>&1 || true)
if ! echo "${OUT}" | grep -q "rabbit@rabbitmq-node"; then
  echo "✗ 集群未就绪，请先运行 01-cluster-setup.sh"
  exit 1
fi
echo "✓ 集群状态正常"

# ---------- 2. 启用 Federation 插件（3 个节点） ----------
echo ""
echo "[1/4] 启用 federation 插件..."

for node in rabbitmq-node1 rabbitmq-node2 rabbitmq-node3; do
  echo -n "  ${node} ... "
  docker exec ${node} rabbitmq-plugins enable rabbitmq_federation 2>&1 | grep -q "started" && echo "✓" || echo "已启用"
  docker exec ${node} rabbitmq-plugins enable rabbitmq_federation_management 2>&1 | grep -q "started" && echo "  (management) ✓" || true
done

# ---------- 3. 创建两个 vhost 模拟"上游"和"下游" ----------
echo ""
echo "[2/4] 创建 vhost（模拟两个独立集群）..."

docker exec rabbitmq-node1 rabbitmqctl add_vhost upstream 2>&1 || true
docker exec rabbitmq-node1 rabbitmqctl add_vhost downstream 2>&1 || true
docker exec rabbitmq-node1 rabbitmqctl set_permissions -p upstream admin ".*" ".*" ".*" 2>&1
docker exec rabbitmq-node1 rabbitmqctl set_permissions -p downstream admin ".*" ".*" ".*" 2>&1
echo "  ✓ upstream / downstream 已创建"

# ---------- 4. 配置 Federation ----------
echo ""
echo "[3/4] 配置 Federation 上游..."

# 定义上游：下游连接回本集群的 upstream vhost
docker exec rabbitmq-node1 rabbitmqctl set_parameter -p downstream \
  federation-upstream fed-up \
  '{"uri":"amqp://admin:admin123@rabbitmq-node1:5672/upstream","expires":3600000}' 2>&1

echo "  ✓ 上游 fed-up 已定义（downstream → upstream）"

# 设置策略：下游中名称以 fed. 开头的 Exchange 自动联邦
echo ""
echo "[4/4] 设置联邦策略..."

docker exec rabbitmq-node1 rabbitmqctl set_policy -p downstream \
  federate-fed-ex \
  '^fed\.' \
  '{"federation-upstream":"fed-up"}' \
  --priority 1 --apply-to exchanges 2>&1

echo ""
echo "============================================"
echo " ✅ Federation 联邦已配置"
echo ""
echo " 工作方式:"
echo "   下游(downstream vhost)中名为 fed.* 的 Exchange"
echo "   → 自动从上游(upstream vhost)同名 Exchange 拉取消息"
echo ""
echo " 验证:"
echo "   管理界面 → Federation 标签 → Status"
echo "   http://192.168.3.100:15672/#/federation"
echo ""
echo " 测试:"
echo "   1. 在 upstream vhost 创建 Exchange: fed.test"
echo "   2. 在 downstream vhost 创建同名 Exchange: fed.test"
echo "   3. 往 upstream 的 fed.test 发消息"
echo "   4. 在 downstream 的 fed.test 消费 → 消息自动到达"
echo "============================================"
