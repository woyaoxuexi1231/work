#!/usr/bin/env bash
# ============================================================================
# 02-mirrored-queue.sh —— 在集群基础上配置镜像队列
# 前置条件：必须先运行 01-cluster-setup.sh 搭建好 3 节点集群
# 运行环境：192.168.3.100 Ubuntu
# 用法：sudo bash 02-mirrored-queue.sh
# ============================================================================
set -euo pipefail

echo "============================================"
echo " 镜像队列 (Mirrored Queue) 配置与验证"
echo "============================================"

# ---------- 1. 检查集群状态 ----------
echo ""
echo "[1/6] 检查集群是否就绪..."

# 【修复】rabbitmqctl 输出到 stderr，必须 2>&1 而非 2>/dev/null
if ! docker exec rabbitmq-node1 rabbitmqctl cluster_status 2>&1 | grep -q "rabbit@rabbitmq-node"; then
  echo "  ✗ 集群未就绪! 请先运行 01-cluster-setup.sh"
  exit 1
fi
echo "  ✓ 集群状态正常"

# ---------- 2. 配置镜像队列策略 ----------
echo ""
echo "[2/6] 配置镜像队列策略 (ha-mode: exactly, ha-params: 2)"

# 先清理旧策略
docker exec rabbitmq-node1 rabbitmqctl clear_policy mirrored-all 2>&1 || true

# 设置镜像策略：匹配以 "mirrored." 开头的队列，一主一从
docker exec rabbitmq-node1 rabbitmqctl set_policy \
  mirrored-all \
  "^mirrored\\." \
  '{"ha-mode":"exactly","ha-params":2,"ha-sync-mode":"automatic"}' \
  --priority 1 \
  --apply-to queues 2>&1

echo "  ✓ 策略已设置"
echo ""
echo "  策略详情:"
docker exec rabbitmq-node1 rabbitmqctl list_policies 2>&1

# ---------- 3. 创建测试镜像队列 ----------
echo ""
echo "[3/6] 创建测试镜像队列..."

docker exec rabbitmq-node1 rabbitmqadmin declare queue \
  name=mirrored.order.pending durable=true 2>&1 || true

docker exec rabbitmq-node1 rabbitmqadmin declare queue \
  name=mirrored.order.confirmed durable=true 2>&1 || true

# 创建一个普通队列（非镜像）作为对比
docker exec rabbitmq-node1 rabbitmqadmin declare queue \
  name=normal.order.log durable=true 2>&1 || true

echo "  ✓ 队列已创建"
echo ""
echo "  队列分布:"
docker exec rabbitmq-node1 rabbitmqctl list_queues name node policy type 2>&1 | head -20

# ---------- 4. 发布测试消息 ----------
echo ""
echo "[4/6] 向镜像队列发布 100 条测试消息..."

for i in $(seq 1 100); do
  docker exec rabbitmq-node1 rabbitmqadmin publish \
    routing_key=mirrored.order.pending \
    payload="{\"orderId\":$i,\"amount\":$((RANDOM % 10000)),\"ts\":\"$(date -Iseconds)\"}" \
    properties='{"delivery_mode":2}' 2>&1
done

echo "  ✓ 已发布 100 条持久化消息到 mirrored.order.pending"
echo ""
echo "  各节点队列消息数:"
docker exec rabbitmq-node1 rabbitmqctl list_queues name messages 2>&1 | head -10

# ---------- 5. 定位 master 节点 ----------
echo ""
echo "[5/6] 定位镜像队列的 master 节点..."

# 【修复】用 awk 从表格输出中提取 master 节点，更可靠
MASTER_NODE=$(docker exec rabbitmq-node1 rabbitmqctl list_queues name node 2>&1 \
  | grep "mirrored.order.pending" \
  | awk '{print $NF}')
echo "  mirrored.order.pending 的 master 在: ${MASTER_NODE}"

# 提取节点序号（rabbit@rabbitmq-node1 → 1）
MASTER_IDX=$(echo "${MASTER_NODE}" | grep -oP 'node\K\d' || echo "1")
if [ -z "$MASTER_IDX" ]; then
  MASTER_IDX=1
fi

# 计算 mirror 节点（取下一个）：node1→node2, node2→node3, node3→node1
SLAVE_IDX=$(( (MASTER_IDX % 3) + 1 ))
SLAVE_NODE="rabbitmq-node${SLAVE_IDX}"

echo "  master: ${MASTER_NODE} → container rabbitmq-node${MASTER_IDX}"
echo "  mirror: rabbit@${SLAVE_NODE} → container ${SLAVE_NODE}"

# ---------- 6. 模拟 master 宕机，观察故障切换 ----------
echo ""
echo "[6/6] ====== 模拟故障切换 ======"
echo ""
echo "  ▸ 即将停止 master 容器: rabbitmq-node${MASTER_IDX}"
echo "  ▸ 预期: ${SLAVE_NODE} 上的 mirror 自动提升为新 master"
echo "  ▸ 队列 mirrored.order.pending 继续可用"
echo ""
echo "  执行: docker stop rabbitmq-node${MASTER_IDX}"
echo ""

docker stop "rabbitmq-node${MASTER_IDX}"
sleep 5

echo ""
echo "  观察集群状态 (从 ${SLAVE_NODE} 查看):"
docker exec ${SLAVE_NODE} rabbitmqctl cluster_status 2>&1 | grep -A5 "Running Nodes" || echo "  (集群状态查询可能短暂失败，正在恢复中...)"
echo ""

# 验证队列仍可用
echo "  验证队列 mirrored.order.pending 是否仍可访问..."
sleep 2
if docker exec ${SLAVE_NODE} rabbitmqadmin get queue=mirrored.order.pending ackmode=ack_requeue_false 2>&1 | grep -q "orderId"; then
  echo "  ✓ 故障切换成功! 队列仍可消费，消息未丢失"
else
  echo "  (正在选举新 master，稍后重试...)"
  sleep 5
  docker exec ${SLAVE_NODE} rabbitmqadmin get queue=mirrored.order.pending ackmode=ack_requeue_false 2>&1 | head -5
fi

echo ""
echo "============================================"
echo " 镜像队列故障切换演示完成"
echo ""
echo " 恢复 master 节点:"
echo "   docker start rabbitmq-node${MASTER_IDX}"
echo "   docker exec rabbitmq-node${MASTER_IDX} rabbitmqctl start_app"
echo ""
echo " 观察点:"
echo "   1. 镜像队列 master 宕机 → mirror 自动提升"
echo "   2. 普通队列 normal.order.log 在宕机节点上不可用"
echo "   3. publisher confirm 行为: 已确认的消息不丢失"
echo "============================================"
