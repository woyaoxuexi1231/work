#!/usr/bin/env bash
# MinIO | Port: 9000(API), 9001(Console) | Data: /root/minio | minioadmin/minioadmin
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

CONTAINER="minio"
IMAGE="minio/minio:${MINIO_VERSION:-latest}"
DATA="${MINIO_DATA_ROOT:-/root/minio}"
API_PORT="${MINIO_API_PORT:-9000}"
CONSOLE_PORT="${MINIO_CONSOLE_PORT:-9001}"
MINIO_USER="${MINIO_ROOT_USER:-minioadmin}"
MINIO_PASS="${MINIO_ROOT_PASSWORD:-minioadmin}"

require_root
check_docker
check_container_exists "${CONTAINER}" && exit 0
cleanup_container "${CONTAINER}"

ensure_dir "${DATA}/data"

pull_image "${IMAGE}"
docker run -d --name "${CONTAINER}" --restart=always \
  -p ${API_PORT}:9000 -p ${CONSOLE_PORT}:9001 \
  -e "MINIO_ROOT_USER=${MINIO_USER}" \
  -e "MINIO_ROOT_PASSWORD=${MINIO_PASS}" \
  -v "${DATA}/data:/data" \
  "${IMAGE}" server /data --console-address ":9001"

wait_for_container "${CONTAINER}" 30

done_banner "MinIO"
log_info "API: http://127.0.0.1:${API_PORT}  Console: http://127.0.0.1:${CONSOLE_PORT}"
log_info "User: ${MINIO_USER}  Pass: ${MINIO_PASS}"
