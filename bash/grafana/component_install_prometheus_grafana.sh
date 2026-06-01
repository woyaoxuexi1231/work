#!/usr/bin/env bash
# Prometheus + Grafana | Ports: 9090(Prom), 3000(Grafana), 9100(NodeExp) | Data: /root/monitoring
# Node Exporter 安装在宿主机上
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

DATA="${DATA_ROOT:-/root/monitoring}"
PROM_PORT="${PROMETHEUS_PORT:-9090}"
GRAF_PORT="${GRAFANA_PORT:-3000}"
GRAF_USER="${GRAFANA_USER:-admin}"
GRAF_PASS="${GRAFANA_PASSWORD:-admin123}"
NE_PORT="${NODE_EXPORTER_PORT:-9100}"
HOST_IP="${HOST_IP:-$(hostname -I | awk '{print $1}')}"
TARGET="${TARGET_SERVER:-192.168.3.100}"

require_root
check_docker

ensure_dir "${DATA}/prometheus" "${DATA}/grafana" "${DATA}/configs"
chmod 777 "${DATA}/prometheus" "${DATA}/grafana"

# --- Prometheus config ---
cat > "${DATA}/configs/prometheus.yml" <<EOF
global:
  scrape_interval: 15s
scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']
  - job_name: 'node'
    static_configs:
      - targets: ['${HOST_IP}:${NE_PORT}', '${TARGET}:${NE_PORT}']
EOF

# --- Node Exporter (host) ---
if ! command -v node_exporter >/dev/null 2>&1; then
  NE_VER="${NODE_EXPORTER_VERSION:-1.8.2}"
  NE_TAR="${SCRIPT_DIR}/node_exporter-${NE_VER}.linux-amd64.tar.gz"
  if [[ -f "${NE_TAR}" ]]; then
    mkdir -p /opt/node_exporter
    cp "${NE_TAR}" /opt/node_exporter/
    cd /opt/node_exporter
    tar xzf "node_exporter-${NE_VER}.linux-amd64.tar.gz"
    mv "node_exporter-${NE_VER}.linux-amd64/node_exporter" /usr/local/bin/
    chmod +x /usr/local/bin/node_exporter
    rm -rf node_exporter-*
    cat > /etc/systemd/system/node_exporter.service <<EOF
[Unit]
Description=Node Exporter
After=network.target
[Service]
Type=simple; User=root
ExecStart=/usr/local/bin/node_exporter --web.listen-address=0.0.0.0:${NE_PORT}
Restart=always
[Install]
WantedBy=multi-user.target
EOF
    systemctl daemon-reload && systemctl enable --now node_exporter
  else
    log_warn "未找到 node_exporter 安装包，跳过（可稍后手动安装）"
  fi
fi

# --- Prometheus ---
if ! check_container_exists "prometheus"; then
  cleanup_container "prometheus"
  pull_image "prom/prometheus:v2.45.0"
  docker run -d --name prometheus --restart=always \
    -p ${PROM_PORT}:9090 -e TZ=Asia/Shanghai \
    -v "${DATA}/configs/prometheus.yml:/etc/prometheus/prometheus.yml" \
    -v "${DATA}/prometheus:/prometheus" \
    prom/prometheus:v2.45.0 \
    --config.file=/etc/prometheus/prometheus.yml --web.enable-lifecycle
  wait_for_container "prometheus" 15
fi

# --- Grafana ---
if ! check_container_exists "grafana"; then
  cleanup_container "grafana"
  pull_image "grafana/grafana:9.5.8"
  docker run -d --name grafana --restart=always \
    -p ${GRAF_PORT}:3000 -e TZ=Asia/Shanghai \
    -e "GF_SECURITY_ADMIN_USER=${GRAF_USER}" \
    -e "GF_SECURITY_ADMIN_PASSWORD=${GRAF_PASS}" \
    -v "${DATA}/grafana:/var/lib/grafana" \
    grafana/grafana:9.5.8
  wait_for_container "grafana" 15
fi

done_banner "Prometheus + Grafana"
log_info "Prometheus: http://127.0.0.1:${PROM_PORT}"
log_info "Grafana:    http://127.0.0.1:${GRAF_PORT}  (${GRAF_USER}/${GRAF_PASS})"
log_info "NodeExp:    http://127.0.0.1:${NE_PORT}/metrics"
