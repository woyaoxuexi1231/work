#!/usr/bin/env bash
# RocketMQ 5.3.2 | Ports: 9876, 10911, 10912, 10909, 8080, 8081, 8888 | Data: /root/rocketmq
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

IMAGE="apache/rocketmq:${ROCKETMQ_VERSION:-5.3.2}"
DATA="${ROCKETMQ_DATA_ROOT:-/root/rocketmq}"
NET="${ROCKETMQ_NETWORK:-rocketmq}"
NS_CONTAINER="rmqnamesrv"
BR_CONTAINER="rmqbroker"
DB_CONTAINER="rmqdashboard"
NS_PORT="${NAMESRV_PORT:-9876}"
BR_PORT="${BROKER_PORT_2:-10911}"
BR_HA="${BROKER_PORT_1:-10912}"
BR_QA="${BROKER_PORT_3:-10909}"
PX_HTTP="${PROXY_PORT_1:-8080}"
PX_GRPC="${PROXY_PORT_2:-8081}"
DB_PORT="${ROCKETMQ_DASHBOARD_PORT:-8888}"
BROKER_IP="${ROCKETMQ_BROKER_IP:-192.168.3.100}"

require_root
check_docker

# --- network ---
docker network ls --format '{{.Name}}' | grep -qw "${NET}" || docker network create "${NET}"

# --- NameServer ---
if ! check_container_exists "${NS_CONTAINER}"; then
  cleanup_container "${NS_CONTAINER}"
  ensure_dir "${DATA}/log/namesrv"
  pull_image "${IMAGE}"
  docker run -d --name "${NS_CONTAINER}" --restart=unless-stopped --network "${NET}" \
    -p ${NS_PORT}:9876 -u 0 -e TZ=Asia/Shanghai \
    -v "${DATA}/log/namesrv:/home/rocketmq/logs/rocketmqlogs" \
    "${IMAGE}" sh mqnamesrv
  wait_for_container "${NS_CONTAINER}" 15
fi

# --- Broker config ---
ensure_dir "${DATA}/conf" "${DATA}/data" "${DATA}/log/broker"
cat > "${DATA}/conf/broker.conf" <<EOF
brokerIP1=${BROKER_IP}
brokerClusterName=DefaultCluster
brokerName=broker-a
brokerId=0
brokerRole=ASYNC_MASTER
flushDiskType=ASYNC_FLUSH
listenPort=10911
haListenPort=10912
autoCreateTopicEnable=true
autoCreateSubscriptionGroup=true
storePathRootDir=/home/rocketmq/store
storePathCommitLog=/home/rocketmq/store/commitlog
storePathConsumeQueue=/home/rocketmq/store/consumequeue
EOF

cat > "${DATA}/conf/rmq-proxy.json" <<'JSON'
{"rocketMQClusterName":"DefaultCluster","proxyClusterName":"DefaultCluster","grpcServerPort":8081,"remotingServerPort":8080}
JSON

chmod -R 777 "${DATA}/log"

# --- Broker ---
if ! check_container_exists "${BR_CONTAINER}"; then
  mem_total_gb=$(($(grep MemTotal /proc/meminfo | awk '{print $2}') / 1024 / 1024))
  if [[ $mem_total_gb -ge 8 ]]; then mem_limit="4g"; java_opt="-Xms1g -Xmx2g"
  elif [[ $mem_total_gb -ge 4 ]]; then mem_limit="3g"; java_opt="-Xms512m -Xmx1g"
  else mem_limit="2g"; java_opt="-Xms256m -Xmx512m"
  fi

  cleanup_container "${BR_CONTAINER}"
  docker run -d --name "${BR_CONTAINER}" --restart=unless-stopped --network "${NET}" \
    --memory="${mem_limit}" -u 0 \
    -p ${BR_HA}:10912 -p ${BR_PORT}:10911 -p ${BR_QA}:10909 \
    -p ${PX_HTTP}:8080 -p ${PX_GRPC}:8081 \
    -e "NAMESRV_ADDR=${NS_CONTAINER}:9876" \
    -e "JAVA_OPT=${java_opt}" \
    -e TZ=Asia/Shanghai \
    -v "${DATA}/conf:/home/rocketmq/conf" \
    -v "${DATA}/data:/home/rocketmq/store" \
    -v "${DATA}/log/broker:/home/rocketmq/logs/rocketmqlogs" \
    "${IMAGE}" sh mqbroker --enable-proxy -c /home/rocketmq/conf/broker.conf
  wait_for_container "${BR_CONTAINER}" 30
fi

# --- Dashboard ---
if ! check_container_exists "${DB_CONTAINER}"; then
  DB_IMAGE="apacherocketmq/rocketmq-dashboard:${ROCKETMQ_DASHBOARD_VERSION:-latest}"
  pull_image "${DB_IMAGE}"
  cleanup_container "${DB_CONTAINER}"
  docker run -d --name "${DB_CONTAINER}" --restart=unless-stopped --network "${NET}" \
    -e "JAVA_OPTS=-Drocketmq.namesrv.addr=${NS_CONTAINER}:9876" \
    -e TZ=Asia/Shanghai \
    -p ${DB_PORT}:8082 \
    "${DB_IMAGE}" 2>/dev/null || log_warn "Dashboard 安装失败（可忽略）"
fi

done_banner "RocketMQ"
log_info "NameServer: 127.0.0.1:${NS_PORT}  Broker: 127.0.0.1:${BR_PORT}"
log_info "Dashboard: http://127.0.0.1:${DB_PORT}"
