#!/usr/bin/env bash
# Nacos 2.3.2 | Port: 8848, 9848, 9849 | nacos/nacos
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

C="nacos"; I="nacos/nacos-server:${NACOS_VERSION:-v2.3.2}"; P="${NACOS_PORT:-8848}"
G1=$((P+1000)); G2=$((P+1001))

check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

pull_image "${I}"
docker run -d --name "${C}" --restart=always -p ${P}:8848 -p ${G1}:9848 -p ${G2}:9849 \
  -e MODE="${NACOS_MODE:-standalone}" -e NACOS_AUTH_ENABLE=false "${I}"
wait_for_container "${C}" 60

done_banner "Nacos"; log_info "Console: http://127.0.0.1:${P}/nacos  User: nacos  Pass: nacos"
