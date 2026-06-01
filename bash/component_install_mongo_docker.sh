#!/usr/bin/env bash
# MongoDB 8.0 | Port: 27017 | admin/admin123
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

C="mongo"; I="mongo:${MONGO_VERSION:-8.0}"; P="${MONGO_PORT:-27017}"; U="${MONGO_ROOT_USER:-admin}"; PASS="${MONGO_ROOT_PASSWORD:-admin123}"

check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

pull_image "${I}"
docker run -d --name "${C}" --restart=always -p ${P}:27017 -e TZ=Asia/Shanghai \
  -e "MONGO_INITDB_ROOT_USERNAME=${U}" -e "MONGO_INITDB_ROOT_PASSWORD=${PASS}" "${I}"
wait_for_container "${C}" 60

done_banner "MongoDB"; log_info "Port: ${P}  URI: mongodb://${U}:${PASS}@127.0.0.1:${P}/admin"
