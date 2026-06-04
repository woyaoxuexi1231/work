#!/usr/bin/env bash
# Milvus 2.6.7 (standalone) | Port: 19530, 3000(Attu)
# export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="milvus-standalone"; MI="milvusdb/milvus:${MILVUS_VERSION:-v2.6.7}"
P="${MILVUS_PORT:-19530}"; ATTU_P="${ATTU_PORT:-3000}"
DATA="${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}/milvus-data"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

# ==== 数据目录 ====
mkdir -p "${DATA}" || { log_error "无法创建 ${DATA}"; exit 1; }

# ==== 拉取镜像 ====
pull_image "${MI}"

# ==== 启动 Milvus ====
docker run -d --name "${C}" --restart=unless-stopped -p ${P}:19530 -p 9091:9091 -e TZ=Asia/Shanghai \
  -e ETCD_USE_EMBED=true -e ETCD_DATA_DIR=/var/lib/milvus/etcd -e COMMON_STORAGETYPE=local \
  -v "${DATA}:/var/lib/milvus" \
  "${MI}" milvus run standalone
wait_for_container "${C}" 90

# ==== Attu 管理界面 ====
if ! check_container_exists "milvus-attu"; then
  cleanup_container "milvus-attu"
  pull_image "zilliz/attu:${ATTU_VERSION:-v2.3.4}" 2>/dev/null || true
  docker run -d --name milvus-attu --restart=unless-stopped -p ${ATTU_P}:3000 \
    -e "MILVUS_URL=${C}:19530" zilliz/attu:${ATTU_VERSION:-v2.3.4} 2>/dev/null || log_warn "Attu 跳过"
fi

# ==== 验证 ====
for i in $(seq 1 45); do
  docker exec "${C}" curl -sf http://localhost:9091/healthz >/dev/null 2>&1 && break
  sleep 2
done

# ==== 完成 ====
done_banner "Milvus | API: localhost:${P} | Attu: http://localhost:${ATTU_P} | Data: ${DATA}"
