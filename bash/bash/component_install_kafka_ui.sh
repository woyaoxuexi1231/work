#!/usr/bin/env bash
# Kafka UI | Port: 9097
# export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="kafka-ui"; I="provectuslabs/kafka-ui:${UI_VERSION:-latest}"
P="${UI_PORT:-9097}"; B="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

# ==== 拉取镜像 ====
pull_image "${I}"

# ==== 启动容器 ====
docker run -d --name "${C}" --restart=unless-stopped -p ${P}:8080 -e TZ=Asia/Shanghai \
  -e KAFKA_CLUSTERS_0_NAME=local -e "KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=${B}" \
  "${I}"
wait_for_container "${C}" 30

# ==== 完成 ====
done_banner "Kafka UI | http://localhost:${P}"
