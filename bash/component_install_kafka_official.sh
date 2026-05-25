#!/usr/bin/env bash
# ========================================================================================
# Kafka 官方Docker镜像安装脚本 (Kafka Official Docker Installer)
# ========================================================================================
#
# 功能说明:
#   本脚本基于 Apache Kafka 官方快速开始指南，使用 apache/kafka 官方JVM Docker镜像
#   实现完整的 Kafka 环境部署和功能演示。包含主题管理、生产者消费者演示等核心功能。
#
# 主要特性:
#   ✓ 官方指南实现: 严格按照 Apache Kafka 官方文档步骤
#   ✓ Docker容器部署: 使用官方JVM Docker镜像快速部署
#   ✓ 功能演示: 包含主题创建、生产消费等核心功能
#   ✓ 交互式演示: 提供生产者和消费者命令行工具演示
#   ✓ 一键部署: 简化部署流程，适合学习和测试
#
# Kafka 简介:
#   Kafka 是一个分布式流处理平台，具有以下特点：
#   - 高吞吐量: 单机支持百万级消息并发处理
#   - 持久化存储: 支持消息持久化和日志压缩
#   - 水平扩展: 支持分区和多副本机制
#   - 实时流处理: 支持 Kafka Streams API
#   - 生态完善: 提供丰富的客户端和集成工具
#   - 容错性强: 支持多副本和自动故障转移
#
# 配置参数:
#   KAFKA_VERSION        - Kafka 版本 (默认: 4.1.1)
#   KAFKA_PORT           - Kafka 端口 (默认: 9092)
#   KAFKA_HOST_IP        - Kafka 主机 IP 地址 (默认: 192.168.3.100)
#   DEMO_MODE            - 演示模式 (默认: true)
#
# Kafka 环境变量配置 (KRaft 模式):
#   KAFKA_NODE_ID=1                                    - 节点ID
#   KAFKA_PROCESS_ROLES=broker,controller             - 同时运行 broker 和 controller
#   KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
#                                                       - 定义监听器端口
#   KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://192.168.3.100:9092
#                                                       - 客户端连接地址
#   KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER         - Controller 监听器名称
#   KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093     - Controller 法定人数配置
#
# 端口说明:
#   9092  - Kafka 客户端连接端口 (PLAINTEXT)
#   9093  - Kafka Controller 内部通信端口 (CONTROLLER)
#
# 使用场景:
#   1. 学习环境: 快速了解 Kafka 的基本使用流程
#   2. 开发环境: 本地开发和测试 Kafka 应用程序
#   3. 测试环境: 验证 Kafka 功能和集成测试
#   4. 演示环境: 向他人展示 Kafka 的核心功能
#
# 部署方式:
#   Docker 容器部署: 使用 apache/kafka 官方 JVM Docker 镜像
#
# 注意事项:
#   ⚠️  单节点部署仅适用于开发/测试环境
#   ⚠️  生产环境至少需要 3 个 Broker 节点
#   ⚠️  演示模式会创建测试数据和主题
#   ⚠️  需要确保 Docker 服务正在运行
#
# 故障排除:
#   - 检查 Docker 服务: systemctl status docker
#   - 查看容器状态: docker ps -a | grep kafka
#   - 检查端口占用: netstat -tlnp | grep 9092
#   - 查看容器日志: docker logs kafka-official
#   - 测试连接: telnet localhost 9092
#
# 系统要求:
#   - Docker 环境 (必需)
#   - Docker Compose (可选，用于更复杂的部署)
#   - 足够的内存 (建议至少 4GB)
#   - 足够的磁盘空间用于消息存储
#   - 网络端口 9092 未被占用
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
KAFKA_VERSION="${KAFKA_VERSION:-4.1.1}"
KAFKA_PORT="${KAFKA_PORT:-9092}"
KAFKA_HOST_IP="${KAFKA_HOST_IP:-192.168.3.100}"
DEMO_MODE="${DEMO_MODE:-true}"

# 日志文件
LOG_FILE="${LOG_FILE:-/tmp/install_kafka_official.log}"

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

log_info "=== Kafka 官方Docker镜像安装脚本开始 ==="
log_info "参数: KAFKA_VERSION=${KAFKA_VERSION}, KAFKA_PORT=${KAFKA_PORT}, KAFKA_HOST_IP=${KAFKA_HOST_IP}, DEMO_MODE=${DEMO_MODE}"
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

# 检查是否已有运行中的 Kafka 容器
check_existing_kafka() {
  local container_id
  container_id=$(docker ps -a --filter "name=kafka-official" --format "{{.ID}}" || true)
  if [[ -n "${container_id}" ]]; then
    log_warn "检测到已存在的 Kafka 容器 (ID: ${container_id})"
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



# 启动 Kafka Docker 容器
start_kafka_docker() {
  log_step "Step 1: 启动 Kafka Docker 容器"

  log_info "使用官方 JVM 镜像启动 Kafka..."
  docker run -d --name kafka-official \
    --restart=unless-stopped \
    -p ${KAFKA_PORT}:9092 \
    -p 9093:9093 \
    -e TZ=Asia/Shanghai \
    -e KAFKA_NODE_ID=1 \
    -e KAFKA_PROCESS_ROLES=broker,controller \
    -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
    -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://${KAFKA_HOST_IP}:9092 \
    -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
    -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
    -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
    -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
    -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
    apache/kafka:${KAFKA_VERSION}

  # 等待 Kafka 服务就绪并验证
  log_info "等待 Kafka 服务就绪..."
  local max_retries=60
  local retry_count=0
  local kafka_ready=false

  while [[ $retry_count -lt $max_retries ]]; do
    # 检查容器是否存在且运行
    if docker ps --filter "name=kafka-official" --filter "status=running" --format "{{.Names}}" | grep -q "^kafka-official$"; then
      # 尝试连接 Kafka 并列出主题（使用完整路径）
      if docker exec kafka-official /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092 >/dev/null 2>&1; then
        kafka_ready=true
        log_info "✓ Kafka 服务已就绪"
        log_info "Kafka 连接地址: ${KAFKA_HOST_IP}:${KAFKA_PORT}"
        break
      fi
    else
      log_warn "容器 kafka-official 未运行或不存在，检查容器状态..."
      docker ps -a --filter "name=kafka-official"
      sleep 5
      return 1
    fi
    retry_count=$((retry_count + 1))
    if [[ $((retry_count % 10)) -eq 0 ]]; then
      log_info "等待 Kafka 服务启动中... (${retry_count}/${max_retries})"
    fi
    sleep 3
  done

  if [[ "${kafka_ready}" != "true" ]]; then
    log_error "Kafka 服务启动超时，请检查容器日志"
    log_error "容器日志: docker logs kafka-official"
    return 1
  fi
}

# 创建主题
create_topic() {
  log_step "Step 2: 创建主题"

  local topic_name="quickstart-events"

  log_info "创建主题: ${topic_name}"
  docker exec kafka-official /opt/kafka/bin/kafka-topics.sh --create \
    --topic "${topic_name}" \
    --bootstrap-server localhost:9092

  log_info "查看主题详情:"
  docker exec kafka-official /opt/kafka/bin/kafka-topics.sh --describe \
    --topic "${topic_name}" \
    --bootstrap-server localhost:9092

  log_info "✓ 主题创建完成"
}

# 演示生产者
demo_producer() {
  log_step "Step 4: 演示生产者"

  if [[ "${DEMO_MODE}" != "true" ]]; then
    log_info "跳过演示模式"
    return 0
  fi

  log_info "启动控制台生产者..."
  log_info "请输入消息（每行一个消息），Ctrl+C 退出:"

  docker exec -it kafka-official /opt/kafka/bin/kafka-console-producer.sh \
    --topic quickstart-events \
    --bootstrap-server localhost:9092
}

# 演示消费者
demo_consumer() {
  log_step "Step 5: 演示消费者"

  if [[ "${DEMO_MODE}" != "true" ]]; then
    log_info "跳过演示模式"
    return 0
  fi

  log_info "启动控制台消费者..."
  log_info "从头开始消费消息，Ctrl+C 退出:"

  docker exec -it kafka-official /opt/kafka/bin/kafka-console-consumer.sh \
    --topic quickstart-events \
    --from-beginning \
    --bootstrap-server localhost:9092
}

# Kafka Connect 演示
demo_connect() {
  log_step "Step 6: Kafka Connect 演示"

  if [[ "${DEMO_MODE}" != "true" ]]; then
    log_info "跳过演示模式"
    return 0
  fi

  log_info "Kafka Connect 演示说明："
  log_info "Kafka Connect 允许在外部系统和 Kafka 之间可靠地传输数据"
  log_info "官方镜像中包含了文件连接器，可用于文件和 Kafka 之间的数据传输"
  log_info "要运行完整的 Kafka Connect 演示，需要挂载配置文件到容器"
  log_info "这里我们只展示基本概念，实际部署时请参考官方文档"
  log_info "https://kafka.apache.org/documentation/#connect"

  log_info "✓ Kafka Connect 概念演示完成"
}

# Kafka Streams 示例
show_streams_example() {
  log_step "Step 7: Kafka Streams 示例"

  log_info "Kafka Streams WordCount 示例代码:"

  cat << 'EOF'
KStream<String, String> textLines = builder.stream("quickstart-events");

KTable<String, Long> wordCounts = textLines
        .flatMapValues(line -> Arrays.asList(line.toLowerCase().split(" ")))
        .groupBy((keyIgnored, word) -> word)
        .count();

wordCounts.toStream().to("output-topic", Produced.with(Serdes.String(), Serdes.Long()));
EOF

  log_info "此示例演示了如何使用 Kafka Streams 进行实时单词计数"
  log_info "输入: quickstart-events 主题的消息"
  log_info "输出: output-topic 主题的单词计数结果"
}

# 清理函数
cleanup() {
  log_step "清理 Kafka 环境"

  log_info "停止 Kafka Docker 容器..."
  docker stop kafka-official || true
  docker rm kafka-official || true

  log_info "✓ 清理完成"
}

# 显示使用说明
show_usage() {
  log_info ""
  log_info "=== Kafka Docker 镜像使用说明 ==="
  log_info ""
  log_info "1. 基本命令："
  log_info "   列出主题: docker exec kafka-official /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092"
  log_info "   创建主题: docker exec kafka-official /opt/kafka/bin/kafka-topics.sh --create --topic my-topic --bootstrap-server localhost:9092"
  log_info "   生产消息: docker exec -it kafka-official /opt/kafka/bin/kafka-console-producer.sh --topic my-topic --bootstrap-server localhost:9092"
  log_info "   消费消息: docker exec -it kafka-official /opt/kafka/bin/kafka-console-consumer.sh --topic my-topic --from-beginning --bootstrap-server localhost:9092"
  log_info ""
  log_info "2. 主题管理："
  log_info "   查看主题详情: docker exec kafka-official /opt/kafka/bin/kafka-topics.sh --describe --topic my-topic --bootstrap-server localhost:9092"
  log_info "   删除主题: docker exec kafka-official /opt/kafka/bin/kafka-topics.sh --delete --topic my-topic --bootstrap-server localhost:9092"
  log_info ""
  log_info "3. 消费者组管理："
  log_info "   列出消费者组: docker exec kafka-official /opt/kafka/bin/kafka-consumer-groups.sh --list --bootstrap-server localhost:9092"
  log_info "   查看消费者组详情: docker exec kafka-official /opt/kafka/bin/kafka-consumer-groups.sh --describe --group my-group --bootstrap-server localhost:9092"
  log_info ""
  log_info "4. 配置信息："
  log_info "   查看 Broker 配置: docker exec kafka-official /opt/kafka/bin/kafka-configs.sh --describe --entity-type brokers --entity-name 1 --bootstrap-server localhost:9092"
  log_info ""
  log_info "5. 容器管理："
  log_info "   查看容器日志: docker logs kafka-official"
  log_info "   进入容器: docker exec -it kafka-official bash"
  log_info "   停止容器: docker stop kafka-official"
  log_info "   删除容器: docker rm kafka-official"
  log_info ""
  log_info "6. 学习资源："
  log_info "   官方文档: https://kafka.apache.org/documentation/"
  log_info "   快速开始: https://kafka.apache.org/quickstart"
  log_info "   Kafka Streams: https://kafka.apache.org/documentation/streams/"
  log_info "   Kafka Connect: https://kafka.apache.org/documentation/#connect"
  log_info ""
  log_info "7. 故障排除："
  log_info "   - 查看容器日志: docker logs kafka-official"
  log_info "   - 检查端口: netstat -tlnp | grep 9092"
  log_info "   - 测试连接: telnet ${KAFKA_HOST_IP} 9092"
  log_info "   - 容器状态: docker ps -a | grep kafka-official"
  log_info ""
  log_info "8. Spring Boot 连接配置："
  log_info "   spring.kafka.bootstrap-servers=${KAFKA_HOST_IP}:${KAFKA_PORT}"
  log_info "   # 不要使用 localhost，要使用实际的服务器IP地址"
}

# 主函数
main() {
  # 检查现有容器
  check_existing_kafka

  # 环境检查
  check_docker

  # 启动 Kafka Docker 容器
  start_kafka_docker

  # 创建主题
  create_topic

  if [[ "${DEMO_MODE}" == "true" ]]; then
    log_info ""
    log_info "=== 交互式演示开始 ==="
    log_info "接下来将演示生产者和消费者功能"
    log_info "请在不同的终端窗口中运行以下命令来体验完整功能"
    log_info ""

    # 显示演示说明
    echo
    echo "演示说明:"
    echo "1. 打开一个新的终端，运行消费者演示:"
    echo "   docker exec -it kafka-official /opt/kafka/bin/kafka-console-consumer.sh --topic quickstart-events --from-beginning --bootstrap-server localhost:9092"
    echo
    echo "2. 在当前终端，运行生产者演示:"
    echo "   docker exec -it kafka-official /opt/kafka/bin/kafka-console-producer.sh --topic quickstart-events --bootstrap-server localhost:9092"
    echo
    echo "3. 在生产者中输入消息，观察消费者是否收到"
    echo
    echo "注意：Spring Boot 应用请连接 ${KAFKA_HOST_IP}:${KAFKA_PORT}，不要使用 localhost"
    echo
    read -p "是否现在运行生产者演示? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
      demo_producer
    fi

    # Kafka Connect 演示
    read -p "是否运行 Kafka Connect 演示? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
      demo_connect
    fi
  fi

  # 显示 Streams 示例
  show_streams_example

  # 显示使用说明
  show_usage

  log_info ""
  log_info "=== Kafka Docker 镜像安装完成 ==="
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
    echo "  (无参数)    安装并演示 Kafka"
    echo "  cleanup     清理 Kafka 环境"
    echo "  help        显示此帮助信息"
    echo ""
    echo "环境变量:"
    echo "  KAFKA_VERSION     Kafka 版本 (默认: 4.1.1)"
    echo "  KAFKA_PORT        Kafka 端口 (默认: 9092)"
    echo "  DEMO_MODE         启用演示模式 (默认: true)"
    ;;
  *)
    main
    ;;
esac
