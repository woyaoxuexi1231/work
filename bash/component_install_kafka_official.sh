#!/usr/bin/env bash
# Kafka 4.1.1 (KRaft) | Port: 9092, 9093 | apache/kafka
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

CONTAINER="kafka"
IMAGE="apache/kafka:${KAFKA_VERSION:-4.1.1}"
PORT="${KAFKA_PORT:-9092}"
HOST_IP="${KAFKA_HOST_IP:-192.168.3.100}"

require_root
check_docker
check_container_exists "${CONTAINER}" && exit 0
cleanup_container "${CONTAINER}"

pull_image "${IMAGE}"
docker run -d --name "${CONTAINER}" --restart=unless-stopped \
  -p ${PORT}:9092 -p 9093:9093 \
  -e TZ=Asia/Shanghai \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
  -e "KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://${HOST_IP}:9092" \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  "${IMAGE}"

wait_for_container "${CONTAINER}" 60

# verify
for i in $(seq 1 30); do
  if docker exec "${CONTAINER}" /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092 >/dev/null 2>&1; then
    log_info "Kafka broker ready"
    break
  fi
  sleep 2
done

done_banner "Kafka"
log_info "Bootstrap: ${HOST_IP}:${PORT}"
log_info "Topics: docker exec ${CONTAINER} /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092"
