#!/usr/bin/env bash
# ZooKeeper 3.9 | Port: 2181, 2888, 3888, 8181 | Data: /root/zookeeper
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

CONTAINER="zookeeper"
IMAGE="zookeeper:${ZK_VERSION:-3.9}"
DATA="${ZK_DATA_ROOT:-/root/zookeeper}"
PORT="${ZK_PORT:-2181}"
PEER="${ZK_PEER_PORT:-2888}"
ELECT="${ZK_ELECTION_PORT:-3888}"
ADMIN="${ZK_ADMIN_PORT:-8181}"

require_root
check_docker
check_container_exists "${CONTAINER}" && exit 0
cleanup_container "${CONTAINER}"

ensure_dir "${DATA}/conf" "${DATA}/data" "${DATA}/log" "${DATA}/datalog"

cat > "${DATA}/conf/zoo.cfg" <<EOF
tickTime=2000
initLimit=10
syncLimit=5
dataDir=/data
dataLogDir=/datalog
clientPort=2181
clientPortAddress=0.0.0.0
admin.enableServer=true
admin.serverPort=8181
admin.serverAddress=0.0.0.0
4lw.commands.whitelist=*
EOF

echo "1" > "${DATA}/data/myid"
chmod -R 777 "${DATA}/data" "${DATA}/log" "${DATA}/datalog"

pull_image "${IMAGE}"
docker run -d --name "${CONTAINER}" --restart=always --privileged=true --user root \
  -p ${PORT}:2181 -p ${PEER}:2888 -p ${ELECT}:3888 -p ${ADMIN}:8181 \
  -e TZ=Asia/Shanghai \
  -v "${DATA}/conf/zoo.cfg:/conf/zoo.cfg" \
  -v "${DATA}/data:/data" \
  -v "${DATA}/datalog:/datalog" \
  -v "${DATA}/log:/logs" \
  "${IMAGE}"

wait_for_container "${CONTAINER}" 30

done_banner "ZooKeeper"
log_info "Port: ${PORT}  Admin: http://127.0.0.1:${ADMIN}/commands"
