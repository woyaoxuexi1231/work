#!/usr/bin/env bash
set -euo pipefail

PORT="2375"
BIND_ADDR="0.0.0.0"
OVERRIDE_DIR="/etc/systemd/system/docker.service.d"
OVERRIDE_FILE="${OVERRIDE_DIR}/override.conf"
DAEMON_JSON="/etc/docker/daemon.json"

[[ $EUID -ne 0 ]] && { echo "需要 root 权限"; exit 1; }

echo "=========================================="
echo " 开启 Docker $PORT 端口"
echo "=========================================="

# 检测 dockerd 路径
DOCKERD_PATH=""
for path in /usr/bin/dockerd /usr/local/bin/dockerd /usr/sbin/dockerd /snap/bin/dockerd; do
    [[ -x "$path" ]] && DOCKERD_PATH="$path" && break
done
[[ -z "$DOCKERD_PATH" ]] && DOCKERD_PATH="$(command -v dockerd 2>/dev/null || true)"
[[ -z "$DOCKERD_PATH" ]] && { echo "未找到 dockerd"; exit 1; }

echo "检测到 dockerd: $DOCKERD_PATH"

# 解除 masked
[[ "$(systemctl is-enabled docker.service 2>/dev/null || true)" == "masked" ]] && \
    systemctl unmask docker.service

# 移除 daemon.json 中的 hosts 配置
if [[ -f "$DAEMON_JSON" ]] && grep -q '"hosts"' "$DAEMON_JSON"; then
    command -v jq >/dev/null 2>&1 && \
        jq 'del(.hosts)' "$DAEMON_JSON" > "${DAEMON_JSON}.tmp" && \
        mv "${DAEMON_JSON}.tmp" "$DAEMON_JSON" || \
        sed -i '/"hosts"/d' "$DAEMON_JSON"
    echo "已移除 daemon.json 中的 hosts 配置"
fi

# 创建 systemd override
mkdir -p "$OVERRIDE_DIR"
cat > "$OVERRIDE_FILE" <<EOF
[Service]
ExecStart=
ExecStart=${DOCKERD_PATH} -H unix:///var/run/docker.sock -H tcp://${BIND_ADDR}:${PORT}
EOF
echo "已配置 systemd override"

# 重启 Docker
systemctl daemon-reload
systemctl restart docker
echo "Docker 已重启"

# 验证端口
sleep 3
if (command -v ss >/dev/null 2>&1 && ss -tlnp | grep -q ":${PORT} ") || \
   (command -v netstat >/dev/null 2>&1 && netstat -tlnp 2>/dev/null | grep -q ":${PORT} "); then
    echo "=========================================="
    echo " ✅ Docker $PORT 端口已开启!"
    echo " 📡 监听: ${BIND_ADDR}:${PORT}"
    echo "=========================================="
else
    echo "端口未监听，请检查日志"
    exit 1
fi