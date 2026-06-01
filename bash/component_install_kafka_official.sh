#!/usr/bin/env bash
# Kafka 4.1.1 (KRaft) | Port: 9092 | apache/kafka
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

C="kafka"; I="apache/kafka:${KAFKA_VERSION:-4.1.1}"; P="${KAFKA_PORT:-9092}"; H="${KAFKA_HOST_IP:-127.0.0.1}"

check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

pull_image "${I}"
docker run -d --name "${C}" --restart=unless-stopped -p ${P}:9092 -e TZ=Asia/Shanghai \
  -e KAFKA_NODE_ID=1 -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
  -e "KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092" \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 "${I}"
wait_for_container "${C}" 60

for i in $(seq 1 30); do
  docker exec "${C}" /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092 >/dev/null 2>&1 && { log_info "kafka ready"; break; }
  sleep 2
done

done_banner "Kafka"; log_info "Bootstrap: localhost:${P}"
