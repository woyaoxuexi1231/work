#!/usr/bin/env bash
# ============================================================================
# portproxy.sh —— 解决本机无法用 192.168.3.100 访问 Docker 容器的问题
# 原因：Windows Docker Desktop 端口只绑 127.0.0.1，不绑物理网卡 IP
# 修法：netsh portproxy 把 192.168.3.100 的流量转发到 127.0.0.1
# 用法：右键 Git Bash → 以管理员身份运行 → bash scripts/portproxy.sh
# ============================================================================

LOCAL_IP="192.168.3.100"

echo "============================================"
echo " 端口转发: ${LOCAL_IP} → 127.0.0.1"
echo "============================================"
echo ""

for port in 5672 5673 5674 15672 15673 15674; do
  echo -n "  ${LOCAL_IP}:${port} → 127.0.0.1:${port} ... "

  # 删旧规则
  netsh interface portproxy delete v4tov4 \
    listenport="${port}" listenaddress="${LOCAL_IP}" >/dev/null 2>&1 || true

  # 添加转发
  netsh interface portproxy add v4tov4 \
    listenport="${port}" listenaddress="${LOCAL_IP}" \
    connectport="${port}" connectaddress="127.0.0.1" >/dev/null 2>&1 && echo "✓" || echo "✗（请以管理员身份运行）"
done

echo ""
echo "============================================"
echo " 完成！现在本机可以用 ${LOCAL_IP} 访问了"
echo ""
echo " 验证: curl http://${LOCAL_IP}:15672"
echo "============================================"
