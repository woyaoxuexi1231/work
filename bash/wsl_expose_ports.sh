#!/usr/bin/env bash
# =============================================================================
# WSL 端口暴露脚本 — 将所有服务端口从 Windows 转发到 WSL2
# 
# 原理: netsh portproxy + Windows Firewall
# 用法: sudo bash wsl_expose_ports.sh          # 暴露所有端口
#       sudo bash wsl_expose_ports.sh --clean  # 清理所有规则
#       sudo bash wsl_expose_ports.sh --list   # 列出已有规则
# =============================================================================
set -euo pipefail

# ---- 所有需要暴露的端口 ----
PORTS=(
  2375   # Docker API
  3306   # MySQL
  6379   # Redis
  27017  # MongoDB
  9200   # Elasticsearch HTTP
  9300   # Elasticsearch Transport
  9000   # MinIO API
  9001   # MinIO Console
  8848   # Nacos HTTP
  9848   # Nacos gRPC
  9849   # Nacos gRPC-Server
  8500   # Consul HTTP
  8600   # Consul DNS
  2181   # ZooKeeper Client
  2888   # ZooKeeper Peer
  3888   # ZooKeeper Election
  8181   # ZooKeeper Admin
  9092   # Kafka
  9093   # Kafka Controller
  9097   # Kafka UI
  5672   # RabbitMQ AMQP
  15672  # RabbitMQ Management
  9876   # RocketMQ NameServer
  10911  # RocketMQ Broker
  10912  # RocketMQ Broker HA
  10909  # RocketMQ Broker QA
  8080   # RocketMQ Proxy HTTP
  8081   # RocketMQ Proxy gRPC
  8888   # RocketMQ Dashboard
  19530  # Milvus API
  9091   # Milvus Metrics
  3000   # Grafana + Attu
  9090   # Prometheus
  9100   # Node Exporter
)

RULE_PREFIX="WSL2_DEV"

log()  { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
warn() { log "WARN: $*"; }

# ---- 环境检测 ----
detect_wsl() {
  if ! grep -qi microsoft /proc/version 2>/dev/null && ! grep -qi WSL /proc/version 2>/dev/null && ! command -v netsh.exe >/dev/null 2>&1; then
    log "未检测到 WSL 环境，脚本仅在 WSL2 中有意义"
    return 1
  fi
  return 0
}

get_wsl_ip() {
  ip route get 1 2>/dev/null | awk '{print $7; exit}' || \
    hostname -I 2>/dev/null | awk '{print $1}' || \
    ip -4 addr show eth0 2>/dev/null | grep -oP 'inet \K[\d.]+' || \
    echo "127.0.0.1"
}

# ---- 清理 ----
clean_rules() {
  log "清理所有 ${RULE_PREFIX} 规则..."
  
  for port in "${PORTS[@]}"; do
    netsh.exe interface portproxy delete v4tov4 listenport=${port} listenaddress=0.0.0.0 2>/dev/null || true
  done

  # 删除防火墙规则
  for port in "${PORTS[@]}"; do
    netsh.exe advfirewall firewall delete rule name="${RULE_PREFIX}_${port}" 2>/dev/null || true
  done

  log "清理完成"
}

# ---- 列出 ----
list_rules() {
  log "=== 当前端口转发规则 ==="
  netsh.exe interface portproxy show v4tov4 2>/dev/null || true
  log ""
  log "=== 当前防火墙规则 (${RULE_PREFIX}) ==="
  netsh.exe advfirewall firewall show rule name=all dir=in 2>/dev/null | grep -i "${RULE_PREFIX}" || echo "(无)"
}

# ---- 暴露 ----
expose_ports() {
  local wsl_ip="$1"
  log "WSL IP: ${wsl_ip}"
  log "正在暴露 ${#PORTS[@]} 个端口..."

  local ok=0 fail=0
  for port in "${PORTS[@]}"; do
    # 删除旧规则
    netsh.exe interface portproxy delete v4tov4 listenport=${port} listenaddress=0.0.0.0 2>/dev/null || true

    # 添加 portproxy
    if netsh.exe interface portproxy add v4tov4 \
      listenport=${port} listenaddress=0.0.0.0 \
      connectport=${port} connectaddress=${wsl_ip} 2>/dev/null; then
      ((ok++))
    else
      warn "portproxy 失败: ${port}"
      ((fail++))
    fi

    # 防火墙规则
    netsh.exe advfirewall firewall add rule \
      name="${RULE_PREFIX}_${port}" \
      dir=in action=allow protocol=TCP \
      localport=${port} 2>/dev/null || true
  done

  log "完成: ${ok} 成功, ${fail} 失败"
  log ""
  log "验证: 在 Windows PowerShell 中运行:"
  log "  netsh interface portproxy show v4tov4"
  log "  Test-NetConnection -ComputerName localhost -Port 3306"
}

# ---- 主入口 ----
WSL_IP=$(get_wsl_ip)

case "${1:-}" in
  --clean|-c)
    clean_rules
    ;;
  --list|-l)
    list_rules
    ;;
  --help|-h)
    echo "用法: sudo bash $0 [--clean|-c] [--list|-l]"
    echo "  无参数  暴露所有服务端口"
    echo "  --clean 清理所有 WSL 端口转发和防火墙规则"
    echo "  --list  列出当前规则"
    exit 0
    ;;
  *)
    detect_wsl || true
    clean_rules
    expose_ports "${WSL_IP}"
    ;;
esac
