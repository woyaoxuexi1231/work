#!/usr/bin/env bash
# MySQL 8.1 | Port: 3306 | Password: 123456
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

C="mysql"; I="mysql:${MYSQL_VERSION:-8.1}"; P="${MYSQL_PORT:-3306}"; PASS="${MYSQL_ROOT_PASSWORD:-123456}"

check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

pull_image "${I}"
docker run -d --name "${C}" --restart=always -p ${P}:3306 \
  -e "MYSQL_ROOT_PASSWORD=${PASS}" -e TZ=Asia/Shanghai "${I}"
wait_for_container "${C}" 60; sleep 5

for i in $(seq 1 30); do
  docker exec "${C}" mysqladmin ping -h localhost --silent 2>/dev/null && \
    docker exec "${C}" mysql -uroot -p"${PASS}" -e "CREATE DATABASE IF NOT EXISTS test CHARACTER SET utf8mb4;" 2>/dev/null && break
  sleep 2
done

done_banner "MySQL"; log_info "Port: ${P}  Pass: ${PASS}  mysql -h 127.0.0.1 -P ${P} -u root -p${PASS}"
