#!/usr/bin/env bash
# MySQL 8.1 | Port: 3306 | Data: /root/mysql | Password: 123456
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

CONTAINER="mysql"
IMAGE="mysql:${MYSQL_VERSION:-8.1}"
DATA="${MYSQL_DATA_ROOT:-/root/mysql}"
PORT="${MYSQL_PORT:-3306}"
PASS="${MYSQL_ROOT_PASSWORD:-123456}"

require_root
check_docker
check_container_exists "${CONTAINER}" && exit 0
cleanup_container "${CONTAINER}"

ensure_dir "${DATA}/conf" "${DATA}/data" "${DATA}/log" "${DATA}/binlog" "${DATA}/mysql-files"

if [[ ! -f "${DATA}/conf/my.cnf" ]]; then
  cat > "${DATA}/conf/my.cnf" <<'CNF'
[client]
default-character-set=utf8mb4
[mysql]
default-character-set=utf8mb4
[mysqld]
user=mysql
port=3306
character-set-server=utf8mb4
collation_server=utf8mb4_bin
max_connections=4000
default-storage-engine=InnoDB
innodb_file_per_table=true
CNF
fi

chmod 777 "${DATA}/data" "${DATA}/log"

pull_image "${IMAGE}"
docker run -d --name "${CONTAINER}" --restart=always --privileged=true \
  -p ${PORT}:3306 \
  -e "MYSQL_ROOT_PASSWORD=${PASS}" \
  -e TZ=Asia/Shanghai \
  -v "${DATA}/conf/my.cnf:/etc/mysql/my.cnf" \
  -v "${DATA}/data:/var/lib/mysql" \
  -v "${DATA}/log:/var/log/mysql" \
  -v "${DATA}/mysql-files:/var/lib/mysql-files" \
  "${IMAGE}"

wait_for_container "${CONTAINER}" 60

for i in $(seq 1 30); do
  if docker exec "${CONTAINER}" mysqladmin ping -h localhost --silent 2>/dev/null; then
    docker exec "${CONTAINER}" mysql -uroot -p"${PASS}" \
      -e "CREATE DATABASE IF NOT EXISTS test CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;" 2>/dev/null && break
  fi
  sleep 2
done

done_banner "MySQL"
log_info "Port: ${PORT}  Password: ${PASS}  mysql -h 127.0.0.1 -P ${PORT} -u root -p${PASS}"
