#!/usr/bin/env bash
# Consul 1.15.4 | Port: 8500, 8600
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

C="consul"; I="consul:${CONSUL_VERSION:-1.15.4}"; HP="${CONSUL_HTTP_PORT:-8500}"; DP="${CONSUL_DNS_PORT:-8600}"

check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

pull_image "${I}"
docker run -d --name "${C}" --restart=always -e TZ=Asia/Shanghai \
  -p ${HP}:8500 -p ${DP}:${DP}/tcp -p ${DP}:${DP}/udp \
  "${I}" agent -dev -client=0.0.0.0 -bind=0.0.0.0 -ui
wait_for_container "${C}" 15

done_banner "Consul"; log_info "UI: http://127.0.0.1:${HP}/ui  API: http://127.0.0.1:${HP}/v1"
