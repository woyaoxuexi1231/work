#!/usr/bin/env bash
# ZooKeeper 3.9 | Port: 2181
export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="zookeeper"; I="zookeeper:${ZK_VERSION:-3.9}"; P="${ZK_PORT:-2181}"
DATA="${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}/zk-data"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

# ==== 数据目录 ====
mkdir -p "${DATA}/data" "${DATA}/log" || { log_error "无法创建 ${DATA}"; exit 1; }

# ==== 拉取镜像 ====
pull_image "${I}"

# ==== 启动容器 ====
docker run -d --name "${C}" --restart=always -p ${P}:2181 -e TZ=Asia/Shanghai \
  -v "${DATA}/data:/data" -v "${DATA}/log:/logs" \
  "${I}"
wait_for_container "${C}" 30

# ==== 完成 ====
done_banner "ZooKeeper | Port: ${P} | Data: ${DATA}"
