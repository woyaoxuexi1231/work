#!/usr/bin/env bash
# ========================================================================================
# Nacos Docker 安装脚本 (Nacos Docker Installer)
# ========================================================================================
#
# 功能说明:
#   本脚本用于通过 Docker 快速部署 Nacos 动态服务发现、配置和服务管理平台。
#   Nacos 是阿里巴巴开源的项目，致力于帮助发现、配置和管理微服务。
#
# 主要特性:
#   1. 服务发现与服务健康检查: 自动注册实例并检查健康状态
#   2. 动态配置服务: 集中管理配置，支持配置动态刷新
#   3. 动态 DNS 服务: 支持权重路由，轻松实现中间层负载均衡
#   4. 服务及其元数据管理: 支持元数据管理和流量控制
#
# Nacos 简介:
#   Nacos (Dynamic Naming and Configuration Service) 是一个易于使用的平台，
#   旨在用于动态服务发现、配置和服务管理。它可以帮助您：
#   - 发现和管理微服务
#   - 集中管理配置
#   - 管理服务和元数据
#   - 实现动态 DNS 服务
#   - 支持服务流量控制
#
# 架构特点:
#   - 控制台端口 (8848): Web 管理界面和 HTTP API 端口
#   - gRPC 端口 (9848/9849): 客户端 gRPC 通信端口
#   - 数据存储: 支持内嵌数据库（开发）和 MySQL（生产）
#   - 集群模式: 支持多节点集群部署
#   - 命名空间: 支持环境隔离（dev/test/prod）
#
# 配置参数:
#   NACOS_PORT           - 控制台端口 (默认: 8848)
#   NACOS_GRPC_PORT      - gRPC 端口偏移 (默认: 1000，实际端口 9848)
#   NACOS_MODE           - 运行模式: standalone/cluster (默认: standalone)
#   NACOS_DATABASE       - 数据库类型: embedded/mysql (默认: embedded)
#   NACOS_DATA_ROOT      - 数据目录 (默认: /root/nacos)
#
# 安全注意事项:
#   ⚠️  默认用户名和密码均为 nacos
#   ⚠️  生产环境必须修改默认凭据
#   ⚠️  生产环境建议使用 MySQL 存储
#   ⚠️  建议启用鉴权功能
#
# 使用场景:
#   1. 开发环境: 单机模式 + 内嵌数据库
#   2. 测试环境: 单机模式 + MySQL
#   3. 生产环境: 集群模式 + MySQL 高可用
#
# 端口说明:
#   8848 - Nacos 控制台和 HTTP API 端口
#   9848 - Nacos 客户端 gRPC 端口（8848 + 1000）
#   9849 - Nacos 服务端 gRPC 端口（8848 + 1001）
#
# 目录结构:
#   /root/nacos/              - 根目录
#     ├── logs/               - 日志目录
#     ├── data/               - 数据目录
#     └── install_nacos.log   - 安装日志
#
# 访问方式:
#   1. Web 控制台: http://localhost:8848/nacos
#      用户名: nacos
#      密码: nacos
#
#   2. HTTP API: http://localhost:8848/nacos
#      示例: curl -X GET 'http://localhost:8848/nacos/v1/ns/instance/list?serviceName=test-service'
#
#   3. 客户端配置:
#      spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848
#      spring.cloud.nacos.config.server-addr=127.0.0.1:8848
#
# 常用操作:
#   1. 服务注册:
#      curl -X POST 'http://localhost:8848/nacos/v1/ns/instance?serviceName=test-service&ip=127.0.0.1&port=8080'
#
#   2. 服务发现:
#      curl -X GET 'http://localhost:8848/nacos/v1/ns/instance/list?serviceName=test-service'
#
#   3. 发布配置:
#      curl -X POST 'http://localhost:8848/nacos/v1/cs/configs?dataId=test.yaml&group=DEFAULT_GROUP&content=hello'
#
#   4. 获取配置:
#      curl -X GET 'http://localhost:8848/nacos/v1/cs/configs?dataId=test.yaml&group=DEFAULT_GROUP'
#
# 注意事项:
#   ⚠️  standalone 模式仅适用于开发和测试环境
#   ⚠️  生产环境建议使用集群模式 + MySQL
#   ⚠️  注意数据目录的磁盘空间
#   ⚠️  gRPC 端口必须正确配置，否则客户端无法连接
#
# 故障排除:
#   - 查看日志: docker logs nacos
#   - 检查端口: netstat -tlnp | grep 8848
#   - 测试连接: curl http://localhost:8848/nacos/v1/ns/operator/metrics
#   - Web 界面: 打开 http://localhost:8848/nacos 检查控制台
#
# 性能优化:
#   - 生产环境使用 MySQL 替代内嵌数据库
#   - 配置 JVM 内存参数
#   - 使用 SSD 存储提升性能
#   - 配置合理的日志级别
#
# 系统要求:
#   - Docker 环境正常运行
#   - 至少 2GB 可用内存
#   - 网络端口 8848、9848、9849 未被占用
#
# 作者: 系统运维脚本
# 版本: v1.0
# 更新时间: 2026-04-07
# ========================================================================================

# Ensure we are running under bash (Ubuntu /bin/sh 不支持 pipefail)
if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_ROOT="${NACOS_DATA_ROOT:-/root/nacos}"
CONTAINER_NAME="${NACOS_CONTAINER_NAME:-nacos}"
NACOS_VERSION="${NACOS_VERSION:-v2.3.2}"
NACOS_MODE="${NACOS_MODE:-standalone}"
NACOS_DATABASE="${NACOS_DATABASE:-embedded}"
NACOS_PORT="${NACOS_PORT:-8848}"
# gRPC 端口基于 HTTP 端口自动计算
NACOS_GRPC_PORT=$((NACOS_PORT + 1000))
NACOS_GRPC_SERVER_PORT=$((NACOS_PORT + 1001))
LOG_FILE="${LOG_FILE:-${DATA_ROOT}/install_nacos.log}"

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

log_info "=== Nacos Docker 安装开始 ==="
log_info "参数: DATA_ROOT=${DATA_ROOT}, CONTAINER_NAME=${CONTAINER_NAME}, NACOS_VERSION=${NACOS_VERSION}"
log_info "运行模式: ${NACOS_MODE}, 数据库: ${NACOS_DATABASE}"
log_info "HTTP 端口: ${NACOS_PORT}, gRPC 端口: ${NACOS_GRPC_PORT}, gRPC Server 端口: ${NACOS_GRPC_SERVER_PORT}"
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

# 检查 Docker 是否已安装
if ! command -v docker >/dev/null 2>&1; then
  log_error "未检测到 Docker，请先运行安装脚本安装 Docker："
  log_error "  bash ${SCRIPT_DIR}/install_docker.sh"
  log_error "或者手动安装 Docker 后再运行此脚本。"
  exit 1
fi

# 验证 Docker 服务是否运行
if ! docker ps >/dev/null 2>&1; then
  log_warn "Docker 已安装但服务未运行，尝试启动 Docker 服务..."
  systemctl start docker || {
    log_error "无法启动 Docker 服务，请检查：systemctl status docker"
    exit 1
  }
fi

# 检查容器是否已存在
if docker ps -a --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
  if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
    log_warn "容器 ${CONTAINER_NAME} 已存在且正在运行，跳过安装。"
  else
    log_warn "容器 ${CONTAINER_NAME} 已存在（已停止），如需重新创建请先删除该容器。"
  fi
  log_info "=== Nacos Docker 安装结束（已存在，无需处理） ==="
  exit 0
fi

# 1. 创建目录
log_info "创建目录结构: ${DATA_ROOT}/{logs,data}"
ensure_dir "${DATA_ROOT}"
ensure_dir "${DATA_ROOT}/logs"
ensure_dir "${DATA_ROOT}/data"

# 2. 检查并拉取 Nacos 镜像
log_info "检查并拉取 Nacos 镜像: nacos/nacos-server:${NACOS_VERSION}"
if ! docker image inspect "nacos/nacos-server:${NACOS_VERSION}" >/dev/null 2>&1; then
  log_info "本地不存在镜像 nacos/nacos-server:${NACOS_VERSION}，开始拉取..."
  if docker pull "nacos/nacos-server:${NACOS_VERSION}"; then
    log_info "镜像 nacos/nacos-server:${NACOS_VERSION} 拉取成功"
  else
    log_error "镜像拉取失败"
    exit 1
  fi
else
  log_info "镜像 nacos/nacos-server:${NACOS_VERSION} 已存在，跳过拉取"
fi

# 3. 构建 Docker 运行命令
log_info "启动 Nacos 容器: ${CONTAINER_NAME}"

# 基础参数
DOCKER_RUN_CMD=(
  docker run -d
  --name "${CONTAINER_NAME}"
  --restart=always
  -p "${NACOS_PORT}:8848"
  -p "${NACOS_GRPC_PORT}:9848"
  -p "${NACOS_GRPC_SERVER_PORT}:9849"
  -e MODE="${NACOS_MODE}"
  -e PREFER_HOST_MODE=hostname
  -v "${DATA_ROOT}/logs:/home/nacos/logs"
  -v "${DATA_ROOT}/data:/home/nacos/data"
)

# 根据数据库类型添加环境变量
if [[ "${NACOS_DATABASE}" == "mysql" ]]; then
  log_info "配置 MySQL 数据库模式"
  MYSQL_HOST="${NACOS_MYSQL_HOST:-127.0.0.1}"
  MYSQL_PORT="${NACOS_MYSQL_PORT:-3306}"
  MYSQL_DB="${NACOS_MYSQL_DB:-nacos}"
  MYSQL_USER="${NACOS_MYSQL_USER:-nacos}"
  MYSQL_PASSWORD="${NACOS_MYSQL_PASSWORD:-nacos}"
  
  DOCKER_RUN_CMD+=(
    -e SPRING_DATASOURCE_PLATFORM=mysql
    -e MYSQL_SERVICE_HOST="${MYSQL_HOST}"
    -e MYSQL_SERVICE_PORT="${MYSQL_PORT}"
    -e MYSQL_SERVICE_DB_NAME="${MYSQL_DB}"
    -e MYSQL_SERVICE_USER="${MYSQL_USER}"
    -e MYSQL_SERVICE_PASSWORD="${MYSQL_PASSWORD}"
  )
  
  log_info "MySQL 配置: ${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}"
else
  log_info "使用内嵌数据库模式（适合开发/测试）"
  DOCKER_RUN_CMD+=(
    -e NACOS_AUTH_ENABLE=false
  )
fi

# 添加镜像名称
DOCKER_RUN_CMD+=("nacos/nacos-server:${NACOS_VERSION}")

# 4. 启动容器
log_info "执行 Docker 运行命令..."
"${DOCKER_RUN_CMD[@]}"

log_info "Nacos 容器已创建"
log_info "等待 Nacos 启动..."
sleep 20

# 5. 验证 Nacos 启动
log_info "验证 Nacos 启动状态..."
if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
  log_info "Nacos 容器运行正常"

  # 检查 Nacos 日志
  log_info "检查 Nacos 启动日志..."
  if docker logs "${CONTAINER_NAME}" 2>&1 | grep -q "Nacos started successfully"; then
    log_info "✓ Nacos 启动成功"
  else
    log_info "Nacos 启动日志："
    docker logs "${CONTAINER_NAME}" 2>&1 | head -30 | while IFS= read -r line; do
      log_info "  $line"
    done || log_warn "无法读取 Nacos 日志"
  fi

  # 等待 Nacos 服务完全就绪
  log_info "等待 Nacos 服务完全就绪..."
  sleep 10

  # 测试 Nacos 连接
  log_info "测试 Nacos 连接..."
  if command -v curl >/dev/null 2>&1; then
    if curl -s --connect-timeout 10 "http://192.168.3.100:${NACOS_PORT}/nacos/v1/ns/operator/metrics" >/dev/null 2>&1; then
      log_info "✓ Nacos HTTP API 健康检查通过"
    else
      log_warn "Nacos HTTP API 健康检查失败，但容器正在运行，请稍后手动验证"
    fi
  else
    log_warn "未找到 curl，跳过连接测试"
  fi
else
  log_error "Nacos 容器启动失败"
  exit 1
fi

log_info "=== Nacos Docker 安装完成 ==="
log_info ""
log_info "服务信息："
log_info "  Nacos 容器: ${CONTAINER_NAME}"
log_info "  运行模式: ${NACOS_MODE}"
log_info "  数据库: ${NACOS_DATABASE}"
log_info "  HTTP 端口: ${NACOS_PORT}"
log_info "  gRPC 端口: ${NACOS_GRPC_PORT}"
log_info "  gRPC Server 端口: ${NACOS_GRPC_SERVER_PORT}"
log_info ""
log_info "访问地址："
log_info "  Web 控制台: http://192.168.3.100:${NACOS_PORT}/nacos"
log_info "  HTTP API: http://192.168.3.100:${NACOS_PORT}/nacos"
log_info "  默认用户名: nacos"
log_info "  默认密码: nacos"
log_info ""
log_info "Spring Cloud 配置示例："
log_info "  # application.yml"
log_info "  spring:"
log_info "    cloud:"
log_info "      nacos:"
log_info "        discovery:"
log_info "          server-addr: 192.168.3.100:${NACOS_PORT}"
log_info "        config:"
log_info "          server-addr: 192.168.3.100:${NACOS_PORT}"
log_info "          file-extension: yaml"
log_info ""
log_info "使用说明："
log_info "1. 查看服务状态："
log_info "   docker ps | grep ${CONTAINER_NAME}"
log_info ""
log_info "2. 查看 Nacos 日志："
log_info "   docker logs ${CONTAINER_NAME}"
log_info "   docker logs -f ${CONTAINER_NAME}  # 实时查看"
log_info ""
log_info "3. 访问管理控制台："
log_info "   打开浏览器访问: http://192.168.3.100:${NACOS_PORT}/nacos"
log_info "   使用用户名: nacos 密码: nacos 登录"
log_info ""
log_info "4. 测试服务注册："
log_info "   curl -X POST 'http://192.168.3.100:${NACOS_PORT}/nacos/v1/ns/instance?serviceName=test-service&ip=192.168.3.100&port=8080'"
log_info ""
log_info "5. 测试服务发现："
log_info "   curl -X GET 'http://192.168.3.100:${NACOS_PORT}/nacos/v1/ns/instance/list?serviceName=test-service'"
log_info ""
log_info "6. 测试配置管理："
log_info "   # 发布配置"
log_info "   curl -X POST 'http://192.168.3.100:${NACOS_PORT}/nacos/v1/cs/configs?dataId=test.yaml&group=DEFAULT_GROUP&content=hello'"
log_info "   # 获取配置"
log_info "   curl -X GET 'http://192.168.3.100:${NACOS_PORT}/nacos/v1/cs/configs?dataId=test.yaml&group=DEFAULT_GROUP'"
log_info ""
log_info "7. 数据目录："
log_info "   日志: ${DATA_ROOT}/logs"
log_info "   数据: ${DATA_ROOT}/data"
log_info ""
log_info "8. 停止服务："
log_info "   docker stop ${CONTAINER_NAME}"
log_info ""
log_info "9. 启动服务："
log_info "   docker start ${CONTAINER_NAME}"
log_info ""
log_info "10. 清理（删除容器和数据）："
log_info "   docker rm -f ${CONTAINER_NAME}"
log_info "   rm -rf ${DATA_ROOT}"
log_info ""
log_info "注意：当前使用 standalone 模式 + ${NACOS_DATABASE} 数据库，适合开发和测试环境。"
log_info "生产环境建议使用集群模式 + MySQL 数据库以确保高可用性和数据持久化。"
log_info ""
log_info "安全建议："
log_info "1. 修改默认用户名和密码"
log_info "2. 启用鉴权功能（NACOS_AUTH_ENABLE=true）"
log_info "3. 配置防火墙只允许必要端口访问"
log_info "4. 定期更新 Nacos 到最新版本"
log_info "5. 生产环境使用 MySQL 替代内嵌数据库"
log_info ""
log_info "Nacos 功能特点："
log_info "- 服务发现和服务健康检查"
log_info "- 动态配置服务"
log_info "- 动态 DNS 服务"
log_info "- 服务及其元数据管理"
log_info "- 支持 Spring Cloud、Dubbo、Kubernetes 等"
log_info "- 支持多语言客户端（Java/Go/Python/Node.js/C++）"
