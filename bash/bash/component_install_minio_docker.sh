#!/usr/bin/env bash
# MinIO | Port: 9000(API), 9001(Console) | minioadmin/minioadmin
# export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="minio"; I="minio/minio:${MINIO_VERSION:-latest}"
AP="${MINIO_API_PORT:-9000}"; CP="${MINIO_CONSOLE_PORT:-9001}"
U="${MINIO_ROOT_USER:-minioadmin}"; PASS="${MINIO_ROOT_PASSWORD:-minioadmin}"
DATA="${DOCKER_DATA_ROOT:-/c/Users/code/Desktop/docker-data}/minio-data"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

# ==== 数据目录 ====
mkdir -p "${DATA}/data" || { log_error "无法创建 ${DATA}"; exit 1; }

# ==== 拉取镜像 ====
pull_image "${I}"

# ==== 启动容器 ====
docker run -d --name "${C}" --restart=always -p ${AP}:9000 -p ${CP}:9001 \
  -e "MINIO_ROOT_USER=${U}" -e "MINIO_ROOT_PASSWORD=${PASS}" -e TZ=Asia/Shanghai \
  -v "${DATA}/data:/data" \
  "${I}" server /data --console-address ":9001"
wait_for_container "${C}" 20

# ==== 完成 ====
done_banner "MinIO | API: ${AP} | Console: ${CP} | ${U}/${PASS} | Data: ${DATA}"
