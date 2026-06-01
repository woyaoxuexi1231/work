#!/usr/bin/env bash
# =============================================================================
# lib/common.sh — 公共函数库 (Docker Desktop / Git Bash 兼容)
# 所有组件脚本 source 引用
# =============================================================================

set -euo pipefail

# ---- logging ----
log() {
  local level="$1"; shift
  printf '[%s] [%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$level" "$*"
}

log_info()  { log "INFO"  "$@"; }
log_warn()  { log "WARN"  "$@"; }
log_error() { log "ERROR" "$@"; }

# ---- docker check ----
check_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    log_error "Docker 未安装，请先安装 Docker Desktop"
    exit 1
  fi
  if ! docker ps >/dev/null 2>&1; then
    log_error "Docker 未运行，请启动 Docker Desktop"
    exit 1
  fi
}

# ---- idempotent container check ----
check_container_exists() {
  local name="$1"
  if docker ps -a --format '{{.Names}}' | grep -qw "${name}"; then
    if docker ps --format '{{.Names}}' | grep -qw "${name}"; then
      log_warn "容器 ${name} 已存在且运行中，跳过"
    else
      log_warn "容器 ${name} 已存在（已停止），如需重建请先 docker rm -f ${name}"
    fi
    return 0
  fi
  return 1
}

# ---- cleanup before reinstall ----
cleanup_container() {
  local name="$1"
  if docker ps -a --format '{{.Names}}' | grep -qw "${name}"; then
    log_info "清理旧容器: ${name}"
    docker rm -f "${name}" 2>/dev/null || true
  fi
}

# ---- wait for container healthy ----
wait_for_container() {
  local name="$1" max="${2:-60}"
  local i=0
  while [[ $i -lt $max ]]; do
    if docker ps --format '{{.Names}}' | grep -qw "${name}"; then
      return 0
    fi
    sleep 1
    ((i++))
  done
  log_error "容器 ${name} 在 ${max}s 内未启动，docker logs ${name} 查看原因"
  return 1
}

# ---- pull image ----
pull_image() {
  local img="$1"
  if docker image inspect "${img}" >/dev/null 2>&1; then
    log_info "镜像 ${img} 已存在，跳过拉取"
  else
    log_info "拉取镜像: ${img}"
    docker pull "${img}" || { log_error "拉取失败: ${img}"; exit 1; }
  fi
}

# ---- done banner ----
done_banner() {
  local component="$1"
  log_info "=== ${component} 安装完成 ==="
}
