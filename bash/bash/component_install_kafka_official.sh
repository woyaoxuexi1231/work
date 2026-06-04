#!/usr/bin/env bash
# Kafka 4.1.1 (KRaft) | Port: 9092
# export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="kafka"; I="apache/kafka:${KAFKA_VERSION:-4.1.1}"; P="${KAFKA_PORT:-9092}"
DATA="${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}/kafka-data"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

# ==== 数据目录 ====
mkdir -p "${DATA}" || { log_error "无法创建 ${DATA}"; exit 1; }

# ==== 拉取镜像 ====
pull_image "${I}"

# ==== 启动容器 ====
docker run -d --name "${C}" --restart=unless-stopped -p ${P}:9092 -e TZ=Asia/Shanghai \
  -e KAFKA_NODE_ID=1 -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -v "${DATA}:/var/lib/kafka/data" \
  "${I}"
wait_for_container "${C}" 60

# ==== 验证 ====
for i in $(seq 1 30); do
  docker exec "${C}" /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092 >/dev/null 2>&1 && break
  sleep 2
done

# ==== 完成 ====
done_banner "Kafka | Bootstrap: localhost:${P} | Data: ${DATA}"
