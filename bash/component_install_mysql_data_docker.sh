#!/usr/bin/env bash
# MySQL 8.1 (挂载版) | Port: 3306 | Password: 123456 | Data: /root/mysql
# 此脚本挂载配置文件和数据目录到宿主机，容器删除后数据不丢失
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

C="mysql"; I="mysql:${MYSQL_VERSION:-8.1}"; P="${MYSQL_PORT:-3306}"
PASS="${MYSQL_ROOT_PASSWORD:-123456}"
# 数据根目录: C:\Users\15434\Desktop\docker-data\<组件名>
DOCKER_DATA="${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}"
DATA="${DOCKER_DATA}/mysql-data"

check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

# 创建数据目录
mkdir -p "${DATA}/conf" "${DATA}/data" "${DATA}/log" 2>/dev/null || {
  log_warn "无法创建 ${DATA}，回退到当前目录"; DATA="./mysql-data"; mkdir -p "${DATA}/conf" "${DATA}/data" "${DATA}/log"
}

# 配置文件
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

pull_image "${I}"
docker run -d --name "${C}" --restart=always -p ${P}:3306 \
  -e "MYSQL_ROOT_PASSWORD=${PASS}" \
  -e TZ=Asia/Shanghai \
  -v "${DATA}/conf/my.cnf:/etc/mysql/my.cnf" \
  -v "${DATA}/data:/var/lib/mysql" \
  -v "${DATA}/log:/var/log/mysql" \
  "${I}"
wait_for_container "${C}" 60

for i in $(seq 1 30); do
  docker exec "${C}" mysqladmin ping -h localhost --silent 2>/dev/null && \
    docker exec "${C}" mysql -uroot -p"${PASS}" -e "CREATE DATABASE IF NOT EXISTS test CHARACTER SET utf8mb4;" 2>/dev/null && break
  sleep 2
done

done_banner "MySQL (挂载版)"
log_info "Port: ${P}  Pass: ${PASS}  数据目录: ${DATA}"
log_info "mysql -h 127.0.0.1 -P ${P} -u root -p${PASS}"
