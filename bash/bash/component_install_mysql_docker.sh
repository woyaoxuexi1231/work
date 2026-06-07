#!/usr/bin/env bash
# MySQL 8.1 | Port: 3306 | Pass: 123456
# export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="mysql"; I="mysql:${MYSQL_VERSION:-8.1}"; P="${MYSQL_PORT:-3306}"
PASS="${MYSQL_ROOT_PASSWORD:-123456}"
DATA="${DOCKER_DATA_ROOT:-/c/Users/code/Desktop/docker-data}/mysql-data"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

# ==== 数据目录 ====
mkdir -p "${DATA}/conf" "${DATA}/data" "${DATA}/log" || {
  log_error "无法创建 ${DATA}，检查 Docker Desktop 文件共享设置"; exit 1
}

# ==== 配置文件 ====
[[ -f "${DATA}/conf/my.cnf" ]] || cat > "${DATA}/conf/my.cnf" <<'CNF'
[client] default-character-set=utf8mb4
[mysql] default-character-set=utf8mb4
[mysqld] user=mysql; port=3306; character-set-server=utf8mb4; collation_server=utf8mb4_bin; max_connections=4000; default-storage-engine=InnoDB; innodb_file_per_table=true
CNF

# ==== 拉取镜像 ====
pull_image "${I}"

# ==== 启动容器 ====
docker run -d --name "${C}" --restart=always -p ${P}:3306 \
  -e "MYSQL_ROOT_PASSWORD=${PASS}" -e TZ=Asia/Shanghai \
  -v "${DATA}/conf/my.cnf:/etc/mysql/my.cnf" \
  -v "${DATA}/data:/var/lib/mysql" -v "${DATA}/log:/var/log/mysql" \
  "${I}"
wait_for_container "${C}" 60

# ==== 验证 ====
for i in $(seq 1 30); do
  docker exec "${C}" mysqladmin ping -h localhost --silent 2>/dev/null && \
    docker exec "${C}" mysql -uroot -p"${PASS}" -e "CREATE DATABASE IF NOT EXISTS test CHARACTER SET utf8mb4;" 2>/dev/null && break
  sleep 2
done

# ==== 完成 ====
done_banner "MySQL | Port: ${P} | Pass: ${PASS} | Data: ${DATA}"
