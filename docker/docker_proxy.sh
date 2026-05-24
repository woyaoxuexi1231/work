#!/usr/bin/env bash
set -euo pipefail

PROXY_HOST="${PROXY_HOST:-192.168.2.3}"
PROXY_PORT="${PROXY_PORT:-7890}"
PROXY_PROTOCOL="${PROXY_PROTOCOL:-http}"
SYSTEMD_DOCKER_DIR="/etc/systemd/system/docker.service.d"
HTTP_PROXY_CONF="${SYSTEMD_DOCKER_DIR}/http-proxy.conf"

log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
info() { log "INFO: $*"; }

check_root() {
    [[ $EUID -eq 0 ]] && return
    exec sudo -E bash "$0" "$@"
}

restart_docker() {
    [[ "$(systemctl is-enabled docker.service 2>/dev/null || true)" == "masked" ]] && \
        systemctl unmask docker.service

    systemctl daemon-reload
    systemctl restart docker
    info "✓ Docker 已重启"
}

enable_proxy() {
    check_root
    mkdir -p "$SYSTEMD_DOCKER_DIR"

    local PROXY_URL="${PROXY_PROTOCOL}://${PROXY_HOST}:${PROXY_PORT}"

    cat > "$HTTP_PROXY_CONF" <<EOF
[Service]
Environment="HTTP_PROXY=${PROXY_URL}"
Environment="HTTPS_PROXY=${PROXY_URL}"
Environment="NO_PROXY=localhost,127.0.0.1,::1"
EOF

    info "代理已配置: $PROXY_URL"
    restart_docker
}

disable_proxy() {
    check_root
    [[ -f "$HTTP_PROXY_CONF" ]] && rm -f "$HTTP_PROXY_CONF" && info "代理已禁用"
    restart_docker
}

status() {
    [[ -f "$HTTP_PROXY_CONF" ]] && {
        echo "代理配置:"
        cat "$HTTP_PROXY_CONF"
    } || echo "未配置代理"
}

case "${1:-}" in
    enable) enable_proxy ;;
    disable) disable_proxy ;;
    status) status ;;
    *) echo "用法: $0 {enable|disable|status}"; exit 1 ;;
esac