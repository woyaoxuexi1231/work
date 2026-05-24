#!/usr/bin/env bash
set -euo pipefail

TARGET_VERSION="${1:-${DOCKER_TARGET_VERSION:-24.0}}"
LOG_FILE="/var/log/docker_install_$(date +%Y%m%d_%H%M%S).log"
DAEMON_JSON="/etc/docker/daemon.json"

log() { printf '[%s] [%-5s] %s\n' "$(date '+%H:%M:%S')" "$1" "$2" | tee -a "$LOG_FILE"; }
info() { log "INFO" "$*"; }
die() { log "ERROR" "$*" >&2; exit 1; }

ensure_root() {
    [[ $EUID -eq 0 ]] && return
    info "使用 sudo 重新执行..."
    exec sudo -E bash "$0" "$@"
}

get_current_version() {
    docker version --format '{{.Server.Version}}' 2>/dev/null || echo ""
}

check_version_match() {
    local current="$1" target="$2"
    [[ -z "$current" ]] && return 1
    [[ "$target" == "latest" ]] && return 0
    [[ "$current" == "$target"* ]] || [[ "$current" =~ ^${target//./\\.}\. ]]
}

setup_repository() {
    local arch distro gpg_keyring="/etc/apt/keyrings/docker.gpg"
    arch="$(dpkg --print-architecture)"
    source /etc/os-release
    distro="${VERSION_CODENAME:-$(lsb_release -cs 2>/dev/null || echo jammy)}"

    info "系统架构: $arch | 发行版代号: $distro"

    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq
    apt-get install -y -qq ca-certificates curl gnupg lsb-release >/dev/null 2>&1

    mkdir -p /etc/apt/keyrings

    # 阿里云镜像源
    if curl -fsSL --connect-timeout 10 "https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg" \
        | gpg --dearmor -o "$gpg_keyring" 2>/dev/null; then
        chmod a+r "$gpg_keyring"
        echo "deb [arch=$arch signed-by=$gpg_keyring] https://mirrors.aliyun.com/docker-ce/linux/ubuntu $distro stable" \
            > /etc/apt/sources.list.d/docker.list
        apt-get update -qq && info "✓ 仓库配置成功" && return 0
    fi

    die "仓库配置失败"
}

install_docker_packages() {
    local target="$1"

    if [[ "$target" == "latest" ]]; then
        info "安装最新版 Docker..."
        apt-get install -y -qq --allow-change-held-packages \
            docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin \
            2>&1 | tee -a "$LOG_FILE"
    else
        local ce_ver cli_ver
        ce_ver="$(apt-cache madison docker-ce 2>/dev/null \
            | awk '{print $3}' | grep -E "^([0-9]+:)?${target//./\\.}\." | head -1 || true)"

        [[ -z "$ce_ver" ]] && die "未找到 Docker ${target}.x 版本"

        cli_ver="$(apt-cache madison docker-ce-cli 2>/dev/null \
            | awk '{print $3}' | grep -E "^([0-9]+:)?${target//./\\.}\." | head -1 || true)"

        info "目标版本: docker-ce=$ce_ver"
        apt-get install -y -qq --allow-change-held-packages --allow-downgrades \
            "docker-ce=$ce_ver" "docker-ce-cli=${cli_ver:-$ce_ver}" \
            containerd.io docker-buildx-plugin docker-compose-plugin \
            2>&1 | tee -a "$LOG_FILE"
    fi
}

configure_daemon() {
    mkdir -p "$(dirname "$DAEMON_JSON")"
    cat > "$DAEMON_JSON" <<EOF
{
    "log-driver": "json-file",
    "log-opts": {"max-size": "50m", "max-file": "3"},
    "storage-driver": "overlay2"
}
EOF
    chmod 644 "$DAEMON_JSON"
    info "daemon.json 已配置"
}

verify_installation() {
    # 解除 masked 状态
    [[ "$(systemctl is-enabled docker.service 2>/dev/null || true)" == "masked" ]] && \
        systemctl unmask docker.service

    systemctl enable --now docker >/dev/null 2>&1

    local retries=10
    while ((retries-- > 0)); do
        docker info &>/dev/null && break
        sleep 1
    done

    docker info &>/dev/null || die "Docker 启动失败"

    info "=========================================="
    info " Docker $(docker version --format '{{.Server.Version}}') 安装成功"
    info " Compose $(docker compose version --short 2>/dev/null || echo 'N/A')"
    info "=========================================="
}

main() {
    ensure_root "$@"
    mkdir -p "$(dirname "$LOG_FILE")"

    info "=== Docker 安装脚本 ==="
    info "目标版本: $TARGET_VERSION | 日志: $LOG_FILE"

    local current_ver
    current_ver="$(get_current_version)"

    if check_version_match "$current_ver" "$TARGET_VERSION"; then
        info "Docker $current_ver 已安装，跳过"
        verify_installation
        return 0
    fi

    [[ -n "$current_ver" ]] && info "当前版本 $current_ver，将重新安装"

    setup_repository
    install_docker_packages "$TARGET_VERSION"
    configure_daemon
    verify_installation
}

trap 'die "安装中断"' ERR
main "$@"