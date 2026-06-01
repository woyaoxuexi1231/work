#!/usr/bin/env bash
# ============================================================================
# 设置 Ubuntu 时区为中国标准时间 (Asia/Shanghai)
# ============================================================================
set -e

echo "当前时区: $(timedatectl show --property=Timezone --value)"

if timedatectl set-timezone Asia/Shanghai 2>/dev/null; then
    echo "✓ 宿主机时区已设为 Asia/Shanghai"
else
    # fallback：老版本 Ubuntu 或非 systemd
    sudo ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
    echo "✓ 宿主机时区已设为 Asia/Shanghai（ln 方式）"
fi

echo ""
echo "当前时间: $(date)"
echo "当前时区: $(timedatectl show --property=Timezone --value 2>/dev/null || cat /etc/timezone)"
