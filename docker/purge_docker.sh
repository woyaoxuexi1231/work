#!/usr/bin/env bash
set -euo pipefail

log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
info() { log "INFO: $*"; }

[[ $EUID -ne 0 ]] && exec sudo -E bash "$0" "$@"

info "=== 清理 Docker 仓库配置 ==="

# 删除仓库文件
for f in /etc/apt/sources.list.d/docker*; do
    [[ -e "$f" ]] && rm -f "$f" && info "已删除: $f"
done

# 删除 GPG 密钥
for keyfile in /etc/apt/keyrings/docker.gpg /usr/share/keyrings/docker*.gpg; do
    [[ -e "$keyfile" ]] && rm -f "$keyfile" && info "已删除: $keyfile"
done

# 清理 apt-key
for fp in 7EA0A9C3F273FCD8 9DC85822A9553B61 0EBFCD88; do
    apt-key list 2>/dev/null | grep -qi "$fp" && \
        apt-key del "$fp" 2>/dev/null && info "已移除密钥: $fp" || true
done

# 清理缓存
rm -f /var/lib/apt/lists/*docker* 2>/dev/null || true

info "✓ 清理完成"