#!/usr/bin/env bash
set -euo pipefail

DAEMON_JSON="/etc/docker/daemon.json"
REGISTRY_TYPE="${1:-${DOCKER_REGISTRY:-official}}"

log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
info() { log "INFO: $*"; }
error() { log "ERROR: $*" >&2; exit 1; }

check_root() {
    [[ $EUID -eq 0 ]] && return
    info "使用 sudo 重新执行..."
    exec sudo -E bash "$0" "$@"
}

ensure_jq() {
    command -v jq >/dev/null 2>&1 || \
        (apt-get update -qq && apt-get install -y -qq jq >/dev/null 2>&1)
}

get_registry_config() {
    case "$1" in
        aliyun) echo '["https://1d7bgakn.mirror.aliyuncs.com"]' ;;
        tencent) echo '["https://mirror.ccs.tencentyun.com"]' ;;
        azure) echo '["https://dockerhub.azk8s.cn"]' ;;
        netease|163) echo '["https://hub-mirror.c.163.com"]' ;;
        douyin) echo '["https://docker.mirrors.dflux.tech"]' ;;
        custom) [[ -z "${CUSTOM_REGISTRY:-}" ]] && error "需要设置 CUSTOM_REGISTRY"; echo "[\"${CUSTOM_REGISTRY}\"]" ;;
        official) echo "" ;;
        *) error "不支持的镜像源: $1" ;;
    esac
}

set_registry() {
    ensure_jq
    mkdir -p "$(dirname "$DAEMON_JSON")"

    [[ ! -f "$DAEMON_JSON" ]] && echo '{}' > "$DAEMON_JSON"

    local config
    config="$(get_registry_config "$1")"

    if [[ -n "$config" ]]; then
        jq --argjson mirrors "$config" '. + {"registry-mirrors": $mirrors}' \
            "$DAEMON_JSON" > "${DAEMON_JSON}.tmp" && \
            mv "${DAEMON_JSON}.tmp" "$DAEMON_JSON"
        info "已配置镜像源"
    else
        jq 'del(.["registry-mirrors"])' "$DAEMON_JSON" 2>/dev/null > "${DAEMON_JSON}.tmp" && \
            mv "${DAEMON_JSON}.tmp" "$DAEMON_JSON" || true
        info "已恢复官方源"
    fi
}

restart_docker() {
    [[ "$(systemctl is-enabled docker.service 2>/dev/null || true)" == "masked" ]] && \
        systemctl unmask docker.service

    systemctl daemon-reload
    systemctl restart docker
    sleep 2
    info "✓ Docker 已重启"
}

main() {
    [[ "$1" == "status" ]] && {
        [[ -f "$DAEMON_JSON" ]] && cat "$DAEMON_JSON" || echo "配置文件不存在"
        docker info 2>/dev/null | grep -A 5 "Registry Mirrors" || true
        exit 0
    }

    check_root
    command -v docker >/dev/null 2>&1 || error "Docker 未安装"

    set_registry "$REGISTRY_TYPE"
    restart_docker

    info "镜像源已切换为: $REGISTRY_TYPE"
    [[ "$REGISTRY_TYPE" != "official" ]] && \
        info "配置: $(cat "$DAEMON_JSON")"
}

main "$@"