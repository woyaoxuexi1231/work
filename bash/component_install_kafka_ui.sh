#!/usr/bin/env bash
# ========================================================================================
# Kafka UI Docker镜像安装脚本 (Kafka UI Docker Installer)
# ========================================================================================
#
# 功能说明:
#   本脚本基于 provectus/kafka-ui 官方Docker镜像，快速部署Kafka可视化Web管理界面，
#   方便通过浏览器管理和监控Kafka集群。
#
# 主要特性:
#   ✓ 官方镜像实现: 使用 provectus/kafka-ui 官方Docker镜像
#   ✓ Docker容器部署: 一键启动Kafka可视化界面
#   ✓ 多集群支持: 可同时管理多个Kafka集群
#   ✓ 主题管理: 可视化创建、查看、删除Topic
#   ✓ 消息浏览: 查看Topic消息内容和Offset信息
#   ✓ 消费者组: 查看消费者组状态和Lag
#   ✓ 实时监控: 查看Broker、分区、节点状态
#
# Kafka-UI 简介:
#   Kafka-UI 是一个功能丰富的 Kafka Web 管理界面：
#   - 多集群管理: 在一个界面中管理多个 Kafka 集群
#   - 主题管理: 可视化创建、编辑、删除主题
#   - 消息浏览: 按分区/偏移量浏览消息内容
#   - 消费者组: 查看消费者组列表、Lag、重新分配分区
#   - 实时监控: 显示 Broker 运行状态和集群健康度
#   - Schema Registry: 支持 Confluent Schema Registry 集成
#   - Kafka Connect: 可视化查看 Connect 连接器状态
#   - 权限控制: 支持登录认证和角色权限管理
#
# 配置参数:
#   UI_VERSION           - Kafka-UI 版本 (默认: latest)
#   UI_PORT              - Web 管理界面端口 (默认: 8080)
#   KAFKA_BOOTSTRAP_SERVERS - 目标Kafka集群地址 (默认: 192.168.3.100:9092)
#   UI_USERNAME          - 登录用户名 (默认: admin)
#   UI_PASSWORD          - 登录密码 (默认: admin123)
#   DEMO_MODE            - 演示模式 (默认: true)
#
# 使用场景:
#   1. 开发环境: 本地开发和测试时可视化监控Kafka
#   2. 测试环境: 测试集群的管理和监控
#   3. 学习环境: 直观理解Kafka主题、分区、消费者组概念
#   4. 运维管理: 日常运维中的集群状态查看和问题排查
#
# 注意事项:
#   ⚠️  单节点部署仅适用于开发/测试环境
#   ⚠️  生产环境建议配置登录认证
#   ⚠️  确保Kafka集群已提前部署并运行
#   ⚠️  KAFKA_HOST_IP 不要使用 localhost，要用实际服务器IP
#
# 故障排除:
#   - 查看容器状态: docker ps -a | grep kafka-ui
#   - 检查端口占用: netstat -tlnp | grep 8080
#   - 查看容器日志: docker logs kafka-ui
#   - 检查Kafka连接: telnet <KAFKA_IP> 9092
#   - 访问地址: http://<SERVER_IP>:<UI_PORT>
#
# 依赖:
#   - Docker 环境 (必需)
#   - 目标 Kafka 集群 (必需)
#   - 足够的内存 (建议至少 1GB)
#   - 网络端口 UI_PORT 未被占用
#
# 作者: 系统运维脚本
# 版本: v1.0
# 更新时间: 2026-01
# ========================================================================================

# Ensure we are running under bash
if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UI_VERSION="${UI_VERSION:-latest}"
UI_PORT="${UI_PORT:-9097}"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-192.168.3.100:9092}"
UI_USERNAME="${UI_USERNAME:-admin}"
UI_PASSWORD="${UI_PASSWORD:-admin123}"
DEMO_MODE="${DEMO_MODE:-true}"

# 日志文件
LOG_FILE="${LOG_FILE:-/tmp/install_kafka_ui.log}"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log() {
  local level="$1"; shift
  local msg="$*"
  local ts color
  ts="$(date '+%Y-%m-%d %H:%M:%S')"
  case "$level" in
    "INFO") color="$GREEN" ;;
    "WARN") color="$YELLOW" ;;
    "ERROR") color="$RED" ;;
    "STEP") color="$BLUE" ;;
    *) color="$NC" ;;
  esac
  printf '%b[%s] [%s] %s%b\n' "$color" "$ts" "$level" "$msg" "$NC" | tee -a "$LOG_FILE"
}

log_info() { log "INFO" "$@"; }
log_warn() { log "WARN" "$@"; }
log_error() { log "ERROR" "$@"; }
log_step() { log "STEP" "$@"; }

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

trap 'log_error "安装过程中出现错误，退出。"' ERR

log_info "=== Kafka-UI Docker镜像安装脚本开始 ==="
log_info "参数: UI_VERSION=${UI_VERSION}, UI_PORT=${UI_PORT}, KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS}, DEMO_MODE=${DEMO_MODE}"
log_info "日志文件: ${LOG_FILE}"

# 权限检查
if [[ $EUID -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    log_warn "当前非 root，使用 sudo 重新执行脚本..."
    exec sudo -E bash "$0" "$@"
  else
    log_error "需要 root 权限且未找到 sudo，请以 root 或 sudo 运行脚本。"
    exit 1
  fi
fi

# 检查是否已有运行中的 Kafka-UI 容器
check_existing_kafka_ui() {
  local container_id
  container_id=$(docker ps -a --filter "name=kafka-ui" --format "{{.ID}}" || true)
  if [[ -n "${container_id}" ]]; then
    log_warn "检测到已存在的 Kafka-UI 容器 (ID: ${container_id})"
    log_warn "建议先停止并删除现有容器，或使用不同的容器名称"
    read -p "是否继续安装? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
      log_info "用户取消安装"
      exit 0
    fi
  fi
}

# 检查 Docker 环境
check_docker() {
  log_info "检查 Docker 环境..."
  if ! command -v docker >/dev/null 2>&1; then
    log_error "未检测到 Docker，请先安装 Docker"
    exit 1
  fi

  if ! docker ps >/dev/null 2>&1; then
    log_warn "Docker 已安装但服务未运行，尝试启动..."
    systemctl start docker || {
      log_error "无法启动 Docker 服务"
      exit 1
    }
  fi
  log_info "✓ Docker 环境检查通过"
}

# 测试 Kafka 连接
test_kafka_connection() {
  log_step "Step 1: 验证 Kafka 集群连接"

  log_info "测试 Kafka 集群连接: ${KAFKA_BOOTSTRAP_SERVERS}"

  # 如果有 kafka 镜像，尝试使用它来测试
  if docker images apache/kafka --format "{{.Repository}}" | grep -q "^apache/kafka$" 2>/dev/null; then
    if docker run --rm apache/kafka:4.1.1 /opt/kafka/bin/kafka-topics.sh \
      --list --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" >/dev/null 2>&1; then
      log_info "✓ Kafka 集群连接成功: ${KAFKA_BOOTSTRAP_SERVERS}"
      return 0
    fi
  fi

  # 如果没有 kafka 镜像或连接失败，至少检查端口连通性
  local kafka_host="${KAFKA_BOOTSTRAP_SERVERS%%:*}"
  local kafka_port="${KAFKA_BOOTSTRAP_SERVERS##*:}"

  if command -v nc >/dev/null 2>&1; then
    if nc -z -w5 "${kafka_host}" "${kafka_port}" 2>/dev/null; then
      log_info "✓ Kafka 端口可达: ${kafka_host}:${kafka_port}"
      return 0
    fi
  fi

  log_warn "⚠️  无法确认 Kafka 集群是否可达: ${KAFKA_BOOTSTRAP_SERVERS}"
  log_warn "请确保 Kafka 已启动且网络可达，否则 Kafka-UI 启动后可能无法连接"
  read -p "是否继续安装? (y/N): " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    log_info "用户取消安装"
    exit 0
  fi
}

# 拉取镜像
pull_image() {
  log_step "Step 2: 拉取 Kafka-UI Docker 镜像"

  log_info "拉取镜像: provectuslabs/kafka-ui:${UI_VERSION}"
  docker pull "provectuslabs/kafka-ui:${UI_VERSION}"

  log_info "✓ 镜像拉取完成"
}

# 启动 Kafka-UI Docker 容器
start_kafka_ui_docker() {
  log_step "Step 3: 启动 Kafka-UI Docker 容器"

  log_info "启动 Kafka-UI..."
  log_info "连接 Kafka 集群: ${KAFKA_BOOTSTRAP_SERVERS}"
  log_info "Web 管理地址: http://<SERVER_IP>:${UI_PORT}"

  docker run -d --name kafka-ui \
    --restart=unless-stopped \
    -p "${UI_PORT}:8080" \
    -e TZ=Asia/Shanghai \
    -e KAFKA_CLUSTERS_0_NAME=local-cluster \
    -e KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS="${KAFKA_BOOTSTRAP_SERVERS}" \
    -e KAFKA_CLUSTERS_0_READONLY=false \
    "provectuslabs/kafka-ui:${UI_VERSION}"

  # 等待服务就绪
  log_info "等待 Kafka-UI 服务就绪..."
  local max_retries=30
  local retry_count=0
  local ui_ready=false

  while [[ $retry_count -lt $max_retries ]]; do
    if docker ps --filter "name=kafka-ui" --filter "status=running" --format "{{.Names}}" | grep -q "^kafka-ui$"; then
      # 检查端口是否可访问
      if curl -s -o /dev/null -w "%{http_code}" "http://localhost:${UI_PORT}/" 2>/dev/null | grep -q "200\|302"; then
        ui_ready=true
        log_info "✓ Kafka-UI 服务已就绪"
        break
      fi
    fi
    retry_count=$((retry_count + 1))
    if [[ $((retry_count % 10)) -eq 0 ]]; then
      log_info "等待 Kafka-UI 启动中... (${retry_count}/${max_retries})"
    fi
    sleep 2
  done

  if [[ "${ui_ready}" != "true" ]]; then
    log_warn "⚠️  Kafka-UI 启动等待超时，容器可能在后台仍在启动中"
    log_warn "请稍后检查容器日志: docker logs kafka-ui"
    log_warn "或访问 http://<SERVER_IP>:${UI_PORT} 确认是否可用"
  fi
}

# 显示集群信息
show_cluster_info() {
  log_step "Step 4: 查看集群信息"

  log_info "Kafka-UI 容器状态:"
  docker ps --filter "name=kafka-ui" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

  log_info ""
  log_info "集群配置摘要:"
  log_info "  集群名称: local-cluster"
  log_info "  Kafka 地址: ${KAFKA_BOOTSTRAP_SERVERS}"
  log_info "  UI 端口: ${UI_PORT}"
}

# 交互式功能演示
demo_ui_features() {
  log_step "Step 5: Kafka-UI 功能演示"

  log_info ""
  log_info "=== Kafka-UI 核心功能导览 ==="
  log_info ""
  log_info "1. 仪表盘 (Dashboard)"
  log_info "   - 查看集群整体健康状况"
  log_info "   - 显示主题数、分区数、Broker 数等统计"
  log_info ""
  log_info "2. 主题管理 (Topics)"
  log_info "   - 列出所有主题及各分区状态"
  log_info "   - 创建/编辑/删除主题"
  log_info "   - 查看消息内容 (按分区/偏移量/时间)"
  log_info "   - 查看主题配置参数"
  log_info ""
  log_info "3. 消费者组 (Consumers)"
  log_info "   - 查看消费者组成员和状态"
  log_info "   - 监控消费者 Lag (堆积量)"
  log_info "   - 支持重置偏移量"
  log_info ""
  log_info "4. Broker 监控"
  log_info "   - 查看 Broker 列表和运行状态"
  log_info "   - 查看控制器 (Controller) 信息"
  log_info "   - 查看节点配置"
  log_info ""
  log_info "5. Schema Registry (可选)"
  log_info "   - 查看和管理 Avro Schema"
  log_info "   - 支持 Schema 版本管理"
  log_info "   需要额外配置 Confluent Schema Registry 地址"
  log_info ""
  log_info "6. Kafka Connect (可选)"
  log_info "   - 查看 Connect 集群和工作状态"
  log_info "   - 创建/删除/重启连接器"
  log_info "   需要额外配置 Kafka Connect 地址"
  log_info ""

  echo "=== Spring Boot 连接配置参考 ==="
  echo "application.yml:"
  echo "  spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}"
  echo ""
  echo "=== Kafka-UI 访问地址 ==="
  echo "  本机:  http://localhost:${UI_PORT}"
  echo "  远程:  http://<SERVER_IP>:${UI_PORT}"
  echo ""

  # 交互式访问提示
  if command -v curl >/dev/null 2>&1; then
    if curl -s -o /dev/null -w "%{http_code}" "http://localhost:${UI_PORT}/" 2>/dev/null | grep -q "200\|302"; then
      log_info "✅ Kafka-UI 已运行，可通过浏览器访问 http://localhost:${UI_PORT}"
    fi
  fi
}

# 多集群配置
add_multi_cluster() {
  log_step "可选: 添加多集群配置"

  read -p "是否需要添加另一个 Kafka 集群? (y/N): " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    return 0
  fi

  read -p "输入新集群名称: " cluster_name
  read -p "输入新集群 Kafka 地址 (host:port): " cluster_bootstrap

  if [[ -z "${cluster_name}" || -z "${cluster_bootstrap}" ]]; then
    log_warn "集群名称和地址不能为空，跳过"
    return 0
  fi

  log_info "停止现有容器以添加新集群..."
  docker stop kafka-ui
  docker rm kafka-ui

  log_info "使用多集群配置重新启动..."

  docker run -d --name kafka-ui \
    --restart=unless-stopped \
    -p "${UI_PORT}:8080" \
    -e TZ=Asia/Shanghai \
    -e KAFKA_CLUSTERS_0_NAME=local-cluster \
    -e KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS="${KAFKA_BOOTSTRAP_SERVERS}" \
    -e KAFKA_CLUSTERS_1_NAME="${cluster_name}" \
    -e KAFKA_CLUSTERS_1_BOOTSTRAPSERVERS="${cluster_bootstrap}" \
    -e KAFKA_CLUSTERS_0_READONLY=false \
    -e KAFKA_CLUSTERS_1_READONLY=false \
    "provectuslabs/kafka-ui:${UI_VERSION}"

  log_info "✅ 多集群配置完成，已添加: ${cluster_name} (${cluster_bootstrap})"
}

# 启用认证配置
enable_auth() {
  log_step "可选: 启用登录认证"

  read -p "是否需要启用登录认证? (y/N): " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    return 0
  fi

  local auth_username auth_password
  read -p "输入登录用户名 (默认: admin): " auth_username
  auth_username="${auth_username:-admin}"
  read -s -p "输入登录密码 (默认: admin123): " auth_password
  auth_password="${auth_password:-admin123}"
  echo

  log_info "停止现有容器以启用认证..."
  docker stop kafka-ui
  docker rm kafka-ui

  log_info "使用认证配置重新启动..."

  docker run -d --name kafka-ui \
    --restart=unless-stopped \
    -p "${UI_PORT}:8080" \
    -e TZ=Asia/Shanghai \
    -e KAFKA_CLUSTERS_0_NAME=local-cluster \
    -e KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS="${KAFKA_BOOTSTRAP_SERVERS}" \
    -e KAFKA_CLUSTERS_0_READONLY=false \
    -e SPRING_SECURITY_USER_NAME="${auth_username}" \
    -e SPRING_SECURITY_USER_PASSWORD="${auth_password}" \
    "provectuslabs/kafka-ui:${UI_VERSION}"

  log_info "✅ 认证已启用，登录用户名: ${auth_username}"
  log_info "⚠️  请务必记住你的登录密码!"
}

# 清理函数
cleanup() {
  log_step "清理 Kafka-UI 环境"

  log_info "停止 Kafka-UI Docker 容器..."
  docker stop kafka-ui || true
  docker rm kafka-ui || true

  log_info "✓ 清理完成"
}

# 显示使用说明
show_usage() {
  log_info ""
  log_info "=== Kafka-UI Docker 镜像使用说明 ==="
  log_info ""
  log_info "1. 基本命令："
  log_info "   启动容器: $0"
  log_info "   停止容器: docker stop kafka-ui"
  log_info "   启动容器: docker start kafka-ui"
  log_info "   重启容器: docker restart kafka-ui"
  log_info "   删除容器: docker rm kafka-ui"
  log_info "   查看日志: docker logs kafka-ui"
  log_info "   实时日志: docker logs -f kafka-ui"
  log_info ""
  log_info "2. 环境变量参考："
  log_info "   KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS - 集群地址"
  log_info "   KAFKA_CLUSTERS_0_NAME               - 集群名称"
  log_info "   KAFKA_CLUSTERS_0_READONLY            - 只读模式 (false)"
  log_info "   KAFKA_CLUSTERS_0_PROPERTIES_*        - 额外客户端属性"
  log_info "   SPRING_SECURITY_USER_NAME            - 登录用户名"
  log_info "   SPRING_SECURITY_USER_PASSWORD        - 登录密码"
  log_info "   LOGGING_LEVEL_COM_PROVECTUS         - 日志级别 (DEBUG)"
  log_info ""
  log_info "3. 管理功能："
  log_info "   主题管理: 创建、编辑、删除主题"
  log_info "   消息浏览: 按时间/偏移量查看消息"
  log_info "   消费组: 查看 Lag 和消费状态"
  log_info "   分区分配: 查看分区 Leader 和 ISR"
  log_info ""
  log_info "4. 访问地址："
  log_info "   本机访问: http://localhost:${UI_PORT}"
  log_info "   远程访问: http://<SERVER_IP>:${UI_PORT}"
  log_info ""
  log_info "5. 高级配置："
  log_info "   Schema Registry:"
  log_info "     -e KAFKA_CLUSTERS_0_SCHEMAREGISTRY=http://schema-registry:8081"
  log_info "   Kafka Connect:"
  log_info "     -e KAFKA_CLUSTERS_0_KAFKACONNECT_0_NAME=connect"
  log_info "     -e KAFKA_CLUSTERS_0_KAFKACONNECT_0_ADDRESS=http://connect:8083"
  log_info "   SSL 连接:"
  log_info "     -e KAFKA_CLUSTERS_0_PROPERTIES_SECURITY_PROTOCOL=SSL"
  log_info "     -e KAFKA_CLUSTERS_0_PROPERTIES_SSL_KEYSTORE_LOCATION=/keystore.jks"
  log_info ""
  log_info "6. 学习资源："
  log_info "   GitHub: https://github.com/provectus/kafka-ui"
  log_info "   文档: https://docs.kafka-ui.provectus.io/"
  log_info "   Docker Hub: https://hub.docker.com/r/provectuslabs/kafka-ui"
  log_info ""
  log_info "7. 故障排除："
  log_info "   - 查看容器日志: docker logs kafka-ui"
  log_info "   - 检查网络: telnet <KAFKA_IP> 9092"
  log_info "   - 重启服务: docker restart kafka-ui"
  log_info "   - 更新版本: 删除容器后用新标签重新运行"
}

# 主函数
main() {
  # 欢迎信息
  log_info "欢迎使用 Kafka-UI Docker 部署工具"
  log_info "Kafka-UI 将连接到: ${KAFKA_BOOTSTRAP_SERVERS}"
  log_info "Web 界面端口: ${UI_PORT}"
  echo ""

  # 检查现有容器
  check_existing_kafka_ui

  # 环境检查
  check_docker

  # 测试 Kafka 连接
  test_kafka_connection

  # 拉取镜像
  pull_image

  # 启动容器
  start_kafka_ui_docker

  # 显示集群信息
  show_cluster_info

  # 功能演示
  if [[ "${DEMO_MODE}" == "true" ]]; then
    demo_ui_features

    # 询问是否添加多集群
    echo
    add_multi_cluster

    # 询问是否启用认证
    echo
    enable_auth
  fi

  # 显示使用说明
  show_usage

  log_info ""
  log_info "=== Kafka-UI Docker 镜像安装完成 ==="
  log_info "访问地址: http://<SERVER_IP>:${UI_PORT}"
  log_info ""
  log_info "如需清理环境，请运行: $0 cleanup"
}

# 处理命令行参数
case "${1:-}" in
  "cleanup")
    cleanup
    ;;
  "help"|"-h"|"--help")
    echo "用法: $0 [cleanup|help]"
    echo ""
    echo "参数说明:"
    echo "  (无参数)    安装并配置 Kafka-UI"
    echo "  cleanup     清理 Kafka-UI 环境"
    echo "  help        显示此帮助信息"
    echo ""
    echo "环境变量:"
    echo "  UI_VERSION              Kafka-UI 版本 (默认: latest)"
    echo "  UI_PORT                 Web 端口 (默认: 8080)"
    echo "  KAFKA_BOOTSTRAP_SERVERS Kafka 集群地址 (默认: 192.168.3.100:9092)"
    echo "  DEMO_MODE               演示模式 (默认: true)"
    echo ""
    echo "快速使用示例:"
    echo "  # 连接到本地 Kafka (默认端口8080)"
    echo "  export KAFKA_BOOTSTRAP_SERVERS=localhost:9092"
    echo "  sudo \$0"
    echo ""
    echo "  # 自定义端口和集群"
    echo "  export UI_PORT=9000"
    echo "  export KAFKA_BOOTSTRAP_SERVERS=10.0.0.1:9092,10.0.0.2:9092"
    echo "  sudo \$0"
    echo ""
    echo "  # 清理环境"
    echo "  sudo \$0 cleanup"
    ;;
  *)
    main
    ;;
esac
