#!/usr/bin/env bash
# ========================================================================================
# Docker 镜像源切换脚本 (Docker Registry Switcher)
# ========================================================================================
#
# 功能说明:
#   本脚本用于切换 Docker 的镜像源（Registry Mirror），加速镜像拉取。
#   支持通过参数或环境变量选择镜像源。
#
# 支持的镜像源:
#   - official  : 官方 Docker Hub (默认)
#   - aliyun    : 阿里云镜像加速器
#   - tencent   : 腾讯云镜像加速器
#   - azure     : Azure 中国镜像
#   - netease   : 网易镜像
#   - douyin    : 抖音镜像
#   - 163       : 网易163镜像
#   - custom    : 自定义镜像源
#
# 使用方式:
#   1. 通过参数选择镜像源:
#      bash docker_registry.sh aliyun
#      bash docker_registry.sh tencent
#      bash docker_registry.sh official
#
#   2. 通过环境变量选择:
#      DOCKER_REGISTRY=aliyun bash docker_registry.sh
#      DOCKER_REGISTRY=tencent bash docker_registry.sh
#
#   3. 查看当前配置:
#      bash docker_registry.sh status
#
#   4. 自定义镜像源:
#      DOCKER_REGISTRY=custom CUSTOM_REGISTRY="https://mirror.example.com" bash docker_registry.sh
#
#   5. 清理配置（恢复官方）:
#      bash docker_registry.sh clean
#      bash docker_registry.sh official
#
# 配置位置:
#   /etc/docker/daemon.json
#
# 作者: 系统运维脚本
# 版本: v1.0
# 更新时间: 2024-01
# ========================================================================================

# Ensure we are running under bash
if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DAEMON_JSON="/etc/docker/daemon.json"
DAEMON_JSON_BACKUP="${DAEMON_JSON}.backup.$(date +%Y%m%d_%H%M%S)"
LOG_FILE="${LOG_FILE:-/var/log/docker/registry_switch.log}"

# Docker 镜像源配置
declare -A REGISTRY_CONFIGS
REGISTRY_CONFIGS=(
    ["official"]=""
    ["aliyun"]="[\"https://1d7bgakn.mirror.aliyuncs.com\"]"
    ["tencent"]="[\"https://mirror.ccs.tencentyun.com\"]"
    ["azure"]="[\"https://dockerhub.azk8s.cn\"]"
    ["netease"]="[\"https://hub-mirror.c.163.com\"]"
    ["douyin"]="[\"https://docker.mirrors.dflux.tech\"]"
    ["163"]="[\"https://hub-mirror.c.163.com\"]"
)

# 镜像源说明
declare -A REGISTRY_DESCRIPTIONS
REGISTRY_DESCRIPTIONS=(
    ["official"]="官方 Docker Hub (hub.docker.com)"
    ["aliyun"]="阿里云镜像加速器 (免费，需要登录阿里云获取专属加速器)"
    ["tencent"]="腾讯云镜像加速器 (免费，需要腾讯云账号)"
    ["azure"]="Azure 中国镜像 (由世纪互联运营)"
    ["netease"]="网易镜像 (免费，无需登录)"
    ["douyin"]="抖音镜像 (免费，无需登录)"
    ["163"]="网易163镜像 (免费，无需登录)"
    ["custom"]="自定义镜像源"
)

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

# 显示使用帮助
show_help() {
  cat <<EOF
Docker 镜像源切换脚本

用法:
  bash $(basename "$0") [选项] [镜像源]

镜像源:
  official  - 官方 Docker Hub (默认)
  aliyun    - 阿里云镜像加速器
  tencent   - 腾讯云镜像加速器
  azure     - Azure 中国镜像
  netease   - 网易镜像
  douyin    - 抖音镜像
  163       - 网易163镜像 (等同于 netease)
  custom    - 自定义镜像源 (需配合 CUSTOM_REGISTRY)
  status    - 查看当前配置状态
  clean     - 清理镜像配置，恢复官方

选项:
  -h, --help     显示帮助信息
  -v, --version  显示版本信息

环境变量:
  DOCKER_REGISTRY    - 指定镜像源 (优先级低于命令行参数)
  CUSTOM_REGISTRY    - 自定义镜像源地址 (用于 custom 模式)

示例:
  # 使用阿里云镜像
  bash $(basename "$0") aliyun

  # 使用腾讯云镜像
  bash $(basename "$0") tencent

  # 恢复官方镜像
  bash $(basename "$0") official

  # 使用环境变量
  DOCKER_REGISTRY=aliyun bash $(basename "$0")

  # 使用自定义镜像源
  CUSTOM_REGISTRY="https://mirror.example.com" bash $(basename "$0") custom

  # 查看当前配置
  bash $(basename "$0") status

  # 清理配置
  bash $(basename "$0") clean

说明:
  1. 阿里云镜像需要登录阿里云控制台获取专属加速器地址:
     https://cr.console.aliyun.com/cn-hangzhou/instances/mirrors

  2. 腾讯云镜像需要登录腾讯云容器镜像服务:
     https://console.cloud.tencent.com/tke2/registry

  3. 修改配置后 Docker 服务会自动重启使配置生效
EOF
}

# 显示版本
show_version() {
  echo "Docker Registry Switcher v1.0"
  echo "更新时间: 2024-01"
}

# 获取命令行参数
parse_args() {
  # 默认值
  REGISTRY_TYPE="${DOCKER_REGISTRY:-official}"
  CUSTOM_REGISTRY_VALUE="${CUSTOM_REGISTRY:-}"

  # 解析命令行参数
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -h|--help)
        show_help
        exit 0
        ;;
      -v|--version)
        show_version
        exit 0
        ;;
      official|aliyun|tencent|azure|netease|douyin|163|custom|status|clean)
        REGISTRY_TYPE="$1"
        shift
        ;;
      -*)
        log_error "未知选项: $1"
        echo "使用 -h 或 --help 查看帮助"
        exit 1
        ;;
      *)
        log_error "未知参数: $1"
        echo "使用 -h 或 --help 查看帮助"
        exit 1
        ;;
    esac
  done

  # 如果是 custom 但没有设置自定义地址，尝试从环境变量获取
  if [[ "${REGISTRY_TYPE}" == "custom" ]] && [[ -z "${CUSTOM_REGISTRY_VALUE}" ]]; then
    log_error "选择了自定义镜像源 (custom) 但未设置 CUSTOM_REGISTRY 环境变量"
    log_error "请设置: CUSTOM_REGISTRY=\"https://your-mirror.com\" bash $0 custom"
    exit 1
  fi
}

# 检查 root 权限
check_root() {
  if [[ $EUID -ne 0 ]]; then
    if command -v sudo >/dev/null 2>&1; then
      log_warn "需要 root 权限，使用 sudo 重新执行..."
      exec sudo -E bash "$0" "$@"
    else
      log_error "需要 root 权限且未找到 sudo，请以 root 或 sudo 运行脚本。"
      exit 1
    fi
  fi
}

# 确保 jq 已安装
ensure_jq() {
  if ! command -v jq >/dev/null 2>&1; then
    log_info "安装 jq..."
    export DEBIAN_FRONTEND=noninteractive
    if ! apt-get update -y >/dev/null 2>&1 || ! apt-get install -y jq >/dev/null 2>&1; then
      log_error "jq 安装失败"
      exit 1
    fi
  fi
}

# 确保 daemon.json 存在且为合法 JSON
ensure_daemon_json() {
  local daemon_dir
  daemon_dir="$(dirname "${DAEMON_JSON}")"
  ensure_dir "${daemon_dir}"

  if [[ ! -f "${DAEMON_JSON}" ]]; then
    echo '{}' > "${DAEMON_JSON}"
    log_info "已创建配置文件: ${DAEMON_JSON}"
    return 0
  fi

  # 验证是否为合法 JSON，不合法则备份后重建
  if ! jq empty "${DAEMON_JSON}" 2>/dev/null; then
    local invalid_backup="${DAEMON_JSON}.invalid.$(date +%Y%m%d_%H%M%S)"
    cp "${DAEMON_JSON}" "${invalid_backup}"
    log_warn "daemon.json 不是合法的 JSON，已备份到: ${invalid_backup}"
    echo '{}' > "${DAEMON_JSON}"
    log_info "已重新创建合法的 daemon.json"
  fi
}

# 备份配置
backup_config() {
  if [[ -f "${DAEMON_JSON}" ]]; then
    cp "${DAEMON_JSON}" "${DAEMON_JSON_BACKUP}"
    log_info "已备份配置到: ${DAEMON_JSON_BACKUP}"
  fi
}

# 查看当前状态
show_status() {
  echo ""
  echo "=============================================="
  echo "         Docker 镜像源配置状态"
  echo "=============================================="
  echo ""

  # 检查 Docker 是否安装
  if ! command -v docker >/dev/null 2>&1; then
    log_error "Docker 未安装"
    exit 1
  fi

  # 显示当前 daemon.json 配置
  echo "配置文件: ${DAEMON_JSON}"
  echo ""
  if [[ -f "${DAEMON_JSON}" ]]; then
    echo "当前配置内容:"
    cat "${DAEMON_JSON}"
  else
    echo "配置文件不存在"
  fi
  echo ""

  # 显示 registry-mirrors (如果有)
  if command -v docker >/dev/null 2>&1; then
    echo "Docker info 显示的镜像源:"
    docker info 2>/dev/null | grep -A 20 "Registry Mirrors" || echo "  未配置镜像源"
    echo ""
  fi

  echo "支持的镜像源:"
  echo ""
  for key in official aliyun tencent azure netease douyin 163 custom; do
    local desc="${REGISTRY_DESCRIPTIONS[$key]}"
    printf "  %-10s - %s\n" "$key" "$desc"
  done
  echo ""
  echo "=============================================="
}

# 清理配置（恢复官方）
clean_config() {
  log_info "清理 Docker 镜像源配置..."

  ensure_jq
  ensure_daemon_json
  backup_config

  # 移除 registry-mirrors 配置
  if jq -e '.["registry-mirrors"]' "${DAEMON_JSON}" >/dev/null 2>&1; then
    if jq 'del(."registry-mirrors")' "${DAEMON_JSON}" > "${DAEMON_JSON}.tmp" 2>/dev/null; then
      mv "${DAEMON_JSON}.tmp" "${DAEMON_JSON}"
      log_info "已移除 registry-mirrors 配置"
    else
      rm -f "${DAEMON_JSON}.tmp"
      log_error "写入 ${DAEMON_JSON} 失败，原文件未修改，请检查磁盘空间或权限"
      exit 1
    fi
  else
    log_info "registry-mirrors 配置不存在，无需清理"
  fi

  log_info "✓ 配置清理完成，Docker 将使用官方 Docker Hub"
}

# 设置镜像源
set_registry() {
  local registry_type="$1"
  local registry_value="$2"

  log_info "设置 Docker 镜像源: ${registry_type}"

  ensure_jq
  ensure_daemon_json
  backup_config

  # 清理现有 registry-mirrors
  if jq -e '.["registry-mirrors"]' "${DAEMON_JSON}" >/dev/null 2>&1; then
    if jq 'del(."registry-mirrors")' "${DAEMON_JSON}" > "${DAEMON_JSON}.tmp" 2>/dev/null; then
      mv "${DAEMON_JSON}.tmp" "${DAEMON_JSON}"
    else
      rm -f "${DAEMON_JSON}.tmp"
      log_error "写入 ${DAEMON_JSON} 失败，原文件未修改"
      exit 1
    fi
  fi

  # 根据类型设置镜像源
  if [[ "${registry_type}" == "official" ]]; then
    log_info "使用官方 Docker Hub"
  elif [[ "${registry_type}" == "custom" ]]; then
    # 自定义镜像源
    local custom_json="[\"${registry_value}\"]"
    if jq --argjson mirrors "${custom_json}" '. + {"registry-mirrors": $mirrors}' "${DAEMON_JSON}" > "${DAEMON_JSON}.tmp" 2>/dev/null; then
      mv "${DAEMON_JSON}.tmp" "${DAEMON_JSON}"
    else
      rm -f "${DAEMON_JSON}.tmp"
      log_error "写入自定义镜像源失败，原文件未修改"
      exit 1
    fi
    log_info "使用自定义镜像源: ${registry_value}"
  else
    # 预设镜像源
    if [[ -n "${registry_value}" ]]; then
      if jq --argjson mirrors "${registry_value}" '. + {"registry-mirrors": $mirrors}' "${DAEMON_JSON}" > "${DAEMON_JSON}.tmp" 2>/dev/null; then
        mv "${DAEMON_JSON}.tmp" "${DAEMON_JSON}"
      else
        rm -f "${DAEMON_JSON}.tmp"
        log_error "写入镜像源配置失败，原文件未修改"
        exit 1
      fi
      log_info "已配置镜像源"
    fi
  fi

  log_info "配置文件已更新: ${DAEMON_JSON}"
  echo ""
  log_info "新的配置内容:"
  cat "${DAEMON_JSON}"
  echo ""
}

# 重启 Docker 服务
restart_docker() {
  log_info "重启 Docker 服务使配置生效..."

  if systemctl restart docker; then
    log_info "✓ Docker 服务重启成功"
  else
    log_error "✗ Docker 服务重启失败"
    log_error "请检查: systemctl status docker"
    exit 1
  fi

  # 等待 Docker 启动
  sleep 3

  # 验证配置
  log_info "验证配置..."
  if docker info 2>/dev/null | grep -q "Registry Mirrors"; then
    echo ""
    log_info "当前镜像源配置:"
    docker info 2>/dev/null | grep -A 10 "Registry Mirrors" || true
  fi
}

# 测试镜像拉取
test_pull() {
  local image="$1"
  log_info "测试拉取镜像: ${image}"

  if docker pull "${image}" >/dev/null 2>&1; then
    log_info "✓ 镜像拉取成功: ${image}"
    return 0
  else
    log_warn "✗ 镜像拉取失败: ${image}"
    return 1
  fi
}

# 主函数
main() {
  # 确保日志目录存在
  ensure_dir "$(dirname "${LOG_FILE}")"

  # 解析参数
  parse_args "$@"

  log_info "=== Docker 镜像源切换开始 ==="
  log_info "选择的镜像源: ${REGISTRY_TYPE}"

  # 特殊命令处理
  case "${REGISTRY_TYPE}" in
    status)
      show_status
      exit 0
      ;;
    clean)
      check_root
      clean_config
      restart_docker
      exit 0
      ;;
  esac

  # 检查 root 权限
  check_root

  # 检查 Docker 是否安装
  if ! command -v docker >/dev/null 2>&1; then
    log_error "Docker 未安装，请先安装 Docker"
    exit 1
  fi

  # 检查镜像源是否有效
  if [[ "${REGISTRY_TYPE}" != "official" ]] && [[ "${REGISTRY_TYPE}" != "custom" ]]; then
    if [[ -z "${REGISTRY_CONFIGS[${REGISTRY_TYPE}]:-}" ]]; then
      log_error "不支持的镜像源: ${REGISTRY_TYPE}"
      echo ""
      echo "支持的镜像源:"
      for key in official aliyun tencent azure netease douyin 163 custom; do
        echo "  - ${key}"
      done
      exit 1
    fi
  fi

  # 设置镜像源
  if [[ "${REGISTRY_TYPE}" == "custom" ]]; then
    set_registry "${REGISTRY_TYPE}" "${CUSTOM_REGISTRY}"
  else
    set_registry "${REGISTRY_TYPE}" "${REGISTRY_CONFIGS[${REGISTRY_TYPE}]:-}"
  fi

  # 重启 Docker
  restart_docker

  # 显示说明
  echo ""
  log_info "=============================================="
  log_info "=== Docker 镜像源切换完成 ==="
  log_info "=============================================="
  echo ""
  log_info "当前配置:"
  log_info "  镜像源类型: ${REGISTRY_TYPE}"
  log_info "  配置文件: ${DAEMON_JSON}"
  if [[ -n "${DAEMON_JSON_BACKUP}" ]] && [[ -f "${DAEMON_JSON_BACKUP}" ]]; then
    log_info "  备份文件: ${DAEMON_JSON_BACKUP}"
  fi
  echo ""

  if [[ "${REGISTRY_TYPE}" == "official" ]]; then
    log_info "使用官方 Docker Hub，无需额外配置"
  else
    log_info "使用镜像源加速，拉取官方镜像时将自动使用加速器"
    echo ""
    log_info "常用命令示例:"
    log_info "  docker pull nginx          # 使用镜像源加速拉取"
    log_info "  docker pull mysql:8.0       # 拉取 MySQL 镜像"
    log_info "  docker pull redis:latest    # 拉取 Redis 镜像"
  fi

  echo ""
  log_info "查看配置: bash $0 status"
  log_info "恢复官方: bash $0 official"
  echo ""

  # 可选：测试镜像拉取
  if [[ "${REGISTRY_TYPE}" != "official" ]]; then
    echo ""
    read -p "是否测试镜像拉取? (y/n, 默认: n): " -n 1 -r
    echo
    if [[ "${REPLY}" =~ ^[Yy]$ ]]; then
      test_pull "hello-world" || true
    fi
  fi
}

# 执行主函数
main "$@"
