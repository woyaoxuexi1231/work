#!/usr/bin/env bash
# =============================================================================
# Windows 端口开放脚本 — 开放所有服务端口到 Windows 防火墙 (Git Bash)
# 用法: bash wsl_expose_ports.sh          # 添加规则
#       bash wsl_expose_ports.sh --clean  # 删除规则
#       bash wsl_expose_ports.sh --list   # 查看规则
# =============================================================================
set -euo pipefail

PORTS=(2375 3306 6379 27017 9200 9300 9000 9001 8848 9848 9849 8500 8600 2181
       2888 3888 8181 9092 9093 9097 5672 15672 9876 10911 10912 10909
       8080 8081 8888 19530 9091 3000 9090 9100)

PREFIX="DockerDev"

add_rule() {
  for p in "${PORTS[@]}"; do
    netsh.exe advfirewall firewall add rule name="${PREFIX}_${p}" dir=in action=allow protocol=TCP localport=${p} 2>/dev/null && echo "  + ${p}" || echo "  ! ${p}"
  done
  echo "Done."
}

del_rule() {
  for p in "${PORTS[@]}"; do
    netsh.exe advfirewall firewall delete rule name="${PREFIX}_${p}" 2>/dev/null && echo "  - ${p}" || true
  done
  echo "Done."
}

list_rules() {
  netsh.exe advfirewall firewall show rule name=all dir=in 2>/dev/null | grep -i "${PREFIX}" || echo "(none)"
}

case "${1:-}" in
  --clean|-c) del_rule ;;
  --list|-l)  list_rules ;;
  --help|-h)  echo "Usage: bash $0 [--clean|-c] [--list|-l]"; exit 0 ;;
  *)          add_rule ;;
esac
