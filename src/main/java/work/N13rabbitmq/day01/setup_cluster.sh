#!/bin/bash

echo "🚀 Starting RabbitMQ Cluster Setup (Day 01)..."

# 1. 启动所有容器
docker-compose up -d

# 等待容器启动 (至少 10s 让 rabbit1 准备好)
echo "⏳ Waiting for containers to be ready (10s)..."
sleep 10

# 2. 将 rabbit2 加入集群 (RAM 节点)
echo "🔗 Joining rabbit2 to rabbit1 (RAM node)..."
docker exec rabbit2 rabbitmqctl stop_app
docker exec rabbit2 rabbitmqctl join_cluster --ram rabbit@rabbit1
docker exec rabbit2 rabbitmqctl start_app

# 3. 将 rabbit3 加入集群 (RAM 节点)
echo "🔗 Joining rabbit3 to rabbit1 (RAM node)..."
docker exec rabbit3 rabbitmqctl stop_app
docker exec rabbit3 rabbitmqctl join_cluster --ram rabbit@rabbit1
docker exec rabbit3 rabbitmqctl start_app

# 4. 显示集群状态
echo "📊 Cluster Setup Finished! Final status:"
docker exec rabbit1 rabbitmqctl cluster_status

echo "✅ All nodes joined! Visit http://localhost:15672 (guest/guest) to verify."
