#!/usr/bin/env bash
# Prometheus + Grafana | Port: 9090(Prom), 3000(Grafana)
export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
PROM_P="${PROMETHEUS_PORT:-9090}"; GRAF_P="${GRAFANA_PORT:-3000}"
U="${GRAFANA_USER:-admin}"; PASS="${GRAFANA_PASSWORD:-admin123}"
DATA="${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}/monitor-data"

# ==== 前置检查 ====
check_docker

# ==== 数据目录 ====
mkdir -p "${DATA}/prometheus" "${DATA}/grafana" || { log_error "无法创建 ${DATA}"; exit 1; }

# ==== Prometheus ====
if ! check_container_exists "prometheus"; then
  cleanup_container "prometheus"
  pull_image "prom/prometheus:v2.45.0"
  docker run -d --name prometheus --restart=always -p ${PROM_P}:9090 -e TZ=Asia/Shanghai \
    -v "${DATA}/prometheus:/prometheus" \
    prom/prometheus:v2.45.0 --config.file=/etc/prometheus/prometheus.yml --web.enable-lifecycle
  wait_for_container "prometheus" 15
fi

# ==== Grafana ====
if ! check_container_exists "grafana"; then
  cleanup_container "grafana"
  pull_image "grafana/grafana:9.5.8"
  docker run -d --name grafana --restart=always -p ${GRAF_P}:3000 -e TZ=Asia/Shanghai \
    -e "GF_SECURITY_ADMIN_USER=${U}" -e "GF_SECURITY_ADMIN_PASSWORD=${PASS}" \
    -v "${DATA}/grafana:/var/lib/grafana" \
    grafana/grafana:9.5.8
  wait_for_container "grafana" 15
fi

# ==== 完成 ====
done_banner "Prometheus: http://localhost:${PROM_P} | Grafana: http://localhost:${GRAF_P} (${U}/${PASS}) | Data: ${DATA}"
