#!/usr/bin/env bash
# ============================================================================
# 02-mirrored-queue.sh —— 配置所有镜像队列策略（覆盖 Q3/Q4/Q5）
# 前置：01-cluster-setup.sh 已运行
# 用法：sudo bash 02-mirrored-queue.sh
# ============================================================================
set -euo pipefail

echo "============================================"
echo " 镜像队列策略配置（覆盖 Q3 / Q4 / Q5）"
echo "============================================"

# ---------- 检查集群 ----------
OUT=$(docker exec rabbitmq-node1 rabbitmqctl cluster_status 2>&1 || true)
if ! echo "${OUT}" | grep -q "rabbit@rabbitmq-node"; then
  echo "✗ 集群未就绪，请先运行 01-cluster-setup.sh"
  exit 1
fi
echo "✓ 集群状态正常"
echo ""

# ---------- 策略 1: Q3 用 —— 以 mirrored. 开头 → 一主一从 ----------
echo "[1/4] Q3: ^mirrored\\. → exactly:2"
docker exec rabbitmq-node1 rabbitmqctl clear_policy mirrored-all 2>&1 || true
docker exec rabbitmq-node1 rabbitmqctl set_policy \
  mirrored-all "^mirrored\\." \
  '{"ha-mode":"exactly","ha-params":2,"ha-sync-mode":"automatic"}' \
  --priority 1 --apply-to queues 2>&1

# ---------- 策略 2: Q4 用 —— 以 q4.all. 开头 → 全节点镜像（反面教材） ----------
echo "[2/4] Q4: ^q4\\.all\\. → ha-mode:all（写放大 3x）"
docker exec rabbitmq-node1 rabbitmqctl clear_policy q4-all 2>&1 || true
docker exec rabbitmq-node1 rabbitmqctl set_policy \
  q4-all "^q4\\.all\\." \
  '{"ha-mode":"all","ha-sync-mode":"automatic"}' \
  --priority 1 --apply-to queues 2>&1

# ---------- 策略 3: Q4 用 —— 以 q4.ex2. 开头 → 一主一从 ----------
echo "[3/4] Q4: ^q4\\.ex2\\. → exactly:2"
docker exec rabbitmq-node1 rabbitmqctl clear_policy q4-ex2 2>&1 || true
docker exec rabbitmq-node1 rabbitmqctl set_policy \
  q4-ex2 "^q4\\.ex2\\." \
  '{"ha-mode":"exactly","ha-params":2,"ha-sync-mode":"automatic"}' \
  --priority 1 --apply-to queues 2>&1

# ---------- 策略 4: Q5 用 —— 以 q5.mirrored. 开头 → 一主一从 ----------
echo "[4/4] Q5: ^q5\\.mirrored\\. → exactly:2"
docker exec rabbitmq-node1 rabbitmqctl clear_policy q5-mirrored 2>&1 || true
docker exec rabbitmq-node1 rabbitmqctl set_policy \
  q5-mirrored "^q5\\.mirrored\\." \
  '{"ha-mode":"exactly","ha-params":2,"ha-sync-mode":"automatic"}' \
  --priority 1 --apply-to queues 2>&1

echo ""
echo "============================================"
echo " ✅ 4 条镜像策略全部配置"
echo ""
echo " 策略覆盖:"
echo "   ^mirrored\\.      → exactly:2  (Q3)"
echo "   ^q4\\.all\\.      → all        (Q4 反面)"
echo "   ^q4\\.ex2\\.      → exactly:2  (Q4 推荐)"
echo "   ^q5\\.mirrored\\. → exactly:2  (Q5 对比)"
echo ""
echo " 验证: docker exec rabbitmq-node1 rabbitmqctl list_policies"
echo "============================================"
