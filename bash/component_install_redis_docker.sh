#!/usr/bin/env bash
# Redis 7.2 | Port: 6379 | Password: 123456
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

C="redis"; I="redis:${REDIS_VERSION:-7.2}"; P="${REDIS_PORT:-6379}"; PASS="${REDIS_PASSWORD:-123456}"

check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

pull_image "${I}"
docker run -d --name "${C}" --restart=always -p ${P}:6379 -e TZ=Asia/Shanghai \
  "${I}" redis-server --requirepass "${PASS}" --appendonly yes
wait_for_container "${C}" 30

for i in $(seq 1 15); do
  docker exec "${C}" redis-cli -a "${PASS}" ping 2>/dev/null | grep -q PONG && log_info "redis PONG ok" && break
  sleep 1
done

done_banner "Redis"; log_info "Port: ${P}  Pass: ${PASS}  redis-cli -h 127.0.0.1 -p ${P} -a ${PASS}"
