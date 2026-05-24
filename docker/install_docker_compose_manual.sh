#!/usr/bin/env bash
# ========================================================================================
# Docker Compose 手动安装脚本
# ========================================================================================
#
# 此脚本提供多种方式手动安装 Docker Compose
# 适用于在 install_docker.sh 自动安装失败时的备用方案
#
# 使用方法:
#   bash install_docker_compose_manual.sh
#
# ========================================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="${LOG_FILE:-/var/log/docker/install_docker_compose.log}"

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

# 确保日志目录存在
mkdir -p "$(dirname "${LOG_FILE}")"

log_info "=== Docker Compose 手动安装开始 ==="

# 检查是否已安装
if command -v docker-compose >/dev/null 2>&1 || docker compose version >/dev/null 2>&1; then
  log_info "Docker Compose 已安装"
  if command -v docker-compose >/dev/null 2>&1; then
    docker-compose --version
  elif docker compose version >/dev/null 2>&1; then
    docker compose version
  fi
  exit 0
fi

# 方法1: 尝试安装官方APT包
log_info "方法1: 尝试安装官方APT包..."
if apt-get update -y && (apt-get install -y docker-compose-plugin 2>/dev/null || apt-get install -y docker-compose 2>/dev/null); then
  log_info "✓ APT包安装成功"
  if command -v docker-compose >/dev/null 2>&1; then
    docker-compose --version
  elif docker compose version >/dev/null 2>&1; then
    docker compose version
  fi
  exit 0
else
  log_warn "APT包安装失败，尝试其他方法..."
fi

# 方法2: 下载官方二进制文件
log_info "方法2: 下载官方二进制文件..."
DOCKER_COMPOSE_URL="https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)"

if command -v curl >/dev/null 2>&1; then
  if curl -L "$DOCKER_COMPOSE_URL" -o /usr/local/bin/docker-compose 2>/dev/null; then
    chmod +x /usr/local/bin/docker-compose
    if docker-compose --version >/dev/null 2>&1; then
      log_info "✓ 二进制文件安装成功"
      docker-compose --version
      exit 0
    fi
  fi
elif command -v wget >/dev/null 2>&1; then
  if wget -O /usr/local/bin/docker-compose "$DOCKER_COMPOSE_URL" 2>/dev/null; then
    chmod +x /usr/local/bin/docker-compose
    if docker-compose --version >/dev/null 2>&1; then
      log_info "✓ 二进制文件安装成功"
      docker-compose --version
      exit 0
    fi
  fi
else
  log_warn "未找到 curl 或 wget，跳过二进制文件安装"
fi

# 方法3: 使用pip安装
log_info "方法3: 使用pip安装..."
if command -v pip3 >/dev/null 2>&1; then
  if pip3 install docker-compose 2>/dev/null; then
    log_info "✓ pip安装成功"
    docker-compose --version
    exit 0
  fi
elif command -v pip >/dev/null 2>&1; then
  if pip install docker-compose 2>/dev/null; then
    log_info "✓ pip安装成功"
    docker-compose --version
    exit 0
  fi
else
  log_warn "未找到 pip，跳过pip安装"
fi

# 方法4: 使用snap安装
log_info "方法4: 使用snap安装..."
if command -v snap >/dev/null 2>&1; then
  if snap install docker-compose 2>/dev/null; then
    log_info "✓ snap安装成功"
    docker-compose --version
    exit 0
  fi
else
  log_warn "未找到 snap，跳过snap安装"
fi

log_error "所有安装方法都失败了"
log_error "请检查网络连接或手动安装"
log_error ""
log_error "常见问题排查:"
log_error "1. 检查网络: ping github.com"
log_error "2. 检查权限: 确保有root权限"
log_error "3. 检查架构: uname -m"
log_error "4. 手动下载: 访问 https://github.com/docker/compose/releases"
exit 1
