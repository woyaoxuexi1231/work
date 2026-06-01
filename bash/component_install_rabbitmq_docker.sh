#!/usr/bin/env bash
# RabbitMQ 3.13 | Port: 5672, 15672 | Data: /root/rabbitmq | admin/admin
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

CONTAINER="rabbitmq"
IMAGE="rabbitmq:${RMQ_VERSION:-3.13}-management"
DATA="${RMQ_DATA_ROOT:-/root/rabbitmq}"
AMQP_PORT="${RMQ_PORT:-5672}"
MGMT_PORT="${RMQ_MGMT_PORT:-15672}"
RMQ_USER="${RABBITMQ_USER:-admin}"
RMQ_PASS="${RABBITMQ_PASSWORD:-admin}"

require_root
check_docker
check_container_exists "${CONTAINER}" && exit 0
cleanup_container "${CONTAINER}"

ensure_dir "${DATA}/data" "${DATA}/log" "${DATA}/plugins"

cat > "${DATA}/rabbitmq.conf" <<EOF
listeners.tcp.default = 5672
management.tcp.port = 15672
management.tcp.ip = 0.0.0.0
log.console = true
log.console.level = info
EOF

pull_image "${IMAGE}"
docker run -d --name "${CONTAINER}" --restart=unless-stopped --privileged=true \
  -p ${AMQP_PORT}:5672 -p ${MGMT_PORT}:15672 \
  -e TZ=Asia/Shanghai \
  -e "RABBITMQ_DEFAULT_USER=${RMQ_USER}" \
  -e "RABBITMQ_DEFAULT_PASS=${RMQ_PASS}" \
  -v "${DATA}/rabbitmq.conf:/etc/rabbitmq/rabbitmq.conf" \
  -v "${DATA}/data:/var/lib/rabbitmq" \
  -v "${DATA}/log:/var/log/rabbitmq" \
  "${IMAGE}"

wait_for_container "${CONTAINER}" 60

done_banner "RabbitMQ"
log_info "AMQP: 127.0.0.1:${AMQP_PORT}  Console: http://127.0.0.1:${MGMT_PORT}"
log_info "User: ${RMQ_USER}  Pass: ${RMQ_PASS}"
