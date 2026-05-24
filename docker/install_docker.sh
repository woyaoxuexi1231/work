#!/usr/bin/env bash
# ========================================================================================
# Docker CE 纯净安装脚本 (Ubuntu/Debian)
# 前置条件: 如存在旧仓库配置，请先运行 purge_docker.sh
# 功能: 安装/升级 Docker Engine + Compose V2 + Buildx
# 用法: sudo bash install_docker.sh [版本号|latest]
#       例: sudo bash install_docker.sh 24.0
#           sudo bash install_docker.sh latest
# ========================================================================================

set -euo pipefail
IFS=$'\n\t'

readonly TARGET_VERSION="${1:-${DOCKER_TARGET_VERSION:-24.0}}"
readonly LOG_FILE="/var/log/docker_install_$(date +%Y%m%d_%H%M%S).log"
readonly DAEMON_JSON="/etc/docker/daemon.json"

readonly REPO_MIRRORS=(
    "https://mirrors.aliyun.com/docker-ce/linux/ubuntu"
    "https://mirrors.tuna.tsinghua.edu.cn/docker-ce/linux/ubuntu"
    "https://download.docker.com/linux/ubuntu"
)

# ----------------------------------------
# 工具函数
# ----------------------------------------
log() { printf '[%s] [%-5s] %s\n' "$(date '+%H:%M:%S')" "$1" "$2" | tee -a "$LOG_FILE"; }
info()  { log "INFO" "$*"; }
warn()  { log "WARN" "$*"; }
die()   { log "ERROR" "$*" >&2; exit 1; }

ensure_root() {
    if [[ $EUID -ne 0 ]]; then
        warn "需要 root 权限，正在使用 sudo 重新执行..."
        exec sudo -E bash "$0" "$@"
    fi
}

safe_merge_json() {
    local file="$1" new_content="$2"
    if ! command -v jq &>/dev/null; then
        apt-get update -qq && apt-get install -y -qq jq >/dev/null 2>&1 || die "无法安装 jq"
    fi
    if [[ -f "$file" ]]; then
        cp "$file" "${file}.bak.$(date +%s)"
        jq -s '.[0] * .[1]' "$file" <(echo "$new_content") > "${file}.tmp" \
            && mv "${file}.tmp" "$file"
    else
        mkdir -p "$(dirname "$file")"
        echo "$new_content" | jq '.' > "$file"
    fi
    chmod 644 "$file"
}

# ----------------------------------------
# 核心逻辑
# ----------------------------------------
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
    local arch distro repo_url gpg_keyring="/etc/apt/keyrings/docker.gpg"

    arch="$(dpkg --print-architecture)"
    source /etc/os-release
    distro="${VERSION_CODENAME:-$(lsb_release -cs 2>/dev/null || echo jammy)}"

    info "系统架构: $arch | 发行版代号: $distro"

    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq
    apt-get install -y -qq ca-certificates curl gnupg lsb-release >/dev/null 2>&1
    mkdir -p /etc/apt/keyrings

    for mirror in "${REPO_MIRRORS[@]}"; do
        repo_url="$mirror"
        info "尝试仓库源: $repo_url"

        rm -f "$gpg_keyring"
        if curl -fsSL --connect-timeout 10 --max-time 30 "${repo_url}/gpg" \
            | gpg --dearmor -o "$gpg_keyring" 2>/dev/null; then
            chmod a+r "$gpg_keyring"
        else
            warn "GPG 导入失败，跳过此源"
            continue
        fi

        echo "deb [arch=$arch signed-by=$gpg_keyring] $repo_url $distro stable" \
            > /etc/apt/sources.list.d/docker.list

        if apt-get update -qq 2>/dev/null; then
            info "✓ 仓库源配置成功: $repo_url"
            return 0
        fi
        warn "apt update 失败，尝试下一个源..."
    done

    die "所有仓库源均不可用。如存在旧仓库残留，请先运行: sudo bash purge_docker.sh"
}

install_docker_packages() {
    local target="$1" packages=()

    if [[ "$target" == "latest" ]]; then
        packages=(docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin)
        info "安装最新版 Docker..."
        apt-get install -y -qq "${packages[@]}" 2>&1 | tee -a "$LOG_FILE"
    else
        local ce_ver cli_ver
        ce_ver="$(apt-cache madison docker-ce 2>/dev/null \
            | awk '{print $3}' | grep -E "^([0-9]+:)?${target//./\\.}\\." | head -1 || true)"

        if [[ -z "$ce_ver" ]]; then
            die "未找到 Docker ${target}.x 版本，可用版本:\n$(apt-cache madison docker-ce | head -5)"
        fi

        cli_ver="$(apt-cache madison docker-ce-cli 2>/dev/null \
            | awk '{print $3}' | grep -E "^([0-9]+:)?${target//./\\.}\\." | head -1 || true)"

        info "目标版本: docker-ce=$ce_ver"
        packages=("docker-ce=$ce_ver" "docker-ce-cli=${cli_ver:-$ce_ver}"
                  containerd.io docker-buildx-plugin docker-compose-plugin)

        apt-get install -y -qq --allow-downgrades "${packages[@]}" 2>&1 | tee -a "$LOG_FILE"
    fi
}

configure_daemon() {
    local config='{
        "log-driver": "json-file",
        "log-opts": {"max-size": "50m", "max-file": "3"},
        "storage-driver": "overlay2"
    }'
    safe_merge_json "$DAEMON_JSON" "$config"
    info "daemon.json 已配置 (日志轮转 + overlay2)"
}

verify_installation() {
    systemctl enable --now docker >/dev/null 2>&1

    local retries=10
    while ((retries-- > 0)); do
        docker info &>/dev/null && break
        sleep 1
    done

    if ! docker info &>/dev/null; then
        die "Docker 服务启动失败，请查看: journalctl -xeu docker.service"
    fi

    info "=========================================="
    info " Docker $(docker version --format '{{.Server.Version}}') 安装成功"
    info " Compose $(docker compose version --short 2>/dev/null || echo 'N/A')"
    info " API: $(docker version --format '{{.Server.APIVersion}}')"
    info " Storage: $(docker info --format '{{.Driver}}')"
    info "=========================================="
}

# ----------------------------------------
# 主流程
# ----------------------------------------
main() {
    ensure_root "$@"
    mkdir -p "$(dirname "$LOG_FILE")"

    info "=== Docker 纯净安装脚本 ==="
    info "目标版本: $TARGET_VERSION | 日志: $LOG_FILE"

    local current_ver
    current_ver="$(get_current_version)"
    if check_version_match "$current_ver" "$TARGET_VERSION"; then
        info "Docker $current_ver 已安装且版本匹配，跳过安装"
        verify_installation
        return 0
    fi

    [[ -n "$current_ver" ]] && warn "当前版本 $current_ver 不匹配目标 $TARGET_VERSION，将重新安装"

    setup_repository
    install_docker_packages "$TARGET_VERSION"
    configure_daemon
    verify_installation
}

trap 'die "安装中断 (行号: $LINENO)"' ERR
main "$@"