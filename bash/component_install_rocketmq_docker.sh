#!/usr/bin/env bash
# RocketMQ 5.3.2 | Ports: 9876, 10911, 8888(Dashboard)
# export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
I="apache/rocketmq:${ROCKETMQ_VERSION:-5.3.2}"
NS="rmqnamesrv"; BR="rmqbroker"; DB="rmqdashboard"
NS_P="${NAMESRV_PORT:-9876}"; BR_P="${BROKER_PORT_2:-10911}"; DB_P="${ROCKETMQ_DASHBOARD_PORT:-8888}"
NET="rmq"
DATA="${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}/rocketmq-data"

# ==== 前置检查 ====
check_docker
docker network ls --format '{{.Name}}' | grep -qw "${NET}" || docker network create "${NET}"

# ==== 数据目录 ====
mkdir -p "${DATA}/namesrv" "${DATA}/broker" "${DATA}/store" || { log_error "无法创建 ${DATA}"; exit 1; }

# ==== 拉取镜像 ====
pull_image "${I}"

# ==== NameServer ====
if ! check_container_exists "${NS}"; then
  cleanup_container "${NS}"
  docker run -d --name "${NS}" --restart=unless-stopped --network "${NET}" \
    -p ${NS_P}:9876 -e TZ=Asia/Shanghai \
    -v "${DATA}/namesrv:/home/rocketmq/logs/rocketmqlogs" \
    "${I}" sh mqnamesrv
  wait_for_container "${NS}" 15
fi

# ==== Broker + Proxy ====
if ! check_container_exists "${BR}"; then
  cleanup_container "${BR}"
  docker run -d --name "${BR}" --restart=unless-stopped --network "${NET}" \
    -p ${BR_P}:10911 -p 10909:10909 -p 8080:8080 -p 8081:8081 \
    -e "NAMESRV_ADDR=${NS}:9876" -e "JAVA_OPT=-Xms512m -Xmx1g" -e TZ=Asia/Shanghai \
    -v "${DATA}/broker:/home/rocketmq/logs/rocketmqlogs" \
    -v "${DATA}/store:/home/rocketmq/store" \
    "${I}" sh mqbroker --enable-proxy \
    -c /home/rocketmq/rocketmq-5.3.2/conf/broker.conf \
    -n "${NS}:9876" autoCreateTopicEnable=true 2>/dev/null
  wait_for_container "${BR}" 30
fi

# ==== Dashboard ====
if ! check_container_exists "${DB}"; then
  DB_IMG="apacherocketmq/rocketmq-dashboard:${ROCKETMQ_DASHBOARD_VERSION:-latest}"
  pull_image "${DB_IMG}" 2>/dev/null || true
  cleanup_container "${DB}"
  docker run -d --name "${DB}" --restart=unless-stopped --network "${NET}" \
    -e "JAVA_OPTS=-Drocketmq.namesrv.addr=${NS}:9876" -p ${DB_P}:8082 "${DB_IMG}" 2>/dev/null || log_warn "Dashboard 跳过"
fi

# ==== 完成 ====
done_banner "RocketMQ | NS: ${NS_P} | Broker: ${BR_P} | Dashboard: http://localhost:${DB_P} | Data: ${DATA}"
