#!/bin/bash

echo "=========================================================="
echo "      RabbitMQ Cluster Health Check Tool (Day 01)        "
echo "=========================================================="

# 检查容器状态
echo -e "\n[1/3] Checking Docker Container Status..."
nodes=("rabbit1" "rabbit2" "rabbit3")
for node in "${nodes[@]}"; do
    status=$(docker inspect -f '{{.State.Running}}' "$node" 2>/dev/null)
    if [ "$status" == "true" ]; then
        echo "✅ Node $node is RUNNING"
    else
        echo "❌ Node $node is STOPPED or MISSING"
    fi
done

# 检查集群状态 (从 rabbit1 获取)
echo -e "\n[2/3] Fetching Cluster Status from rabbit1..."
cluster_info=$(docker exec rabbit1 rabbitmqctl cluster_status 2>/dev/null)
if [ $? -eq 0 ]; then
    echo "✅ Cluster is healthy. Running nodes detected."
else
    echo "❌ Failed to fetch cluster status. Is rabbit1 ready?"
    exit 1
fi

# 输出节点类型报告 (Disk vs RAM)
echo -e "\n[3/3] Node Type Report:"
echo "----------------------------------------------------------"
# 使用 rabbitmqctl cluster_status 的输出解析节点类型
# 在 RabbitMQ 3.x 中，可以通过 'Nodes' 列表和 'Disk Nodes' 列表来区分
disk_nodes=$(echo "$cluster_info" | grep -A 10 "Disk Nodes" | grep "@" | sed 's/[[:space:]]//g' | tr -d '[]' | tr ',' '\n')
running_nodes=$(echo "$cluster_info" | grep -A 10 "Running Nodes" | grep "@" | sed 's/[[:space:]]//g' | tr -d '[]' | tr ',' '\n')

for node in $running_nodes; do
    is_disk=false
    for disk_node in $disk_nodes; do
        if [ "$node" == "$disk_node" ]; then
            is_disk=true
            break
        fi
    done

    if [ "$is_disk" == "true" ]; then
        echo "🔹 Node: $node | Type: DISK"
    else
        echo "🔸 Node: $node | Type: RAM"
    fi
done
echo "----------------------------------------------------------"
echo "Check Complete! 🚀"
