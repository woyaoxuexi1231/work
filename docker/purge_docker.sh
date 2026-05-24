#!/usr/bin/env bash
# ========================================================================================
# Docker 环境深度清理脚本
# 功能: 清除旧版 Docker 仓库配置、过期 GPG 密钥、apt 缓存
# 注意: 此脚本仅清理软件源配置，不会卸载已安装的 Docker 包或删除容器数据
# 用法: sudo bash purge_docker.sh
# ========================================================================================

set -euo pipefail

log() { printf '[%s] [%-5s] %s\n' "$(date '+%H:%M:%S')" "$1" "$2"; }
info()  { log "INFO" "$*"; }
warn()  { log "WARN" "$*"; }

if [[ $EUID -ne 0 ]]; then
    exec sudo -E bash "$0" "$@"
fi

info "=== 开始清理 Docker 旧仓库配置 ==="

# 1. 清理 sources.list 中的 docker 仓库文件
info "清理 /etc/apt/sources.list.d/ 中的 Docker 仓库..."
for f in /etc/apt/sources.list.d/docker*; do
    if [[ -e "$f" ]]; then
        rm -f "$f"
        info "  已删除: $f"
    fi
done

# 2. 清理 GPG 密钥文件
info "清理 Docker GPG 密钥文件..."
for keyfile in \
    /etc/apt/keyrings/docker.gpg \
    /etc/apt/keyrings/docker.asc \
    /usr/share/keyrings/docker-archive-keyring.gpg \
    /usr/share/keyrings/docker-ce-archive-keyring.gpg; do
    if [[ -e "$keyfile" ]]; then
        rm -f "$keyfile"
        info "  已删除: $keyfile"
    fi
done

# 3. 清理 apt-key 遗留密钥（Docker 历史公钥指纹）
info "清理 apt-key 中的 Docker 旧密钥..."
DOCKER_KEY_FINGERPRINTS=(
    "7EA0A9C3F273FCD8"   # 旧版官方密钥 (已过期)
    "9DC85822A9553B61"   # 旧版备用密钥
    "0EBFCD88"           # 更早期的密钥 ID
)
for fp in "${DOCKER_KEY_FINGERPRINTS[@]}"; do
    if apt-key list 2>/dev/null | grep -qi "$fp"; then
        apt-key del "$fp" 2>/dev/null && info "  已移除密钥: $fp" || true
    fi
done

# 4. 清理 apt 缓存中可能损坏的 docker 包索引
info "清理 apt 缓存中的 Docker 索引..."
rm -f /var/lib/apt/lists/*docker* 2>/dev/null || true

# 5. 验证清理结果
info "验证清理结果..."
if ls /etc/apt/sources.list.d/docker* &>/dev/null; then
    warn "仍有残留的 Docker 仓库文件:"
    ls -la /etc/apt/sources.list.d/docker*
else
    info "✓ 所有 Docker 仓库配置已清理干净"
fi

info "=== 清理完成 ==="
info "提示: 如需卸载 Docker 包本身，请执行:"
info "  sudo apt-get remove -y docker-ce docker-ce-cli containerd.io docker-compose-plugin"