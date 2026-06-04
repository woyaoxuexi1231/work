#!/usr/bin/env bash
# MongoDB 8.0 | Port: 27017 | admin/admin123
export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="mongo"; I="mongo:${MONGO_VERSION:-8.0}"; P="${MONGO_PORT:-27017}"
U="${MONGO_ROOT_USER:-admin}"; PASS="${MONGO_ROOT_PASSWORD:-admin123}"
DATA="${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}/mongo-data"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

# ==== 数据目录 ====
mkdir -p "${DATA}/data" "${DATA}/log" || { log_error "无法创建 ${DATA}"; exit 1; }

# ==== 拉取镜像 ====
pull_image "${I}"

# ==== 启动容器 ====
docker run -d --name "${C}" --restart=always -p ${P}:27017 -e TZ=Asia/Shanghai \
  -e "MONGO_INITDB_ROOT_USERNAME=${U}" -e "MONGO_INITDB_ROOT_PASSWORD=${PASS}" \
  -v "${DATA}/data:/data/db" -v "${DATA}/log:/var/log/mongodb" \
  "${I}"
wait_for_container "${C}" 60

# ==== 完成 ====
done_banner "MongoDB | Port: ${P} | ${U}/${PASS} | Data: ${DATA}"
