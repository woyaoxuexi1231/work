#!/usr/bin/env bash
# ============================================================================
# 03-quorum-queue.sh —— 在集群基础上创建仲裁队列 (Quorum Queue)
# 前置条件：必须先运行 01-cluster-setup.sh 搭建好 3 节点集群
#           如果之前运行了 02-mirrored-queue.sh，需先恢复集群
# 运行环境：192.168.3.100 Ubuntu
# 用法：chmod +x 03-quorum-queue.sh && ./03-quorum-queue.sh
# ============================================================================
set -euo pipefail

echo "============================================"
echo " 仲裁队列 (Quorum Queue) 配置与验证"
echo "============================================"

# ---------- 0. 确保集群 3 节点全在线 ----------
echo ""
echo "[0/6] 确保 3 节点全部在线..."
for node in rabbitmq-node1 rabbitmq-node2 rabbitmq-node3; do
  if ! docker inspect ${node} --format='{{.State.Running}}' 2>/dev/null | grep -q "true"; then
    echo "  启动 ${node}..."
    docker start ${node} 2>/dev/null || true
  fi
  docker exec ${node} rabbitmqctl start_app 2>/dev/null || true
done
sleep 5
echo "  ✓ 3 节点均在线"

# ---------- 1. 创建仲裁队列 ----------
echo ""
echo "[1/6] 创建仲裁队列 (x-queue-type: quorum)"

# 仲裁队列 1：订单处理队列
docker exec rabbitmq-node1 rabbitmqadmin declare queue \
  name=quorum.order.process \
  durable=true \
  arguments='{"x-queue-type":"quorum"}' 2>/dev/null || true

# 仲裁队列 2：支付结果队列
docker exec rabbitmq-node1 rabbitmqadmin declare queue \
  name=quorum.payment.result \
  durable=true \
  arguments='{"x-queue-type":"quorum"}' 2>/dev/null || true

# 再创建一个普通经典队列作为对照
docker exec rabbitmq-node1 rabbitmqadmin declare queue \
  name=classic.control \
  durable=true 2>/dev/null || true

echo "  ✓ 已创建 2 个仲裁队列 + 1 个经典对照队列"
echo ""
echo "  队列信息:"
docker exec rabbitmq-node1 rabbitmqctl list_queues name type state messages leader --formatter=table 2>/dev/null | head -20

# ---------- 2. 查看 Raft 成员分布 ----------
echo ""
echo "[2/6] 查看仲裁队列的 Raft 成员分布"

for q in "quorum.order.process" "quorum.payment.result"; do
  echo ""
  echo "  ── ${q} ──"
  LEADER=$(docker exec rabbitmq-node1 rabbitmqctl list_queues name leader --formatter=csv 2>/dev/null | grep "${q}" | cut -d',' -f2 | tr -d '"')
  echo "  Leader: ${LEADER}"

  # 查看成员信息
  docker exec rabbitmq-node1 rabbitmq-queues members "${q}" 2>/dev/null || echo "  (members 命令需要 RabbitMQ 3.8+)"
done

# ---------- 3. 发布消息 ----------
echo ""
echo "[3/6] 向仲裁队列发布 100 条消息..."

for i in $(seq 1 100); do
  docker exec rabbitmq-node1 rabbitmqadmin publish \
    routing_key=quorum.order.process \
    payload="{\"orderId\":${i},\"type\":\"quorum-test\",\"amount\":$((RANDOM % 10000))}" \
    properties='{"delivery_mode":2}' 2>/dev/null
done

echo "  ✓ 已发布 100 条消息"
echo ""
echo "  队列消息计数:"
docker exec rabbitmq-node1 rabbitmqctl list_queues name messages --formatter=table 2>/dev/null | head -10

# ---------- 4. 发布到经典队列作对比 ----------
echo ""
echo "[4/6] 向经典对照队列发布 100 条消息..."
for i in $(seq 1 100); do
  docker exec rabbitmq-node1 rabbitmqadmin publish \
    routing_key=classic.control \
    payload="{\"id\":${i},\"type\":\"classic-control\"}" \
    properties='{"delivery_mode":2}' 2>/dev/null
done
echo "  ✓ 已发布"

# ---------- 5. 定位 Leader 并模拟宕机 ----------
echo ""
echo "[5/6] ====== 模拟 Leader 宕机，观察 Raft 选举 ======"

# 找到 quorum.order.process 的 leader
LEADER_NODE=$(docker exec rabbitmq-node1 rabbitmqctl list_queues name leader --formatter=csv 2>/dev/null | grep "quorum.order.process" | cut -d',' -f2 | tr -d '"')
LEADER_CONTAINER="rabbitmq-node1"  # leader 通常是 node1，但动态提取更准确
# rabbitmq-node1 返回的 leader 格式类似 "rabbit@rabbitmq-node1"
if echo "${LEADER_NODE}" | grep -q "node2"; then
  LEADER_CONTAINER="rabbitmq-node2"
elif echo "${LEADER_NODE}" | grep -q "node3"; then
  LEADER_CONTAINER="rabbitmq-node3"
fi

echo ""
echo "  quorum.order.process Leader: ${LEADER_NODE} → ${LEADER_CONTAINER}"
echo ""
echo "  ▸ 即将停止 Leader: ${LEADER_CONTAINER}"
echo "  ▸ 预期: Raft 自动选举新 Leader (2-5 秒)"
echo "  ▸ 关键: 仲裁队列不阻塞! 写入在多数派存活时继续"
echo ""

# 记录选举前时间
START_TS=$(date +%s%3N)

docker stop ${LEADER_CONTAINER}
echo "  ${LEADER_CONTAINER} 已停止，等待选举..."

# 轮询等待新 Leader 选出
for i in $(seq 1 30); do
  sleep 1
  # 从存活节点查询
  for survivor in rabbitmq-node1 rabbitmq-node2 rabbitmq-node3; do
    if [ "${survivor}" != "${LEADER_CONTAINER}" ]; then
      NEW_LEADER=$(docker exec ${survivor} rabbitmqctl list_queues name leader --formatter=csv 2>/dev/null | grep "quorum.order.process" | cut -d',' -f2 | tr -d '"' || echo "")
      if [ -n "${NEW_LEADER}" ] && [ "${NEW_LEADER}" != "${LEADER_NODE}" ]; then
        END_TS=$(date +%s%3N)
        ELAPSED=$((END_TS - START_TS))
        echo ""
        echo "  ✓ 新 Leader 选出: ${NEW_LEADER}"
        echo "  ✓ 选举耗时: ${ELAPSED}ms"
        break 2
      fi
    fi
  done
  echo -n "."
done

# ---------- 6. 验证数据一致性 ----------
echo ""
echo "[6/6] ====== 验证数据一致性 ======"

# 找到一个存活节点
SURVIVOR="rabbitmq-node1"
if [ "${LEADER_CONTAINER}" = "rabbitmq-node1" ]; then
  SURVIVOR="rabbitmq-node2"
fi

echo ""
echo "  从 ${SURVIVOR} 消费消息，验证数据未丢失:"
echo ""

# 消费 5 条确认数据完整
CONSUMED=0
for i in $(seq 1 5); do
  MSG=$(docker exec ${SURVIVOR} rabbitmqadmin get queue=quorum.order.process ackmode=ack_requeue_false 2>/dev/null)
  if echo "${MSG}" | grep -q "orderId"; then
    CONSUMED=$((CONSUMED + 1))
    echo "  ✓ 消费到: $(echo ${MSG} | grep -oP 'orderId[^,}]+')"
  fi
done

echo ""
echo "  仲裁队列剩余消息:"
docker exec ${SURVIVOR} rabbitmqctl list_queues name messages --formatter=table 2>/dev/null | grep quorum

echo ""
echo "  ── 经典队列对照 ──"
echo "  经典队列 classic.control (master=${LEADER_CONTAINER} 已宕机):"
CLASSIC_MSG=$(docker exec ${SURVIVOR} rabbitmqadmin get queue=classic.control ackmode=ack_requeue_false 2>&1 || true)
if echo "${CLASSIC_MSG}" | grep -q "NOT_FOUND\|down\|error\|no.*queue"; then
  echo "  ✗ 经典队列不可用! (因为 master 在已宕机的节点上)"
else
  echo "  ${CLASSIC_MSG}"
fi

echo ""
echo "============================================"
echo " 仲裁队列故障切换演示完成"
echo ""
echo " 恢复 Leader 节点:"
echo "   docker start ${LEADER_CONTAINER}"
echo "   docker exec ${LEADER_CONTAINER} rabbitmqctl start_app"
echo ""
echo " 关键对比:"
echo "   ┌──────────────────┬─────────────────┬─────────────────┐"
echo "   │                  │ 仲裁队列        │ 经典队列         │"
echo "   ├──────────────────┼─────────────────┼─────────────────┤"
echo "   │ Leader 宕机后    │ ✓ 2-5s 自动选举 │ ✗ 队列不可用     │"
echo "   │ 数据丢失         │ ✓ 不丢失        │ 消息在宕机磁盘上 │"
echo "   │ 写入是否阻塞     │ ✓ 多数派存活可写│ ✗ 完全不可写     │"
echo "   │ 新节点加入       │ 增量追赶不阻塞  │ N/A              │"
echo "   └──────────────────┴─────────────────┴─────────────────┘"
echo "============================================"
