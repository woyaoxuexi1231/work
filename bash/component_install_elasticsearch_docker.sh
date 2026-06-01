#!/usr/bin/env bash
# Elasticsearch 8.11 | Port: 9200, 9300 | no-auth
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

C="elasticsearch"; I="elasticsearch:${ES_VERSION:-8.11.0}"; P="${ES_PORT:-9200}"; T="${ES_TRANSPORT_PORT:-9300}"

check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

pull_image "${I}"
docker run -d --name "${C}" --restart=always -p ${P}:9200 -p ${T}:9300 \
  -e "discovery.type=single-node" -e "xpack.security.enabled=false" \
  -e "ES_JAVA_OPTS=${ES_JAVA_OPTS:--Xms512m -Xmx512m}" \
  -e TZ=Asia/Shanghai "${I}"
wait_for_container "${C}" 120; sleep 15

done_banner "Elasticsearch"; log_info "Port: ${P}  curl http://127.0.0.1:${P}/_cluster/health?pretty"
