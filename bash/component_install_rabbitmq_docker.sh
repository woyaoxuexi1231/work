#!/usr/bin/env bash
# RabbitMQ 3.13 | Port: 5672, 15672 | admin/admin
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

C="rabbitmq"; I="rabbitmq:${RMQ_VERSION:-3.13}-management"; AP="${RMQ_PORT:-5672}"; MP="${RMQ_MGMT_PORT:-15672}"
U="${RABBITMQ_USER:-admin}"; PASS="${RABBITMQ_PASSWORD:-admin}"

check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

pull_image "${I}"
docker run -d --name "${C}" --restart=unless-stopped -p ${AP}:5672 -p ${MP}:15672 \
  -e TZ=Asia/Shanghai -e "RABBITMQ_DEFAULT_USER=${U}" -e "RABBITMQ_DEFAULT_PASS=${PASS}" "${I}"
wait_for_container "${C}" 60

done_banner "RabbitMQ"; log_info "AMQP: localhost:${AP}  Console: http://localhost:${MP}  (${U}/${PASS})"
