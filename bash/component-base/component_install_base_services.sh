#!/usr/bin/env bash
# ========================================================================================
# 基础服务批量安装脚本 (Base Services Batch Installer)
# ========================================================================================
#
# 功能说明:
#   本脚本用于批量安装多个基础服务组件，支持选择性安装，避免重复的手动安装过程。
#   适用于开发环境、测试环境或需要快速搭建基础服务栈的场景。
#
# 支持的服务 (默认全部安装):
#   ✓ MinIO      - 高性能对象存储服务，S3 API 兼容
#   ✓ Redis      - 高性能键值缓存数据库
#   ✓ MySQL      - 关系型数据库
#   ✓ MongoDB    - NoSQL 文档数据库
#   ✓ Elasticsearch - 全文搜索引擎和分析引擎
#   ✓ ZooKeeper  - 分布式协调服务
#
# 主要特性:
#   1. 灵活选择: 支持安装全部服务或指定服务列表
#   2. 智能检查: 自动检测 Docker 环境并安装
#   3. 进度跟踪: 显示详细的安装进度和状态
#   4. 错误处理: 完善的错误检测和恢复机制
#   5. 状态验证: 安装完成后自动验证服务运行状态
#   6. 日志记录: 完整的安装日志记录到文件
#
# 使用方法:
#   1. 安装所有服务: bash component_install_base_services.sh --all
#   2. 选择性安装: bash component_install_base_services.sh --services redis,mysql,mongo
#   3. 查看帮助: bash component_install_base_services.sh --help
#
# 环境变量:
#   INSTALL_SERVICES    - 设置默认安装的服务列表 (空格分隔，默认: minio redis mysql mongo elasticsearch zookeeper)
#   LOG_FILE           - 指定日志文件路径
#
# 注意事项:
#   - 需要 root 权限或 sudo 权限
#   - 会自动检查并安装 Docker 环境
#   - 生产环境请根据需要调整配置参数
#   - 建议在开发/测试环境使用
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
LOG_FILE="${LOG_FILE:-/var/log/base-services/install_base_services.log}"

# 可用的基础服务列表
AVAILABLE_SERVICES=(
    "minio"
    "redis"
    "mysql"
    "mongo"
    "elasticsearch"
    "zookeeper"
)

# 服务对应的安装脚本映射
declare -A SERVICE_SCRIPTS=(
    ["minio"]="component_install_minio_docker.sh"
    ["redis"]="component_install_redis_docker.sh"
    ["mysql"]="component_install_mysql_docker.sh"
    ["mongo"]="component_install_mongo_docker.sh"
    ["elasticsearch"]="component_install_elasticsearch_docker.sh"
    ["zookeeper"]="component_install_zookeeper_docker.sh"
)

# 默认要安装的服务
DEFAULT_SERVICES_LIST=(minio redis mysql mongo elasticsearch zookeeper)
# 可以通过环境变量覆盖
if [[ -n "${INSTALL_SERVICES:-}" ]]; then
  # 如果设置了环境变量，用空格分割
  read -ra DEFAULT_SERVICES_LIST <<< "$INSTALL_SERVICES"
fi

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

# 确保日志目录及其父目录存在，避免 tee 报错
ensure_dir "$(dirname "${LOG_FILE}")"

trap 'log_error "安装过程中出现错误，退出。"' ERR

log_info "=== 基础服务批量安装开始 ==="
log_info "脚本目录: ${SCRIPT_DIR}"
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

# 解析命令行参数
INSTALL_ALL=false
SERVICES_TO_INSTALL=()

while [[ $# -gt 0 ]]; do
  case $1 in
    --all)
      INSTALL_ALL=true
      shift
      ;;
    --services)
      if [[ -n "${2:-}" && ! "$2" =~ ^-- ]]; then
        IFS=',' read -ra SERVICES_TO_INSTALL <<< "$2"
        shift 2
      else
        log_error "错误: --services 参数需要提供服务列表，用逗号分隔"
        exit 1
      fi
      ;;
    --help)
      echo "用法: $0 [选项]"
      echo ""
      echo "选项:"
      echo "  --all                    安装所有可用的基础服务"
      echo "  --services LIST          安装指定的服务列表（用逗号分隔）"
      echo "  --help                   显示此帮助信息"
      echo ""
      echo "可用服务: ${AVAILABLE_SERVICES[*]}"
      echo ""
      echo "示例:"
      echo "  $0 --all                                    # 安装所有服务"
      echo "  $0 --services minio,redis,mysql,mongo       # 安装指定服务"
      echo "  $0                                          # 使用默认服务列表"
      exit 0
      ;;
    *)
      log_error "未知参数: $1"
      log_error "使用 --help 查看帮助信息"
      exit 1
      ;;
  esac
done

# 确定要安装的服务列表
if [[ "${INSTALL_ALL}" == "true" ]]; then
  SERVICES_TO_INSTALL=("${AVAILABLE_SERVICES[@]}")
elif [[ ${#SERVICES_TO_INSTALL[@]} -eq 0 ]]; then
  # 使用默认服务列表
  SERVICES_TO_INSTALL=("${DEFAULT_SERVICES_LIST[@]}")
fi

# 验证服务名称
for service in "${SERVICES_TO_INSTALL[@]}"; do
  if [[ ! " ${AVAILABLE_SERVICES[*]} " =~ " ${service} " ]]; then
    log_error "未知服务: ${service}"
    log_error "可用服务: ${AVAILABLE_SERVICES[*]}"
    exit 1
  fi
done

log_info "将要安装的服务: ${SERVICES_TO_INSTALL[*]}"
log_info "服务数量: ${#SERVICES_TO_INSTALL[@]}"

# 1. 检查 Docker 环境
log_info "检查 Docker 环境..."
if ! command -v docker >/dev/null 2>&1; then
  log_error "Docker 未安装，请先手动安装 Docker 环境"
  log_error "安装方法：bash component_install_docker.sh"
  exit 1
fi

# 验证 Docker 服务是否运行
if ! docker ps >/dev/null 2>&1; then
  log_warn "Docker 服务未运行，尝试启动..."
  systemctl start docker || {
    log_error "无法启动 Docker 服务，请手动启动 Docker 服务"
    exit 1
  }
fi
log_info "✓ Docker 环境正常"

# 2. 依次安装选定的服务
total_services=${#SERVICES_TO_INSTALL[@]}
current_service=0

for service in "${SERVICES_TO_INSTALL[@]}"; do
  current_service=$((current_service + 1))

  log_info "=== [${current_service}/${total_services}] 开始安装: ${service} ==="

  # 检查服务是否有对应的安装脚本
  if [[ ! -v SERVICE_SCRIPTS[$service] ]]; then
    log_warn "服务 ${service} 暂无安装脚本，跳过"
    continue
  fi

  script="${SERVICE_SCRIPTS[$service]}"
  if [[ -z "${script}" ]]; then
    log_warn "服务 ${service} 的安装脚本为空，跳过"
    continue
  fi

  script_path="${SCRIPT_DIR}/${script}"
  if [[ ! -f "${script_path}" ]]; then
    log_error "安装脚本不存在: ${script_path}"
    exit 1
  fi

  log_info "执行脚本: ${script_path}"

  # 执行安装脚本
  if bash "${script_path}"; then
    log_info "✓ ${service} 安装成功"
  else
    log_error "✗ ${service} 安装失败"
    exit 1
  fi

  # 在服务间添加短暂延迟，避免资源竞争
  if [[ ${current_service} -lt ${total_services} ]]; then
    log_info "等待 3 秒后继续下一个服务..."
    sleep 3
  fi
done

log_info "=== 基础服务批量安装完成 ==="
log_info ""
log_info "已安装的服务: ${SERVICES_TO_INSTALL[*]}"
log_info ""
log_info "服务状态检查:"

# 3. 检查安装的服务状态
for service in "${SERVICES_TO_INSTALL[@]}"; do
  if [[ -v SERVICE_SCRIPTS[$service] ]]; then
    script="${SERVICE_SCRIPTS[$service]}"
  else
    script=""
  fi

  if [[ -n "${script}" ]]; then
    case "${service}" in
      "minio")
        if docker ps --format '{{.Names}}' | grep -q "minio"; then
          log_info "  ✓ MinIO: 运行中"
        else
          log_warn "  ✗ MinIO: 未运行"
        fi
        ;;
      "redis")
        if docker ps --format '{{.Names}}' | grep -q "redis"; then
          log_info "  ✓ Redis: 运行中"
        else
          log_warn "  ✗ Redis: 未运行"
        fi
        ;;
      "mysql")
        if docker ps --format '{{.Names}}' | grep -q "mysql"; then
          log_info "  ✓ MySQL: 运行中"
        else
          log_warn "  ✗ MySQL: 未运行"
        fi
        ;;
      "mongo")
        if docker ps --format '{{.Names}}' | grep -q "mongo"; then
          log_info "  ✓ MongoDB: 运行中"
        else
          log_warn "  ✗ MongoDB: 未运行"
        fi
        ;;
      "elasticsearch")
        if docker ps --format '{{.Names}}' | grep -q "elasticsearch"; then
          log_info "  ✓ Elasticsearch: 运行中"
        else
          log_warn "  ✗ Elasticsearch: 未运行"
        fi
        ;;
      "zookeeper")
        if docker ps --format '{{.Names}}' | grep -q "zookeeper"; then
          log_info "  ✓ ZooKeeper: 运行中"
        else
          log_warn "  ✗ ZooKeeper: 未运行"
        fi
        ;;
      *)
        log_info "  ? ${service}: 请手动检查状态"
        ;;
    esac
  fi
done

log_info ""
log_info "使用说明:"
log_info "1. 查看所有容器状态: docker ps"
log_info "2. 查看服务日志: docker logs <container_name>"
log_info "3. 停止服务: docker stop <container_name>"
log_info "4. 启动服务: docker start <container_name>"
log_info ""
log_info "常用端口映射:"
log_info "  MinIO: 9000 (API), 9001 (控制台)"
log_info "  Redis: 6379"
log_info "  MySQL: 3306"
log_info "  MongoDB: 27017"
log_info "  Elasticsearch: 9200"
log_info "  ZooKeeper: 2181"
log_info ""
log_info "默认安装的服务顺序:"
log_info "  1. MinIO (对象存储)"
log_info "  2. Redis (缓存数据库)"
log_info "  3. MySQL (关系数据库)"
log_info "  4. MongoDB (文档数据库)"
log_info "  5. Elasticsearch (搜索引擎)"
log_info "  6. ZooKeeper (分布式协调)"
log_info ""
log_info "日志文件位置: ${LOG_FILE}"
