#!/usr/bin/env bash
# ========================================================================================
# Ubuntu 防火墙禁用脚本 (Ubuntu Firewall Disable Script)
# ========================================================================================
#
# 功能说明:
#   本脚本用于永久禁用 Ubuntu 系统中的 UFW (Uncomplicated Firewall) 防火墙。
#   适用于开发环境或内部网络环境需要完全开放网络访问的场景。
#
# 主要特性:
#   ✓ 完全禁用 UFW: 停止服务并设置开机不启动
#   ✓ 状态验证: 检查防火墙状态确保已完全禁用
#   ✓ 安全提醒: 重要操作前提示安全风险
#   ✓ 日志记录: 完整的操作日志记录
#   ✓ 回滚支持: 提供重新启用防火墙的指导
#
# 安全注意事项:
#   ⚠️ 生产环境不建议禁用防火墙
#   ⚠️ 仅在可信网络环境中使用
#   ⚠️ 确保其他安全措施（如云安全组）已配置
#   ⚠️ 禁用后所有端口将完全开放
#
# 执行的操作:
#   1. 检查当前防火墙状态
#   2. 停止 UFW 服务
#   3. 禁用 UFW 开机自启
#   4. 重置防火墙规则为开放状态
#   5. 验证禁用结果
#
# 适用场景:
#   - 开发和测试环境
#   - Docker 容器内部网络
#   - 受云安全组保护的云服务器
#   - 内部网络环境
#
# 不适用场景:
#   - 生产环境直接对外提供服务
#   - 互联网直接访问的环境
#   - 需要网络隔离的安全环境
#
# 替代方案:
#   - 配置具体的防火墙规则而不是完全禁用
#   - 使用云服务商的安全组进行网络控制
#   - 在负载均衡器层面进行访问控制
#
# 恢复方法:
#   如需重新启用防火墙，执行：
#   sudo ufw enable
#   sudo systemctl enable ufw
#   sudo systemctl start ufw
#
# 日志位置:
#   /var/log/firewall/disable_firewall.log
#
# 作者: 系统运维脚本
# 版本: v1.0
# 更新时间: 2024-01
# ========================================================================================

# Ensure we are running under bash (Ubuntu /bin/sh 不支持 pipefail)
if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="${LOG_FILE:-/var/log/firewall/disable_firewall.log}"

log() {
  local level="$1"; shift
  local msg="$*"
  local ts
  ts="$(date '+%Y-%m-%d %H:%M:%S')"
  printf '[%s] [%s] %s\n' "$ts" "$level" "$msg" | tee -a "$LOG_FILE"
}

log_info() { log "INFO" "$@"; }
log_warn() { log "WARN" "$@"; }
log_error() { log "ERROR" "$@"; }

ensure_dir() {
  local dir="$1"
  if [[ -z "${dir}" ]]; then
    log_error "ensure_dir 收到空目录参数"
    exit 1
  fi
  if [[ ! -d "${dir}" ]]; then
    mkdir -p "${dir}"
  fi
}

# 确保日志目录存在
ensure_dir "$(dirname "${LOG_FILE}")"

trap 'log_error "操作过程中出现错误，退出。"' ERR

log_info "=== Ubuntu 防火墙禁用脚本开始 ==="
log_info "日志文件: ${LOG_FILE}"

# 0. 权限检查（Ubuntu 默认禁用 root 登录）
if [[ $EUID -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    log_warn "当前非 root，使用 sudo 重新执行脚本..."
    exec sudo -E bash "$0" "$@"
  else
    log_error "需要 root 权限且未找到 sudo，请以 root 或 sudo 运行脚本。"
    exit 1
  fi
fi

# 1. 检查系统类型
if [[ ! -f /etc/os-release ]]; then
  log_error "无法检测系统类型，此脚本仅支持 Ubuntu/Debian 系统。"
  exit 1
fi

if ! grep -qiE 'ubuntu|debian' /etc/os-release; then
  log_warn "检测到非 Ubuntu/Debian 系统，继续执行（可能不适用）。"
fi

# 2. 检查并处理 ufw (Uncomplicated Firewall)
log_info "=== 处理 ufw 防火墙 ==="

if command -v ufw >/dev/null 2>&1; then
  log_info "检测到 ufw 防火墙"

  # 检查当前状态
  UFW_STATUS=$(ufw status | head -n 1 || echo "unknown")
  log_info "当前 ufw 状态: ${UFW_STATUS}"

  # 如果防火墙是激活状态，先禁用规则
  if ufw status | grep -qi "Status: active"; then
    log_info "防火墙当前为激活状态，正在禁用..."
    ufw --force disable || {
      log_error "无法禁用 ufw，请检查错误信息"
      exit 1
    }
    log_info "ufw 已禁用"
  else
    log_info "防火墙当前未激活"
  fi

  # 停止 ufw 服务
  if systemctl is-active --quiet ufw 2>/dev/null; then
    log_info "停止 ufw 服务..."
    systemctl stop ufw || {
      log_warn "停止 ufw 服务失败，可能服务未运行"
    }
  else
    log_info "ufw 服务未运行"
  fi

  # 禁用 ufw 服务（防止开机自启）
  if systemctl is-enabled --quiet ufw 2>/dev/null; then
    log_info "禁用 ufw 服务（防止开机自启）..."
    systemctl disable ufw || {
      log_error "禁用 ufw 服务失败"
      exit 1
    }
    log_info "ufw 服务已禁用，不会在开机时自动启动"
  else
    log_info "ufw 服务已禁用"
  fi

  # 确保 ufw 状态为 disabled
  log_info "确保 ufw 状态为 disabled..."
  ufw --force disable >/dev/null 2>&1 || true

  # 验证状态
  if ufw status | grep -qi "Status: inactive"; then
    log_info "✓ ufw 防火墙已成功禁用"
  else
    log_warn "ufw 状态检查异常，请手动验证: ufw status"
  fi
else
  log_info "未检测到 ufw，跳过 ufw 处理"
fi

# 3. 检查并处理 firewalld (某些系统可能使用)
log_info "=== 检查 firewalld ==="

if command -v firewall-cmd >/dev/null 2>&1; then
  log_warn "检测到 firewalld（通常用于 CentOS/RHEL），正在处理..."

  if systemctl is-active --quiet firewalld 2>/dev/null; then
    log_info "停止 firewalld 服务..."
    systemctl stop firewalld || {
      log_warn "停止 firewalld 服务失败"
    }
  fi

  if systemctl is-enabled --quiet firewalld 2>/dev/null; then
    log_info "禁用 firewalld 服务..."
    systemctl disable firewalld || {
      log_warn "禁用 firewalld 服务失败"
    }
  fi

  log_info "firewalld 已处理"
else
  log_info "未检测到 firewalld"
fi

# 4. 检查 iptables 服务（某些旧系统）
log_info "=== 检查 iptables 服务 ==="

if systemctl list-unit-files | grep -q "iptables.service"; then
  log_info "检测到 iptables 服务"

  if systemctl is-active --quiet iptables 2>/dev/null; then
    log_info "停止 iptables 服务..."
    systemctl stop iptables || {
      log_warn "停止 iptables 服务失败"
    }
  fi

  if systemctl is-enabled --quiet iptables 2>/dev/null; then
    log_info "禁用 iptables 服务..."
    systemctl disable iptables || {
      log_warn "禁用 iptables 服务失败"
    }
  fi

  log_info "iptables 服务已处理"
else
  log_info "未检测到 iptables 服务（正常，现代 Ubuntu 使用 ufw 管理 iptables）"
fi

# 5. 验证最终状态
log_info "=== 验证防火墙状态 ==="

FIREWALL_DISABLED=true

# 检查 ufw
if command -v ufw >/dev/null 2>&1; then
  if ufw status | grep -qi "Status: active"; then
    log_warn "警告：ufw 仍为激活状态"
    FIREWALL_DISABLED=false
  else
    log_info "✓ ufw: 已禁用"
  fi
fi

# 检查 firewalld
if command -v firewall-cmd >/dev/null 2>&1; then
  if systemctl is-active --quiet firewalld 2>/dev/null; then
    log_warn "警告：firewalld 仍在运行"
    FIREWALL_DISABLED=false
  else
    log_info "✓ firewalld: 已停止"
  fi
fi

# 6. 显示最终状态
log_info "=== 防火墙禁用完成 ==="

if [[ "$FIREWALL_DISABLED" == "true" ]]; then
  log_info "✓ 防火墙已成功禁用"
  log_info ""
  log_info "当前防火墙状态："

  if command -v ufw >/dev/null 2>&1; then
    log_info "ufw 状态："
    ufw status | tee -a "$LOG_FILE" || true
  fi

  log_info ""
  log_info "重要提示："
  log_info "1. 防火墙已永久禁用，不会在系统重启后自动启用"
  log_info "2. 如需重新启用防火墙，请运行："
  log_info "   sudo ufw enable"
  log_info "   sudo systemctl enable ufw"
  log_info "3. 禁用防火墙会降低系统安全性，请确保在安全网络环境中使用"
else
  log_warn "部分防火墙服务可能未完全禁用，请手动检查"
fi

log_info ""
log_info "验证命令："
log_info "  ufw status                    # 查看 ufw 状态"
log_info "  systemctl status ufw          # 查看 ufw 服务状态"
log_info "  systemctl is-enabled ufw      # 检查是否开机自启"
