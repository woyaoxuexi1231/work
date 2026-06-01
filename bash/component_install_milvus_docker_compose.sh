#!/usr/bin/env bash
# Milvus 2.6.7 (docker compose) | Ports: 19530, 9091, 3000 | Data: /root/milvus-compose
# 依赖外部 MinIO (默认 127.0.0.1:9000)
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

DATA="${MILVUS_DATA_ROOT:-/root/milvus-compose}"
PROJECT="${MILVUS_COMPOSE_PROJECT:-milvus}"
MILVUS_VER="${MILVUS_VERSION:-v2.6.7}"
PORT="${MILVUS_PORT:-19530}"
ATTU_PORT="${ATTU_PORT:-3000}"
MINIO_HOST="${MINIO_ADDRESS:-192.168.3.100}:${MINIO_PORT:-9000}"

require_root
check_docker

# check docker compose
if docker compose version >/dev/null 2>&1; then  DCC="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then DCC="docker-compose"
else log_error "docker-compose 未安装"; exit 1
fi

ensure_dir "${DATA}/volumes/milvus" "${DATA}/volumes/etcd"

cat > "${DATA}/docker-compose.yml" <<EOF
version: '3.5'
services:
  etcd:
    container_name: ${PROJECT}-etcd
    image: quay.io/coreos/etcd:v3.5.25
    restart: unless-stopped
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
    volumes:
      - ${DATA}/volumes/etcd:/etcd
    command: etcd -advertise-client-urls=http://etcd:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd
    healthcheck:
      test: ["CMD","etcdctl","endpoint","health"]
      interval: 30s; timeout: 20s; retries: 3

  standalone:
    container_name: ${PROJECT}-standalone
    image: milvusdb/milvus:${MILVUS_VER}
    restart: unless-stopped
    command: ["milvus","run","standalone"]
    security_opt: [seccomp:unconfined]
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: ${MINIO_HOST}
      MQ_TYPE: woodpecker
    volumes:
      - ${DATA}/volumes/milvus:/var/lib/milvus
    ports:
      - "${PORT}:19530"
      - "9091:9091"
    depends_on: [etcd]

  attu:
    container_name: ${PROJECT}-attu
    image: zilliz/attu:${ATTU_VERSION:-v2.3.4}
    restart: unless-stopped
    environment:
      MILVUS_URL: standalone:19530
    ports:
      - "${ATTU_PORT}:3000"
    depends_on: [standalone]

networks:
  default:
    name: ${PROJECT}
EOF

cd "${DATA}"
${DCC} up -d 2>&1 | head -10
log_info "等待 Milvus 启动（首次需拉取镜像，可能较慢）..."

for i in $(seq 1 90); do
  if docker exec "${PROJECT}-standalone" curl -sf http://localhost:9091/healthz >/dev/null 2>&1; then
    log_info "Milvus ready"
    break
  fi
  sleep 2
done

done_banner "Milvus"
log_info "API: 127.0.0.1:${PORT}  Attu: http://127.0.0.1:${ATTU_PORT}"
log_info "Compose dir: ${DATA}  停止: cd ${DATA} && ${DCC} down"
