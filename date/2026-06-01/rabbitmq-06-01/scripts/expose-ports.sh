#!/usr/bin/env bash
# ============================================================================
# expose-ports.sh —— Windows 防火墙开放 RabbitMQ 集群端口
# 用法：右键 Git Bash → 以管理员身份运行 → bash scripts/expose-ports.sh
# ============================================================================

echo "============================================"
echo " Windows 防火墙开放 RabbitMQ 集群端口"
echo "============================================"
echo ""

for port in 5672 5673 5674 15672 15673 15674; do
  echo -n "  开放端口 ${port} ... "
  netsh advfirewall firewall add rule \
    name="RabbitMQ-Port-${port}" \
    dir=in \
    action=allow \
    protocol=TCP \
    localport="${port}" \
    >/dev/null 2>&1 && echo "✓" || echo "已存在（跳过）"
done

echo ""
echo "============================================"
echo " 完成！端口: 5672-5674 (AMQP) + 15672-15674 (管理界面)"
echo "============================================"
