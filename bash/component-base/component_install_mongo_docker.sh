#!/usr/bin/env bash
# MongoDB 8.0 | Port: 27017 | Data: /root/mongo | admin/admin123
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

CONTAINER="mongo"
IMAGE="mongo:${MONGO_VERSION:-8.0}"
DATA="${MONGO_DATA_ROOT:-/root/mongo}"
PORT="${MONGO_PORT:-27017}"
MONGO_USER="${MONGO_ROOT_USER:-admin}"
MONGO_PASS="${MONGO_ROOT_PASSWORD:-admin123}"

require_root
check_docker
check_container_exists "${CONTAINER}" && exit 0
cleanup_container "${CONTAINER}"

ensure_dir "${DATA}/conf" "${DATA}/data" "${DATA}/log" "${DATA}/init"

cat > "${DATA}/conf/mongod.conf" <<EOF
storage:
  dbPath: /data/db
systemLog:
  logAppend: true
net:
  port: 27017
  bindIp: 0.0.0.0
processManagement:
  fork: false
operationProfiling:
  slowOpThresholdMs: 100
  mode: slowOp
EOF

pull_image "${IMAGE}"
docker run -d --name "${CONTAINER}" --restart=always --privileged=true \
  -p ${PORT}:27017 \
  -e TZ=Asia/Shanghai \
  -e "MONGO_INITDB_ROOT_USERNAME=${MONGO_USER}" \
  -e "MONGO_INITDB_ROOT_PASSWORD=${MONGO_PASS}" \
  -e "MONGO_INITDB_DATABASE=admin" \
  -v "${DATA}/conf/mongod.conf:/etc/mongod.conf" \
  -v "${DATA}/data:/data/db" \
  -v "${DATA}/log:/var/log/mongodb" \
  "${IMAGE}" --config /etc/mongod.conf

wait_for_container "${CONTAINER}" 60
sleep 5

done_banner "MongoDB"
log_info "Port: ${PORT}  User: ${MONGO_USER}  Pass: ${MONGO_PASS}"
log_info "URI: mongodb://${MONGO_USER}:${MONGO_PASS}@127.0.0.1:${PORT}/admin"
