#!/usr/bin/env bash
# Elasticsearch 8.11 | Port: 9200, 9300
# export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="elasticsearch"; I="elasticsearch:${ES_VERSION:-8.11.0}"
P="${ES_PORT:-9200}"; T="${ES_TRANSPORT_PORT:-9300}"
DATA="${DOCKER_DATA_ROOT:-/c/Users/code/Desktop/docker-data}/es-data"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

# ==== 数据目录 ====
mkdir -p "${DATA}/data" "${DATA}/log" || { log_error "无法创建 ${DATA}"; exit 1; }

# ==== 内核参数 (ES 需要) ====
[[ "$(sysctl -n vm.max_map_count 2>/dev/null || echo 0)" -lt 262144 ]] && sysctl -w vm.max_map_count=262144

# ==== 拉取镜像 ====
pull_image "${I}"

# ==== 启动容器 ====
docker run -d --name "${C}" --restart=always -p ${P}:9200 -p ${T}:9300 \
  -e "discovery.type=single-node" -e "xpack.security.enabled=false" \
  -e "ES_JAVA_OPTS=${ES_JAVA_OPTS:--Xms512m -Xmx512m}" -e TZ=Asia/Shanghai \
  -v "${DATA}/data:/usr/share/elasticsearch/data" -v "${DATA}/log:/usr/share/elasticsearch/logs" \
  "${I}"
wait_for_container "${C}" 90

# ==== 完成 ====
done_banner "Elasticsearch | Port: ${P} | Data: ${DATA} | curl http://localhost:${P}"
