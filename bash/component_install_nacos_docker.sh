#!/usr/bin/env bash
# Nacos 2.3.2 | Port: 8848, 9848, 9849 | nacos/nacos
export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="nacos"; I="nacos/nacos-server:${NACOS_VERSION:-v2.3.2}"; P="${NACOS_PORT:-8848}"
G1=$((P+1000)); G2=$((P+1001))
DATA="${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}/nacos-data"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

# ==== 数据目录 ====
mkdir -p "${DATA}/logs" "${DATA}/data" || { log_error "无法创建 ${DATA}"; exit 1; }

# ==== 拉取镜像 ====
pull_image "${I}"

# ==== 启动容器 ====
docker run -d --name "${C}" --restart=always -p ${P}:8848 -p ${G1}:9848 -p ${G2}:9849 \
  -e MODE="${NACOS_MODE:-standalone}" -e NACOS_AUTH_ENABLE=false -e TZ=Asia/Shanghai \
  -v "${DATA}/logs:/home/nacos/logs" -v "${DATA}/data:/home/nacos/data" \
  "${I}"
wait_for_container "${C}" 60

# ==== 完成 ====
done_banner "Nacos | Console: http://localhost:${P}/nacos | nacos/nacos | Data: ${DATA}"
