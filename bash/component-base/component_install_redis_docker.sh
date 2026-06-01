#!/usr/bin/env bash
# Redis 7.2 | Port: 6379 | Data: /root/redis | Password: 123456
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

CONTAINER="redis"
IMAGE="redis:${REDIS_VERSION:-7.2}"
DATA="${REDIS_DATA_ROOT:-/root/redis}"
PORT="${REDIS_PORT:-6379}"
PASS="${REDIS_PASSWORD:-123456}"

require_root
check_docker
check_container_exists "${CONTAINER}" && exit 0
cleanup_container "${CONTAINER}"

ensure_dir "${DATA}/conf" "${DATA}/data" "${DATA}/log"

# config
cat > "${DATA}/conf/redis.conf" <<EOF
bind 0.0.0.0
port 6379
protected-mode no
loglevel notice
logfile ""
dir /data
requirepass ${PASS}
appendonly yes
appendfsync everysec
save 900 1
save 300 10
save 60 10000
EOF

chmod 777 "${DATA}/data"

pull_image "${IMAGE}"
docker run -d --name "${CONTAINER}" --restart=always --privileged=true \
  -p ${PORT}:6379 \
  -e TZ=Asia/Shanghai \
  -v "${DATA}/conf/redis.conf:/usr/local/etc/redis/redis.conf" \
  -v "${DATA}/data:/data" \
  "${IMAGE}" redis-server /usr/local/etc/redis/redis.conf

wait_for_container "${CONTAINER}" 30
sleep 3

# verify
for i in $(seq 1 15); do
  if docker exec "${CONTAINER}" redis-cli -a "${PASS}" ping 2>/dev/null | grep -q PONG; then
    log_info "Redis PONG ok"
    break
  fi
  sleep 1
done

done_banner "Redis"
log_info "Port: ${PORT}  Pass: ${PASS}  redis-cli -h 127.0.0.1 -p ${PORT} -a ${PASS}"
