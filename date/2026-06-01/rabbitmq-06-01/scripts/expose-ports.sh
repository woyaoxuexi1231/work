#!/usr/bin/env bash
# ============================================================================
# expose-ports.sh —— Windows 防火墙 + 端口转发（解决本机无法用外网IP访问Docker）
# 用法：右键 Git Bash → 以管理员身份运行 → bash scripts/expose-ports.sh
# ============================================================================

LOCAL_IP="192.168.3.100"

echo "============================================"
echo " Windows Docker 端口暴露"
echo "============================================"
echo ""

# -------- 1. 防火墙规则 --------
echo "[1/2] 防火墙开放端口..."
for port in 5672 5673 5674 15672 15673 15674; do
  echo -n "  ${port} ... "
  netsh advfirewall firewall add rule \
    name="RabbitMQ-Port-${port}" \
    dir=in action=allow protocol=TCP \
    localport="${port}" >/dev/null 2>&1 && echo "✓" || echo "已存在"
done

# -------- 2. 端口转发（解决本机 localhost-only 问题） --------
echo ""
echo "[2/2] 端口转发: ${LOCAL_IP} → 127.0.0.1"
echo "  (解决本机无法用 ${LOCAL_IP} 访问 Docker 容器的问题)"

for port in 5672 5673 5674 15672 15673 15674; do
  echo -n "  ${LOCAL_IP}:${port} → 127.0.0.1:${port} ... "
  # 先删旧的（如果有）
  netsh interface portproxy delete v4tov4 \
    listenport="${port}" listenaddress="${LOCAL_IP}" >/dev/null 2>&1 || true
  # 添加转发
  netsh interface portproxy add v4tov4 \
    listenport="${port}" listenaddress="${LOCAL_IP}" \
    connectport="${port}" connectaddress="127.0.0.1" >/dev/null 2>&1 && echo "✓" || echo "✗"
done

echo ""
echo "============================================"
echo " 完成！"
echo ""
echo " 本机访问:  http://localhost:15672  或  http://${LOCAL_IP}:15672"
echo " 其他机器:  http://${LOCAL_IP}:15672"
echo ""
echo " 验证: netsh interface portproxy show v4tov4"
echo "============================================"
