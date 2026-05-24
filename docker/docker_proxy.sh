#!/usr/bin/env bash
# ========================================================================================
# Docker 代理配置脚本 (Docker Proxy Configuration)
# ========================================================================================
#
# 功能说明:
#   本脚本用于配置/移除 Docker 的 HTTP/HTTPS 代理。
#
# 使用方式:
#   1. 启用代理:     bash docker_proxy.sh enable
#   2. 禁用代理:     bash docker_proxy.sh disable
#   3. 查看状态:     bash docker_proxy.sh status
#   4. 自定义代理:   PROXY_HOST=192.168.1.100 PROXY_PORT=1080 bash docker_proxy.sh enable
#
# 环境变量:
#   PROXY_HOST     - 代理服务器地址 (默认: 192.168.2.3)
#   PROXY_PORT     - 代理端口 (默认: 7890)
#   PROXY_PROTOCOL - 代理协议 (默认: http)
#
# 作者: 系统运维脚本
# 版本: v1.0
# 更新时间: 2024-01
# ========================================================================================

if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="${LOG_FILE:-/var/log/docker/proxy_config.log}"

# 默认代理配置
PROXY_HOST="${PROXY_HOST:-192.168.2.3}"
PROXY_PORT="${PROXY_PORT:-7890}"
PROXY_PROTOCOL="${PROXY_PROTOCOL:-http}"
NO_PROXY="localhost,127.0.0.1,::1"

SYSTEMD_DOCKER_DIR="/etc/systemd/system/docker.service.d"
HTTP_PROXY_CONF="${SYSTEMD_DOCKER_DIR}/http-proxy.conf"

log() {
  local level="$1"; shift
  local msg="$*"
  local ts
  ts="$(date '+%Y-%m-%d %H:%M:%S')"
  printf '[%s] [%s] %s\n' "$ts" "$level" "$msg"
  if [[ -n "${LOG_FILE:-}" ]]; then
    # 确保日志目录存在，防止写入失败
    local log_dir
    log_dir="$(dirname "${LOG_FILE}")"
    mkdir -p "${log_dir}" 2>/dev/null || true
    printf '[%s] [%s] %s\n' "$ts" "$level" "$msg" >> "$LOG_FILE" 2>/dev/null || true
  fi
}

log_info() { log INFO "$@"; }
log_warn() { log WARN "$@"; }
log_error() { log ERROR "$@"; }

show_help() {
  cat <<EOF
Docker 代理配置脚本

用法:
  bash $(basename "$0") <命令>

命令:
  enable   - 启用代理
  disable  - 禁用代理
  status   - 查看代理配置状态

环境变量:
  PROXY_HOST     - 代理服务器地址 (默认: 192.168.2.3)
  PROXY_PORT     - 代理端口 (默认: 7890)
  PROXY_PROTOCOL - 代理协议 (默认: http)

示例:
  # 使用默认配置启用代理
  bash $(basename "$0") enable

  # 自定义代理地址
  PROXY_HOST=192.168.1.100 PROXY_PORT=1080 bash $(basename "$0") enable

  # 禁用代理
  bash $(basename "$0") disable

  # 查看状态
  bash $(basename "$0") status
EOF
}

check_root() {
  if [[ $EUID -ne 0 ]]; then
    if command -v sudo >/dev/null 2>&1; then
      log_warn "需要 root 权限，使用 sudo 重新执行..."
      exec sudo -E bash "$0" "$@"
    else
      log_error "需要 root 权限"
      exit 1
    fi
  fi
}

# 查看状态
do_status() {
  echo ""
  echo "=============================================="
  echo "         Docker 代理配置状态"
  echo "=============================================="
  echo ""

  if [[ -f "${HTTP_PROXY_CONF}" ]]; then
    echo "代理配置文件: ${HTTP_PROXY_CONF}"
    echo ""
    echo "配置内容:"
    cat "${HTTP_PROXY_CONF}"
    echo ""

    # 提取代理地址
    HTTP_PROXY=$(grep "HTTP_PROXY=" "${HTTP_PROXY_CONF}" | cut -d'"' -f2 | sed 's|http://||')
    HTTPS_PROXY=$(grep "HTTPS_PROXY=" "${HTTP_PROXY_CONF}" | cut -d'"' -f2 | sed 's|http://||')
    echo "代理地址: ${HTTP_PROXY}"
  else
    echo "代理配置文件不存在"
    echo ""
    echo "提示: 使用 'bash $0 enable' 启用代理"
  fi

  echo ""
  echo "Docker info 显示的代理配置:"
  if command -v docker >/dev/null 2>&1; then
    docker info 2>/dev/null | grep -A 5 "Http Proxy" || echo "  未检测到代理配置"
  fi

  echo ""
  echo "=============================================="
}

# 启用代理
do_enable() {
  log_info "=== 启用 Docker 代理 ==="
  log_info "代理地址: ${PROXY_PROTOCOL}://${PROXY_HOST}:${PROXY_PORT}"

  check_root

  # 创建目录
  mkdir -p "${SYSTEMD_DOCKER_DIR}"

  # 如果已有代理配置文件，先备份
  if [[ -f "${HTTP_PROXY_CONF}" ]]; then
    local BACKUP_FILE="${HTTP_PROXY_CONF}.backup.$(date +%Y%m%d_%H%M%S)"
    cp "${HTTP_PROXY_CONF}" "${BACKUP_FILE}"
    log_info "已备份现有代理配置到: ${BACKUP_FILE}"
  fi

  # 构建代理URL
  local PROXY_URL="${PROXY_PROTOCOL}://${PROXY_HOST}:${PROXY_PORT}"

  cat > "${HTTP_PROXY_CONF}" <<EOF
[Service]
Environment="HTTP_PROXY=${PROXY_URL}"
Environment="HTTPS_PROXY=${PROXY_URL}"
Environment="NO_PROXY=${NO_PROXY}"
EOF

  log_info "代理配置已写入: ${HTTP_PROXY_CONF}"
  echo ""
  cat "${HTTP_PROXY_CONF}"
  echo ""

  # 重新加载 systemd
  log_info "重新加载 systemd 配置..."
  systemctl daemon-reload

  # 重启 Docker
  log_info "重启 Docker 服务..."
  if systemctl restart docker; then
    log_info "✓ Docker 服务重启成功"
  else
    log_error "✗ Docker 服务重启失败"
    exit 1
  fi

  sleep 2

  echo ""
  log_info "=============================================="
  log_info "=== 代理配置完成 ==="
  log_info "=============================================="
  log_info "代理地址: ${PROXY_URL}"
  log_info "配置文件: ${HTTP_PROXY_CONF}"
  log_info ""
  log_info "验证代理配置:"
  log_info "  docker info | grep -A 5 Proxy"
  log_info ""
  log_info "禁用代理:"
  log_info "  bash $0 disable"
}

# 禁用代理
do_disable() {
  log_info "=== 禁用 Docker 代理 ==="

  check_root

  if [[ -f "${HTTP_PROXY_CONF}" ]]; then
    # 备份
    local BACKUP="${HTTP_PROXY_CONF}.backup.$(date +%Y%m%d_%H%M%S)"
    cp "${HTTP_PROXY_CONF}" "${BACKUP}"
    log_info "已备份配置到: ${BACKUP}"

    # 删除
    rm -f "${HTTP_PROXY_CONF}"
    log_info "已删除代理配置文件"
  else
    log_info "代理配置文件不存在，无需清理"
  fi

  # 重新加载 systemd
  log_info "重新加载 systemd 配置..."
  systemctl daemon-reload

  # 重启 Docker
  log_info "重启 Docker 服务..."
  if systemctl restart docker; then
    log_info "✓ Docker 服务重启成功"
  else
    log_error "✗ Docker 服务重启失败"
    exit 1
  fi

  echo ""
  log_info "=============================================="
  log_info "=== 代理已禁用 ==="
  log_info "=============================================="
  log_info "Docker 已恢复为无代理配置"
  log_info ""
  log_info "重新启用代理:"
  log_info "  bash $0 enable"
}

# 主函数
main() {
  local command="${1:-}"

  case "${command}" in
    enable)
      do_enable
      ;;
    disable)
      do_disable
      ;;
    status)
      do_status
      ;;
    -h|--help)
      show_help
      ;;
    *)
      echo "用法: bash $(basename "$0") <enable|disable|status>"
      echo "使用 -h 或 --help 查看帮助"
      exit 1
      ;;
  esac
}

main "$@"
