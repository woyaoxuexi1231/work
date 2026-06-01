#!/usr/bin/env bash
# Milvus 2.6.7 (standalone) | Port: 19530, 9091, 3000(Attu)
# 使用内置 etcd + local MinIO，零外部依赖
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

C="milvus-standalone"; MI="milvusdb/milvus:${MILVUS_VERSION:-v2.6.7}"
P="${MILVUS_PORT:-19530}"; ATTU_P="${ATTU_PORT:-3000}"

check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

pull_image "${MI}"
docker run -d --name "${C}" --restart=unless-stopped \
  -p ${P}:19530 -p 9091:9091 \
  -e ETCD_USE_EMBED=true \
  -e ETCD_DATA_DIR=/var/lib/milvus/etcd \
  -e COMMON_STORAGETYPE=local \
  "${MI}" milvus run standalone
wait_for_container "${C}" 90

# Attu
if ! check_container_exists "milvus-attu"; then
  cleanup_container "milvus-attu"
  pull_image "zilliz/attu:${ATTU_VERSION:-v2.3.4}"
  docker run -d --name milvus-attu --restart=unless-stopped \
    -p ${ATTU_P}:3000 \
    -e "MILVUS_URL=${C}:19530" \
    zilliz/attu:${ATTU_VERSION:-v2.3.4} 2>/dev/null || log_warn "Attu 跳过"
fi

for i in $(seq 1 45); do
  docker exec "${C}" curl -sf http://localhost:9091/healthz >/dev/null 2>&1 && { log_info "milvus ready"; break; }
  sleep 2
done

done_banner "Milvus"; log_info "API: localhost:${P}  Attu: http://localhost:${ATTU_P}"
