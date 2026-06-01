#!/usr/bin/env bash
# Nacos 2.3.2 | Port: 8848, 9848, 9849 | Data: /root/nacos | nacos/nacos
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

CONTAINER="nacos"
IMAGE="nacos/nacos-server:${NACOS_VERSION:-v2.3.2}"
DATA="${NACOS_DATA_ROOT:-/root/nacos}"
PORT="${NACOS_PORT:-8848}"
GRPC_PORT=$((PORT + 1000))
GRPC_SRV=$((PORT + 1001))
NACOS_MODE="${NACOS_MODE:-standalone}"

require_root
check_docker
check_container_exists "${CONTAINER}" && exit 0
cleanup_container "${CONTAINER}"

ensure_dir "${DATA}/logs" "${DATA}/data"

pull_image "${IMAGE}"
docker run -d --name "${CONTAINER}" --restart=always \
  -p ${PORT}:8848 -p ${GRPC_PORT}:9848 -p ${GRPC_SRV}:9849 \
  -e MODE="${NACOS_MODE}" \
  -e PREFER_HOST_MODE=hostname \
  -e NACOS_AUTH_ENABLE=false \
  -v "${DATA}/logs:/home/nacos/logs" \
  -v "${DATA}/data:/home/nacos/data" \
  "${IMAGE}"

wait_for_container "${CONTAINER}" 60

done_banner "Nacos"
log_info "Console: http://127.0.0.1:${PORT}/nacos  User: nacos  Pass: nacos"
log_info "gRPC: ${GRPC_PORT}  gRPC-Server: ${GRPC_SRV}"
