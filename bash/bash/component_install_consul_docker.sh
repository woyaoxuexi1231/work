#!/usr/bin/env bash
# Consul 1.15.4 | Port: 8500, 8600
# export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="consul"; I="consul:${CONSUL_VERSION:-1.15.4}"
HP="${CONSUL_HTTP_PORT:-8500}"; DP="${CONSUL_DNS_PORT:-8600}"
DATA="${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}/consul-data"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

# ==== 数据目录 ====
mkdir -p "${DATA}/data" || { log_error "无法创建 ${DATA}"; exit 1; }

# ==== 拉取镜像 ====
pull_image "${I}"

# ==== 启动容器 ====
docker run -d --name "${C}" --restart=always -e TZ=Asia/Shanghai \
  -p ${HP}:8500 -p ${DP}:8600/tcp -p ${DP}:8600/udp \
  -v "${DATA}/data:/consul/data" \
  "${I}" agent -dev -client=0.0.0.0 -bind=0.0.0.0 -ui
wait_for_container "${C}" 15

# ==== 完成 ====
done_banner "Consul | UI: http://localhost:${HP}/ui | Data: ${DATA}"
