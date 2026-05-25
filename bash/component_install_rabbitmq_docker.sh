#!/usr/bin/env bash
# ========================================================================================
# RabbitMQ Docker 安装脚本 (RabbitMQ Docker Installer)
# ========================================================================================
#
# 功能说明:
#   本脚本用于通过 Docker 快速部署 RabbitMQ 高级消息队列中间件。
#   RabbitMQ 是最广泛使用的开源消息代理，支持多种消息协议和丰富的功能特性。
#
# 主要特性:
#   ✓ 多协议支持: 支持 AMQP、MQTT、STOMP 等多种协议
#   ✓ 管理界面: 内置 Web 管理界面，方便监控和配置
#   ✓ 集群支持: 支持高可用性和负载均衡
#   ✓ 插件系统: 丰富的插件生态，支持各种扩展功能
#   ✓ 消息确认: 支持消息确认机制，保证消息可靠性
#
# RabbitMQ 简介:
#   RabbitMQ 是一个企业级的消息队列系统，具有以下特点：
#   - 可靠性高: 支持消息持久化、发布确认、消费确认
#   - 灵活路由: 支持多种交换机类型和路由规则
#   - 分布式: 支持集群部署和镜像队列
#   - 可观测性: 提供详细的监控指标和日志
#   - 生态完善: 支持多种编程语言的客户端
#
# 核心概念:
#   - Producer: 消息生产者，发送消息到交换机
#   - Exchange: 交换机，根据路由规则分发消息
#   - Queue: 队列，存储等待消费的消息
#   - Consumer: 消息消费者，从队列获取和处理消息
#   - Binding: 绑定关系，连接交换机和队列
#
# 交换机类型:
#   - Direct: 直接交换机，精确路由匹配
#   - Topic: 主题交换机，支持通配符路由
#   - Headers: 头交换机，基于消息头路由
#   - Fanout: 扇形交换机，广播到所有绑定队列
#
# 配置参数:
#   RABBITMQ_DEFAULT_USER    - 默认用户名 (默认: admin)
#   RABBITMQ_DEFAULT_PASS    - 默认密码 (默认: admin)
#   RABBITMQ_PORT            - AMQP 端口 (默认: 5672)
#   RABBITMQ_MANAGEMENT_PORT - 管理界面端口 (默认: 15672)
#
# 端口说明:
#   5672  - AMQP 协议端口 (主要通信端口)
#   15672 - Web 管理界面端口
#   15674 - STOMP 协议端口 (如果启用)
#   1883  - MQTT 协议端口 (如果启用)
#   8883  - MQTT over TLS 端口 (如果启用)
#
# 目录结构:
#   /root/rabbitmq/           - 根目录
#     ├── data/              - 数据存储目录
#     └── install_rabbitmq.log - 安装日志
#
# 访问方式:
#   1. Web 管理界面: http://192.168.3.100:15672
#      用户名: admin (或自定义)
#      密码: admin (或自定义)
#
#   2. AMQP 连接: 192.168.3.100:5672
#
# 主要功能:
#   - 消息队列: 可靠的消息传递
#   - 发布订阅: 灵活的消息分发模式
#   - 工作队列: 负载均衡的任务分发
#   - RPC 调用: 同步请求-响应模式
#   - 流处理: 支持消息流处理
#
# 高级特性:
#   - 死信队列: 处理未成功消费的消息
#   - 延迟队列: 支持定时消息投递
#   - 优先级队列: 支持消息优先级排序
#   - 消息 TTL: 设置消息过期时间
#   - 队列最大长度: 限制队列消息数量
#
# 注意事项:
#   ⚠️  单节点部署仅适用于开发/测试环境
#   ⚠️  生产环境建议使用集群部署确保高可用
#   ⚠️  默认密码应在生产环境中修改
#   ⚠️  注意磁盘空间，消息数据可能增长较快
#
# 故障排除:
#   - 查看日志: docker logs rabbitmq
#   - 检查端口: netstat -tlnp | grep 5672
#   - 测试连接: telnet localhost 5672
#   - Web 界面: 访问 http://localhost:15672 检查管理界面
#   - 集群状态: rabbitmqctl cluster_status (在容器内执行)
#
# 性能优化:
#   - 调整 Erlang VM 参数
#   - 配置适当的内存和磁盘使用限制
#   - 启用消息持久化以提高可靠性
#   - 监控队列深度和消息积压情况
#
# 系统要求:
#   - Docker 环境正常运行
#   - 足够的内存资源 (建议至少 1GB)
#   - 网络端口 5672 和 15672 未被占用
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
DATA_ROOT="${RABBITMQ_DATA_ROOT:-/root/rabbitmq}"
CONTAINER_NAME="${RABBITMQ_CONTAINER_NAME:-rabbitmq}"
RABBITMQ_VERSION="${RABBITMQ_VERSION:-3.13}"
RABBITMQ_USER="${RABBITMQ_USER:-admin}"
RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-admin}"
RABBITMQ_PORT="${RABBITMQ_PORT:-5672}"
RABBITMQ_MANAGEMENT_PORT="${RABBITMQ_MANAGEMENT_PORT:-15672}"
LOG_FILE="${LOG_FILE:-${DATA_ROOT}/install_rabbitmq.log}"

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

log_info "=== RabbitMQ Docker 安装开始 ==="
log_info "参数: DATA_ROOT=${DATA_ROOT}, CONTAINER_NAME=${CONTAINER_NAME}, RABBITMQ_VERSION=${RABBITMQ_VERSION}"
log_info "AMQP 端口: ${RABBITMQ_PORT}, 管理界面端口: ${RABBITMQ_MANAGEMENT_PORT}"
log_info "用户名: ${RABBITMQ_USER}, 密码: ${RABBITMQ_PASSWORD}"
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

# 函数：安装延迟队列插件
install_delayed_message_plugin() {
  log_info "=== 开始安装延迟队列插件 ==="
  local plugin_name="rabbitmq_delayed_message_exchange"
  local plugin_dir="${DATA_ROOT}/plugins"
  local plugin_file=""
  
  # 确保插件目录存在
  ensure_dir "${plugin_dir}"
  
  # 检查容器是否运行
  if ! docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
    log_error "容器 ${CONTAINER_NAME} 未运行，无法安装插件"
    return 1
  fi
  
  # 等待 RabbitMQ 服务就绪
  log_info "等待 RabbitMQ 服务就绪..."
  local max_retries=30
  local retry_count=0
  local rabbitmq_ready=false

  while [[ $retry_count -lt $max_retries ]]; do
    if docker exec "${CONTAINER_NAME}" rabbitmqctl status >/dev/null 2>&1; then
      rabbitmq_ready=true
      break
    fi
    retry_count=$((retry_count + 1))
    sleep 2
  done
  
  if [[ "${rabbitmq_ready}" != "true" ]]; then
    log_warn "RabbitMQ 服务可能未完全就绪，但继续尝试安装插件..."
  fi
  
  # 检查插件是否已启用
  if docker exec "${CONTAINER_NAME}" rabbitmq-plugins list 2>/dev/null | grep -q "${plugin_name}.*\[E\]"; then
    log_info "延迟队列插件 ${plugin_name} 已启用，跳过安装。"
    return 0
  fi
  
  log_info "开始安装延迟队列插件 ${plugin_name}..."
  
  # 使用固定版本的插件
  local plugin_filename="rabbitmq_delayed_message_exchange-3.13.0.ez"
  plugin_file="${plugin_dir}/${plugin_filename}"
  local plugin_url="https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/v3.13.0/${plugin_filename}"

  # 下载插件文件
  if [[ -f "${plugin_file}" ]]; then
    log_info "检测到本地已有插件文件 ${plugin_file}，跳过下载。"
  else
    log_info "下载延迟队列插件: ${plugin_url}"

    # 设置临时代理
    log_info "设置临时代理: http://192.168.3.2:7890"
    export http_proxy="http://192.168.3.2:7890"
    export https_proxy="http://192.168.3.2:7890"
    export HTTP_PROXY="http://192.168.3.2:7890"
    export HTTPS_PROXY="http://192.168.3.2:7890"

    if command -v wget >/dev/null 2>&1; then
      if wget -q --show-progress -O "${plugin_file}" "${plugin_url}"; then
        log_info "插件下载成功: ${plugin_file}"
      else
        log_error "插件下载失败，请检查网络连接或手动下载插件文件"
        log_error "下载地址: ${plugin_url}"
        log_error "保存路径: ${plugin_file}"
        return 1
      fi
    elif command -v curl >/dev/null 2>&1; then
      if curl -L -o "${plugin_file}" "${plugin_url}"; then
        log_info "插件下载成功: ${plugin_file}"
      else
        log_error "插件下载失败，请检查网络连接或手动下载插件文件"
        log_error "下载地址: ${plugin_url}"
        log_error "保存路径: ${plugin_file}"
        return 1
      fi
    else
      log_error "未找到 wget 或 curl，无法下载插件"
      log_error "请手动下载插件文件并保存到: ${plugin_file}"
      log_error "下载地址: ${plugin_url}"
      return 1
    fi

    # 清理临时代理设置
    log_info "清理临时代理设置"
    unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY
  fi
  
  # 将插件文件复制到容器中
  log_info "将插件文件复制到容器中..."
  if docker cp "${plugin_file}" "${CONTAINER_NAME}:/plugins/"; then
    log_info "插件文件已复制到容器 /plugins/ 目录"
  else
    log_error "插件文件复制失败"
    return 1
  fi
  
  # 等待一下确保文件已写入
  sleep 2
  
  # 启用插件（带重试机制，处理可能的文件锁定问题）
  log_info "启用延迟队列插件..."
  local max_retries=3
  local retry_count=0
  local enable_success=false
  
  while [[ $retry_count -lt $max_retries ]]; do
    if docker exec "${CONTAINER_NAME}" rabbitmq-plugins enable "${plugin_name}" 2>&1; then
      enable_success=true
      break
    else
      retry_count=$((retry_count + 1))
      if [[ $retry_count -lt $max_retries ]]; then
        log_warn "插件启用失败，可能是文件锁定问题，等待 3 秒后重试... (${retry_count}/${max_retries})"
        sleep 3
      fi
    fi
  done
  
  if [[ "${enable_success}" != "true" ]]; then
    log_error "延迟队列插件启用失败（已重试 ${max_retries} 次）"
    log_error "如果容器使用了 enabled_plugins 文件挂载，请移除该挂载后重新创建容器"
    log_error "请手动检查：docker exec -it ${CONTAINER_NAME} rabbitmq-plugins list"
    return 1
  else
    log_info "延迟队列插件启用成功"
  fi
  
  # 验证插件是否启用
  log_info "验证插件是否启用..."
  sleep 3
  if docker exec "${CONTAINER_NAME}" rabbitmq-plugins list | grep -q "${plugin_name}.*\[E\]"; then
    log_info "✓ 延迟队列插件 ${plugin_name} 已成功启用"
  else
    log_warn "延迟队列插件可能未正确启用，请手动检查："
    log_warn "  docker exec -it ${CONTAINER_NAME} rabbitmq-plugins list"
  fi
  
  log_info "=== 延迟队列插件安装完成 ==="
  return 0
}

# 检查容器是否已存在
CONTAINER_EXISTS=false
CONTAINER_RUNNING=false

if docker ps -a --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
  CONTAINER_EXISTS=true
  if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
    CONTAINER_RUNNING=true
    log_info "容器 ${CONTAINER_NAME} 已存在且正在运行。"
  else
    log_info "容器 ${CONTAINER_NAME} 已存在但已停止，正在启动..."
    if docker start "${CONTAINER_NAME}"; then
      log_info "容器启动成功，等待服务就绪..."
      sleep 5
      CONTAINER_RUNNING=true
    else
      log_error "容器启动失败"
      exit 1
    fi
  fi
  
  # 容器已存在，检查并安装延迟队列插件
  if [[ "${CONTAINER_RUNNING}" == "true" ]]; then
    install_delayed_message_plugin || {
      log_error "延迟队列插件安装失败"
      exit 1
    }
    log_info "=== RabbitMQ Docker 安装结束（容器已存在，插件已检查/安装） ==="
    exit 0
  fi
fi

# 1. 创建目录
log_info "创建目录结构: ${DATA_ROOT}/{conf,data,log,plugins}"
ensure_dir "${DATA_ROOT}"
ensure_dir "${DATA_ROOT}/conf"
ensure_dir "${DATA_ROOT}/data"
ensure_dir "${DATA_ROOT}/log"
ensure_dir "${DATA_ROOT}/plugins"

# 2. 准备配置文件
CONF_PATH="${DATA_ROOT}/conf/rabbitmq.conf"
PLUGINS_PATH="${DATA_ROOT}/conf/enabled_plugins"

# 2.1 创建 rabbitmq.conf 配置文件
if [[ -f "${CONF_PATH}" ]]; then
  log_warn "检测到已有配置文件 ${CONF_PATH}，将直接复用。"
else
  log_info "写入默认配置到 ${CONF_PATH}"
  cat > "${CONF_PATH}" <<EOF
# RabbitMQ 配置文件

# 网络配置
listeners.tcp.default = ${RABBITMQ_PORT}
management.tcp.port = ${RABBITMQ_MANAGEMENT_PORT}

# 集群配置（单节点模式）
cluster_formation.peer_discovery_backend = classic_config

# 日志配置
log.console = true
log.console.level = info
log.file = false
# log.file = /var/log/rabbitmq/rabbitmq.log
# log.file.level = info
# log.file.rotation.date = \$D0
# log.file.rotation.size = 0

# 内存和磁盘配置
vm_memory_high_watermark.relative = 0.4
disk_free_limit.relative = 1.0

# 队列配置
queue_master_locator = min-masters

# 管理界面配置
management.tcp.ip = 0.0.0.0
management.rates_mode = basic

# 默认用户配置（通过环境变量设置）
# default_user = guest
# default_pass = guest

# 其他配置
collect_statistics_interval = 5000
EOF
fi

# 2.2 注意：enabled_plugins 文件不再挂载，由 RabbitMQ 容器自动管理
# 插件将通过 rabbitmq-plugins enable 命令启用，而不是通过文件挂载
# 这样可以避免文件锁定问题（:ebusy 错误）

log_info "设置目录与配置文件权限"
chmod 755 "${DATA_ROOT}/data" "${DATA_ROOT}/log"
chmod 644 "${CONF_PATH}"

# 3. 检查并拉取镜像（若本地无镜像）
IMAGE_NAME="rabbitmq:${RABBITMQ_VERSION}-management"
log_info "检查并拉取镜像 ${IMAGE_NAME}"
if ! docker image inspect "${IMAGE_NAME}" >/dev/null 2>&1; then
  log_info "本地不存在镜像 ${IMAGE_NAME}，开始拉取..."
  if docker pull "${IMAGE_NAME}"; then
    log_info "镜像 ${IMAGE_NAME} 拉取成功"
  else
    log_error "镜像拉取失败"
    exit 1
  fi
else
  log_info "镜像 ${IMAGE_NAME} 已存在，跳过拉取"
fi

# 4. 启动容器
log_info "启动容器 ${CONTAINER_NAME}"

CONTAINER_ID=$(docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart=unless-stopped \
  --privileged=true \
  -p "${RABBITMQ_PORT}:5672" \
  -p "${RABBITMQ_MANAGEMENT_PORT}:15672" \
  -e TZ=Asia/Shanghai \
  -e RABBITMQ_DEFAULT_USER="${RABBITMQ_USER}" \
  -e RABBITMQ_DEFAULT_PASS="${RABBITMQ_PASSWORD}" \
  -v "${CONF_PATH}:/etc/rabbitmq/rabbitmq.conf" \
  -v "${DATA_ROOT}/data:/var/lib/rabbitmq" \
  -v "${DATA_ROOT}/log:/var/log/rabbitmq" \
  "rabbitmq:${RABBITMQ_VERSION}-management")

log_info "容器已创建，ID: ${CONTAINER_ID}"
log_info "等待容器启动..."
sleep 5

# 显示容器启动日志
log_info "容器启动日志："
docker logs "${CONTAINER_NAME}" 2>&1 | head -30 | while IFS= read -r line; do
  log_info "  $line"
done

if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
  log_info "容器 ${CONTAINER_NAME} 启动成功。"
  log_info "AMQP 端口映射: ${RABBITMQ_PORT} -> 5672"
  log_info "管理界面端口映射: ${RABBITMQ_MANAGEMENT_PORT} -> 15672"
  log_info "用户名: ${RABBITMQ_USER}"
  log_info "密码: ${RABBITMQ_PASSWORD}"
else
  log_error "容器未在预期时间内启动，请检查日志：docker logs ${CONTAINER_NAME}"
  exit 1
fi

# 5. 等待 RabbitMQ 服务就绪并验证
log_info "等待 RabbitMQ 服务就绪..."
MAX_RETRIES=60
RETRY_COUNT=0
RABBITMQ_READY=false

while [[ $RETRY_COUNT -lt $MAX_RETRIES ]]; do
  # 检查 RabbitMQ 是否就绪（通过管理 API）
  if curl -s -u "${RABBITMQ_USER}:${RABBITMQ_PASSWORD}" "http://localhost:${RABBITMQ_MANAGEMENT_PORT}/api/overview" >/dev/null 2>&1; then
    RABBITMQ_READY=true
    log_info "RabbitMQ 服务已就绪"
    break
  fi
  RETRY_COUNT=$((RETRY_COUNT + 1))
  if [[ $((RETRY_COUNT % 10)) -eq 0 ]]; then
    log_info "等待 RabbitMQ 服务启动中... (${RETRY_COUNT}/${MAX_RETRIES})"
  fi
  sleep 2
done

if [[ "${RABBITMQ_READY}" != "true" ]]; then
  log_warn "RabbitMQ 服务在预期时间内未就绪，但容器已启动"
  log_warn "请手动检查：docker logs ${CONTAINER_NAME}"
else
  # 测试 RabbitMQ 连接
  log_info "测试 RabbitMQ 连接..."
  if curl -s -u "${RABBITMQ_USER}:${RABBITMQ_PASSWORD}" "http://localhost:${RABBITMQ_MANAGEMENT_PORT}/api/overview" >/dev/null 2>&1; then
    log_info "✓ RabbitMQ 管理界面连接测试成功"
    
    # 获取 RabbitMQ 版本信息
    RABBITMQ_VERSION_INFO=$(curl -s -u "${RABBITMQ_USER}:${RABBITMQ_PASSWORD}" "http://localhost:${RABBITMQ_MANAGEMENT_PORT}/api/overview" | grep -o '"rabbitmq_version":"[^"]*"' | head -1 || echo "")
    if [[ -n "${RABBITMQ_VERSION_INFO}" ]]; then
      log_info "  ${RABBITMQ_VERSION_INFO}"
    fi
  else
    log_warn "RabbitMQ 连接测试失败，但服务可能正常运行"
  fi
fi

# 6. 安装延迟队列插件
install_delayed_message_plugin || {
  log_error "延迟队列插件安装失败"
  exit 1
}

log_info "=== RabbitMQ Docker 安装完成 ==="
log_info ""
log_info "使用说明："
log_info "1. 管理界面访问："
log_info "   http://localhost:${RABBITMQ_MANAGEMENT_PORT}"
log_info "   用户名: ${RABBITMQ_USER}"
log_info "   密码: ${RABBITMQ_PASSWORD}"
log_info ""
log_info "2. 连接信息："
log_info "   AMQP 地址: localhost:${RABBITMQ_PORT}"
log_info "   用户名: ${RABBITMQ_USER}"
log_info "   密码: ${RABBITMQ_PASSWORD}"
log_info ""
log_info "3. 常用命令："
log_info "   查看日志: docker logs ${CONTAINER_NAME}"
log_info "   查看日志（实时）: docker logs -f ${CONTAINER_NAME}"
log_info "   进入容器: docker exec -it ${CONTAINER_NAME} bash"
log_info "   查看插件: docker exec -it ${CONTAINER_NAME} rabbitmq-plugins list"
log_info "   查看用户: docker exec -it ${CONTAINER_NAME} rabbitmqctl list_users"
log_info "   查看队列: docker exec -it ${CONTAINER_NAME} rabbitmqctl list_queues"
log_info ""
log_info "4. 延迟队列插件："
log_info "   已安装插件: rabbitmq_delayed_message_exchange"
log_info "   插件文件位置: ${DATA_ROOT}/plugins/"
log_info "   使用延迟交换器类型: x-delayed-message"
log_info ""
log_info "5. 数据目录："
log_info "   ${DATA_ROOT}/data"
log_info ""
log_info "6. 配置文件："
log_info "   ${CONF_PATH}"
log_info "   ${PLUGINS_PATH}"
log_info ""
log_info "7. 停止/启动容器："
log_info "   docker stop ${CONTAINER_NAME}"
log_info "   docker start ${CONTAINER_NAME}"
log_info ""
log_info "8. 创建虚拟主机（可选）："
log_info "   docker exec -it ${CONTAINER_NAME} rabbitmqctl add_vhost /myvhost"
log_info ""
log_info "9. 创建新用户（可选）："
log_info "   docker exec -it ${CONTAINER_NAME} rabbitmqctl add_user username password"
log_info "   docker exec -it ${CONTAINER_NAME} rabbitmqctl set_permissions -p / username \".*\" \".*\" \".*\""
log_info "   docker exec -it ${CONTAINER_NAME} rabbitmqctl set_user_tags username administrator"
log_info ""
log_info "10. 延迟队列插件使用示例："
log_info "   创建延迟交换器类型: x-delayed-message"
log_info "   延迟参数: x-delay (单位: 毫秒)"
log_info "   更多信息: https://github.com/rabbitmq/rabbitmq-delayed-message-exchange"

