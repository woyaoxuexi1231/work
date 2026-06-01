#!/usr/bin/env bash
# ============================================================================
# expose-ports.sh —— Windows 防火墙开放 RabbitMQ 集群端口
# Git Bash 中以管理员身份运行：右键 Git Bash → 以管理员身份运行
# ============================================================================
set -euo pipefail

echo "============================================"
echo " Windows 防火墙开放 RabbitMQ 集群端口"
echo "============================================"
echo ""

# AMQP 端口
for port in 5672 5673 5674; do
  echo -n "  开放 AMQP ${port} ... "
  cmd //c "netsh advfirewall firewall add rule name=\"RabbitMQ AMQP ${port}\" dir=in action=allow protocol=TCP localport=${port}" 2>&1 | grep -q "Ok" && echo "✓" || echo "(已存在)"
done

# 管理界面端口
for port in 15672 15673 15674; do
  echo -n "  开放 MGMT ${port} ... "
  cmd //c "netsh advfirewall firewall add rule name=\"RabbitMQ MGMT ${port}\" dir=in action=allow protocol=TCP localport=${port}" 2>&1 | grep -q "Ok" && echo "✓" || echo "(已存在)"
done

echo ""
echo "============================================"
echo " 完成！已开放端口: 5672-5674, 15672-15674"
echo "============================================"
