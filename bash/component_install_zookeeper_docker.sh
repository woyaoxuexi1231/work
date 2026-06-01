#!/usr/bin/env bash
# ZooKeeper 3.9 | Port: 2181
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

C="zookeeper"; I="zookeeper:${ZK_VERSION:-3.9}"; P="${ZK_PORT:-2181}"

check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

pull_image "${I}"
docker run -d --name "${C}" --restart=always -p ${P}:2181 -e TZ=Asia/Shanghai "${I}"
wait_for_container "${C}" 30

done_banner "ZooKeeper"; log_info "Port: ${P}  echo stat | nc 127.0.0.1 ${P}"
