#!/usr/bin/env bash
# Elasticsearch 8.11 | Port: 9200, 9300 | Data: /root/elasticsearch
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

CONTAINER="elasticsearch"
IMAGE="elasticsearch:${ES_VERSION:-8.11.0}"
DATA="${ES_DATA_ROOT:-/root/elasticsearch}"
PORT="${ES_PORT:-9200}"
TRANSPORT="${ES_TRANSPORT_PORT:-9300}"
JAVA_OPTS="${ES_JAVA_OPTS:--Xms512m -Xmx512m}"

require_root
check_docker
check_container_exists "${CONTAINER}" && exit 0
cleanup_container "${CONTAINER}"

ensure_dir "${DATA}/conf" "${DATA}/data" "${DATA}/log" "${DATA}/plugins"

# vm.max_map_count
if [[ "$(sysctl -n vm.max_map_count 2>/dev/null || echo 0)" -lt 262144 ]]; then
  sysctl -w vm.max_map_count=262144
  echo "vm.max_map_count=262144" >> /etc/sysctl.conf
fi

cat > "${DATA}/conf/elasticsearch.yml" <<EOF
cluster.name: docker-cluster
node.name: node-1
network.host: 0.0.0.0
http.port: 9200
transport.port: 9300
path.data: /usr/share/elasticsearch/data
path.logs: /usr/share/elasticsearch/logs
discovery.type: single-node
xpack.security.enabled: false
EOF

chmod 777 "${DATA}/data" "${DATA}/log"

pull_image "${IMAGE}"
docker run -d --name "${CONTAINER}" --restart=always --privileged=true \
  -p ${PORT}:9200 -p ${TRANSPORT}:9300 \
  -e "discovery.type=single-node" \
  -e "ES_JAVA_OPTS=${JAVA_OPTS}" \
  -e "xpack.security.enabled=false" \
  -e TZ=Asia/Shanghai \
  -v "${DATA}/conf/elasticsearch.yml:/usr/share/elasticsearch/config/elasticsearch.yml" \
  -v "${DATA}/data:/usr/share/elasticsearch/data" \
  -v "${DATA}/log:/usr/share/elasticsearch/logs" \
  "${IMAGE}"

wait_for_container "${CONTAINER}" 120
log_info "Waiting for ES (can take 60-90s)..."
sleep 15

done_banner "Elasticsearch"
log_info "Port: ${PORT}  curl http://127.0.0.1:${PORT}/_cluster/health?pretty"
