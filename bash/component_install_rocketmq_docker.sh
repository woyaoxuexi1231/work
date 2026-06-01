#!/usr/bin/env bash
# RocketMQ 5.3.2 | Ports: 9876, 10911, 10909, 8080, 8081, 8888
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

I="apache/rocketmq:${ROCKETMQ_VERSION:-5.3.2}"; NET="rmq"
NS="rmqnamesrv"; BR="rmqbroker"; DB="rmqdashboard"
NS_P="${NAMESRV_PORT:-9876}"; BR_P="${BROKER_PORT_2:-10911}"; DB_P="${ROCKETMQ_DASHBOARD_PORT:-8888}"

check_docker

docker network ls --format '{{.Name}}' | grep -qw "${NET}" || docker network create "${NET}"

# NameServer
if ! check_container_exists "${NS}"; then
  cleanup_container "${NS}"
  pull_image "${I}"
  docker run -d --name "${NS}" --restart=unless-stopped --network "${NET}" \
    -p ${NS_P}:9876 -e TZ=Asia/Shanghai "${I}" sh mqnamesrv
  wait_for_container "${NS}" 15
fi

# Broker + Proxy
if ! check_container_exists "${BR}"; then
  cleanup_container "${BR}"
  docker run -d --name "${BR}" --restart=unless-stopped --network "${NET}" \
    -p ${BR_P}:10911 -p 10909:10909 -p 8080:8080 -p 8081:8081 \
    -e "NAMESRV_ADDR=${NS}:9876" -e "JAVA_OPT=-Xms512m -Xmx1g" \
    -e TZ=Asia/Shanghai "${I}" sh mqbroker --enable-proxy \
    -c /home/rocketmq/rocketmq-5.3.2/conf/broker.conf \
    -n "${NS}:9876" \
    autoCreateTopicEnable=true 2>/dev/null
  wait_for_container "${BR}" 30
fi

# Dashboard
if ! check_container_exists "${DB}"; then
  DB_IMG="apacherocketmq/rocketmq-dashboard:${ROCKETMQ_DASHBOARD_VERSION:-latest}"
  pull_image "${DB_IMG}" 2>/dev/null || true
  cleanup_container "${DB}"
  docker run -d --name "${DB}" --restart=unless-stopped --network "${NET}" \
    -e "JAVA_OPTS=-Drocketmq.namesrv.addr=${NS}:9876" \
    -p ${DB_P}:8082 "${DB_IMG}" 2>/dev/null || log_warn "Dashboard 拉取失败（可忽略）"
fi

done_banner "RocketMQ"; log_info "NameServer: localhost:${NS_P}  Broker: localhost:${BR_P}  Dashboard: http://localhost:${DB_P}"
