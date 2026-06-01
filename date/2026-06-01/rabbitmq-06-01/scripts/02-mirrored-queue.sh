#!/usr/bin/env bash
# ============================================================================
# 02-mirrored-queue.sh —— 配置镜像队列策略
# 前置：01-cluster-setup.sh 已运行
# 用法：sudo bash 02-mirrored-queue.sh
# ============================================================================
set -euo pipefail

echo "============================================"
echo " 镜像队列策略配置"
echo "============================================"

# ---------- 1. 检查集群 ----------
OUT=$(docker exec rabbitmq-node1 rabbitmqctl cluster_status 2>&1 || true)
if ! echo "${OUT}" | grep -q "rabbit@rabbitmq-node"; then
  echo "✗ 集群未就绪，请先运行 01-cluster-setup.sh"
  exit 1
fi
echo "✓ 集群状态正常"

# ---------- 2. 设置镜像策略 ----------
echo ""
echo "配置策略: ha-mode=exactly, ha-params=2 (一主一从)"
echo "匹配队列: 名称以 'mirrored.' 开头"

docker exec rabbitmq-node1 rabbitmqctl clear_policy mirrored-all 2>&1 || true
docker exec rabbitmq-node1 rabbitmqctl set_policy \
  mirrored-all \
  "^mirrored\\." \
  '{"ha-mode":"exactly","ha-params":2,"ha-sync-mode":"automatic"}' \
  --priority 1 \
  --apply-to queues 2>&1

echo ""
echo "============================================"
echo " ✅ 镜像策略已配置"
echo ""
echo " 使用方式: 声明名称以 'mirrored.' 开头的队列即可自动镜像"
echo " 示例: mirrored.order.pending → 自动一主一从"
echo ""
echo " 验证: docker exec rabbitmq-node1 rabbitmqctl list_policies"
echo "============================================"
