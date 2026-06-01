#!/usr/bin/env bash
# Kafka UI | Port: 9097 | provectuslabs/kafka-ui
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

C="kafka-ui"; I="provectuslabs/kafka-ui:${UI_VERSION:-latest}"; P="${UI_PORT:-9097}"; B="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"

check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

pull_image "${I}"
docker run -d --name "${C}" --restart=unless-stopped -p ${P}:8080 -e TZ=Asia/Shanghai \
  -e KAFKA_CLUSTERS_0_NAME=local -e "KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=${B}" \
  -e KAFKA_CLUSTERS_0_READONLY=false "${I}"
wait_for_container "${C}" 30

done_banner "Kafka UI"; log_info "URL: http://localhost:${P}"
