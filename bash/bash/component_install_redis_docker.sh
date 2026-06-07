#!/usr/bin/env bash
# Redis 7.2 | Port: 6379 | Pass: 123456
# export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="redis"; I="redis:${REDIS_VERSION:-7.2}"; P="${REDIS_PORT:-6379}"
PASS="${REDIS_PASSWORD:-123456}"
DATA="${DOCKER_DATA_ROOT:-/c/Users/code/Desktop/docker-data}/redis-data"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

# ==== 数据目录 ====
mkdir -p "${DATA}/data" || { log_error "无法创建 ${DATA}"; exit 1; }

# ==== 拉取镜像 ====
pull_image "${I}"

# ==== 启动容器 ====
docker run -d --name "${C}" --restart=always -p ${P}:6379 -e TZ=Asia/Shanghai \
  -v "${DATA}/data:/data" \
  "${I}" redis-server --requirepass "${PASS}" --appendonly yes
wait_for_container "${C}" 30

# ==== 验证 ====
for i in $(seq 1 15); do
  docker exec "${C}" redis-cli -a "${PASS}" ping 2>/dev/null | grep -q PONG && break
  sleep 1
done

# ==== 完成 ====
done_banner "Redis | Port: ${P} | Pass: ${PASS} | Data: ${DATA}"
