#!/usr/bin/env bash
# RabbitMQ 3.13 | Port: 5672, 15672 | admin/admin
# export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="rabbitmq"; I="rabbitmq:${RMQ_VERSION:-3.13}-management"
AP="${RMQ_PORT:-5672}"; MP="${RMQ_MGMT_PORT:-15672}"
U="${RABBITMQ_USER:-admin}"; PASS="${RABBITMQ_PASSWORD:-admin}"
DATA="${DOCKER_DATA_ROOT:-/c/Users/code/Desktop/docker-data}/rabbitmq-data"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

# ==== 数据目录 ====
mkdir -p "${DATA}/data" "${DATA}/log" || { log_error "无法创建 ${DATA}"; exit 1; }

# ==== 拉取镜像 ====
pull_image "${I}"

# ==== 启动容器 ====
docker run -d --name "${C}" --restart=unless-stopped -p ${AP}:5672 -p ${MP}:15672 \
  -e TZ=Asia/Shanghai -e "RABBITMQ_DEFAULT_USER=${U}" -e "RABBITMQ_DEFAULT_PASS=${PASS}" \
  -v "${DATA}/data:/var/lib/rabbitmq" -v "${DATA}/log:/var/log/rabbitmq" \
  "${I}"
wait_for_container "${C}" 60

# ==== 完成 ====
done_banner "RabbitMQ | AMQP: ${AP} | Console: http://localhost:${MP} | ${U}/${PASS} | Data: ${DATA}"
