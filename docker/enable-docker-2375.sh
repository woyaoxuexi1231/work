#!/bin/bash
# ========================================================================================
# Docker 2375 端口开启脚本 (Enable Docker Remote API on TCP 2375)
# ========================================================================================
# 通过 Systemd override 安全开启 Docker Remote API 端口，兼容已有配置。
# ========================================================================================

set -euo pipefail

PORT="2375"
BIND_ADDR="0.0.0.0"
OVERRIDE_DIR="/etc/systemd/system/docker.service.d"
OVERRIDE_FILE="${OVERRIDE_DIR}/override.conf"
DAEMON_JSON="/etc/docker/daemon.json"

echo "=========================================="
echo " ⚠️  通过 Systemd 强制开启 Docker $PORT"
echo "=========================================="

# 1. 检查 root 权限
if [ "$EUID" -ne 0 ]; then
  echo "❌ 请使用 sudo 或 root 用户运行"
  exit 1
fi

# 2. 处理 daemon.json 中可能冲突的 hosts 字段
if [ -f "$DAEMON_JSON" ] && grep -q '"hosts"' "$DAEMON_JSON"; then
  BACKUP="${DAEMON_JSON}.bak.before-systemd-fix.$(date +%Y%m%d_%H%M%S)"
  cp "$DAEMON_JSON" "$BACKUP"
  echo "📋 已备份 daemon.json 到: $BACKUP"

  # 优先使用 jq 安全移除 hosts，没有 jq 则用 sed 兜底
  if command -v jq >/dev/null 2>&1; then
    jq 'del(.hosts)' "$BACKUP" > "$DAEMON_JSON" 2>/dev/null && {
      echo "✅ 已通过 jq 从 daemon.json 移除 hosts 配置"
    } || {
      echo "⚠️  jq 处理失败，回退使用 sed"
      cp "$BACKUP" "$DAEMON_JSON"
      sed -i '/"hosts"/d' "$DAEMON_JSON"
      # 清理可能残留的尾部逗号
      sed -i ':a;N;$!ba;s/,\n\([[:space:]]*}\)/\n\1/g' "$DAEMON_JSON"
    }
  else
    sed -i '/"hosts"/d' "$DAEMON_JSON"
    # 清理可能残留的尾部逗号
    sed -i ':a;N;$!ba;s/,\n\([[:space:]]*}\)/\n\1/g' "$DAEMON_JSON"
    echo "✅ 已从 daemon.json 移除 hosts 配置"
  fi
fi

# 3. 创建/更新 systemd override 配置（兼容已有内容）
mkdir -p "$OVERRIDE_DIR"

# 如果 override.conf 已存在，备份后兼容写入
if [ -f "$OVERRIDE_FILE" ]; then
  OVERRIDE_BACKUP="${OVERRIDE_FILE}.backup.$(date +%Y%m%d_%H%M%S)"
  cp "$OVERRIDE_FILE" "$OVERRIDE_BACKUP"
  echo "📋 已备份现有 override.conf 到: $OVERRIDE_BACKUP"

  # 检查是否已包含我们的 dockerd ExecStart 配置
  if grep -q "tcp://${BIND_ADDR}:${PORT}" "$OVERRIDE_FILE" 2>/dev/null; then
    echo "⚠️  override.conf 已包含 ${PORT} 端口配置，跳过写入"
    echo "   如需重新配置，请先删除: $OVERRIDE_FILE"
  else
    # 追加配置（而非覆盖），确保不与已有配置冲突
    echo "" >> "$OVERRIDE_FILE"
    cat >> "$OVERRIDE_FILE" <<EOF
# Added by enable-docker-2375.sh on $(date '+%Y-%m-%d %H:%M:%S')
[Service]
ExecStart=
ExecStart=/usr/bin/dockerd -H unix:///var/run/docker.sock -H tcp://${BIND_ADDR}:${PORT}
EOF
    echo "✅ 已追加配置到: $OVERRIDE_FILE"
  fi
else
  cat > "$OVERRIDE_FILE" <<EOF
[Service]
ExecStart=
ExecStart=/usr/bin/dockerd -H unix:///var/run/docker.sock -H tcp://${BIND_ADDR}:${PORT}
EOF
  echo "✅ 已写入 systemd override: $OVERRIDE_FILE"
fi

# 4. 重载并重启 Docker
systemctl daemon-reload
echo "🔄 正在重启 Docker..."
if ! systemctl restart docker; then
  echo "❌ Docker 重启失败，请执行: journalctl -u docker --no-pager -n 30"
  exit 1
fi

# 5. 验证端口
sleep 3
PORT_CHECK_OK=false
if command -v ss >/dev/null 2>&1; then
  ss -tlnp | grep -q ":${PORT} " && PORT_CHECK_OK=true
elif command -v netstat >/dev/null 2>&1; then
  netstat -tlnp 2>/dev/null | grep -q ":${PORT} " && PORT_CHECK_OK=true
fi

if $PORT_CHECK_OK; then
  echo "=========================================="
  echo " ✅ Docker $PORT 端口已成功强制开启!"
  echo " 📡 监听: ${BIND_ADDR}:${PORT}"
  echo " 🔗 连接: export DOCKER_HOST=tcp://<服务器IP>:${PORT}"
  echo "=========================================="
else
  echo "❌ 端口 ${PORT} 未检测到监听，请执行: journalctl -u docker --no-pager -n 30"
  exit 1
fi