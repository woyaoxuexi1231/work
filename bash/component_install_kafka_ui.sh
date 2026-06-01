#!/usr/bin/env bash
# Kafka UI | Port: 9097 | provectuslabs/kafka-ui
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

CONTAINER="kafka-ui"
IMAGE="provectuslabs/kafka-ui:${UI_VERSION:-latest}"
PORT="${UI_PORT:-9097}"
BROKERS="${KAFKA_BOOTSTRAP_SERVERS:-192.168.3.100:9092}"

require_root
check_docker
check_container_exists "${CONTAINER}" && exit 0
cleanup_container "${CONTAINER}"

pull_image "${IMAGE}"
docker run -d --name "${CONTAINER}" --restart=unless-stopped \
  -p ${PORT}:8080 \
  -e TZ=Asia/Shanghai \
  -e KAFKA_CLUSTERS_0_NAME=local \
  -e "KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=${BROKERS}" \
  -e KAFKA_CLUSTERS_0_READONLY=false \
  "${IMAGE}"

wait_for_container "${CONTAINER}" 30

done_banner "Kafka UI"
log_info "URL: http://127.0.0.1:${PORT}"
