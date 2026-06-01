#!/usr/bin/env bash
# MinIO | Port: 9000(API), 9001(Console) | minioadmin/minioadmin
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

C="minio"; I="minio/minio:${MINIO_VERSION:-latest}"; AP="${MINIO_API_PORT:-9000}"; CP="${MINIO_CONSOLE_PORT:-9001}"
U="${MINIO_ROOT_USER:-minioadmin}"; PASS="${MINIO_ROOT_PASSWORD:-minioadmin}"

check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

pull_image "${I}"
docker run -d --name "${C}" --restart=always -p ${AP}:9000 -p ${CP}:9001 \
  -e "MINIO_ROOT_USER=${U}" -e "MINIO_ROOT_PASSWORD=${PASS}" \
  "${I}" server /data --console-address ":9001"
wait_for_container "${C}" 20

done_banner "MinIO"; log_info "API: http://127.0.0.1:${AP}  Console: http://127.0.0.1:${CP}"
log_info "User: ${U}  Pass: ${PASS}"
