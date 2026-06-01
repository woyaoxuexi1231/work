#!/usr/bin/env bash
# Consul 1.15.4 | Port: 8500, 8600 | Data: /root/consul
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

CONTAINER="consul"
IMAGE="consul:${CONSUL_VERSION:-1.15.4}"
DATA="${CONSUL_DATA_ROOT:-/root/consul}"
HTTP_PORT="${CONSUL_HTTP_PORT:-8500}"
DNS_PORT="${CONSUL_DNS_PORT:-8600}"

require_root
check_docker
check_container_exists "${CONTAINER}" && exit 0
cleanup_container "${CONTAINER}"

ensure_dir "${DATA}/config" "${DATA}/data"

cat > "${DATA}/config/consul.hcl" <<EOF
datacenter = "dc1"
node_name = "consul-server-1"
server = true
bootstrap_expect = 1
data_dir = "/consul/data"
log_level = "INFO"
client_addr = "0.0.0.0"
ui_config { enabled = true }
ports { http = 8500  dns = 8600 }
connect { enabled = false }
EOF

pull_image "${IMAGE}"
docker run -d --name "${CONTAINER}" --restart=always \
  -p ${HTTP_PORT}:8500 \
  -p ${DNS_PORT}:${DNS_PORT}/tcp \
  -p ${DNS_PORT}:${DNS_PORT}/udp \
  -v "${DATA}/config/consul.hcl:/consul/config/consul.hcl" \
  -v "${DATA}/data:/consul/data" \
  "${IMAGE}" agent -config-dir=/consul/config

wait_for_container "${CONTAINER}" 30

done_banner "Consul"
log_info "UI: http://127.0.0.1:${HTTP_PORT}/ui  API: http://127.0.0.1:${HTTP_PORT}/v1"
