#!/usr/bin/env bash
# ========================================================================================
# RocketMQ Docker 安装脚本 (RocketMQ Docker Installer)
# ========================================================================================
#
# 功能说明:
#   本脚本用于通过 Docker 快速部署 Apache RocketMQ 分布式消息中间件。
#   RocketMQ 是阿里巴巴开源的高性能分布式消息传递和流处理平台，
#   具有低延迟、高吞吐量和强大的消息处理能力。
#
# 主要特性:
#   ✓ 完整的 RocketMQ 集群: 包含 NameServer、Broker 和 Proxy
#   ✓ 自动网络配置: 创建专用 Docker 网络确保服务间通信
#   ✓ 数据持久化: 配置本地数据和日志目录持久化存储
#   ✓ 健康检查: 自动验证各个组件的启动状态
#   ✓ 生产就绪: 单节点单副本配置，适合开发和测试环境
#
# RocketMQ 简介:
#   RocketMQ 是一个统一的企业级消息中间件，具有以下特点：
#   - 金融级稳定性: 支持事务消息和消息轨迹
#   - 超强性能: 单机支持百万级消息并发
#   - 灵活扩展: 支持集群扩展和负载均衡
#   - 丰富功能: 支持延迟消息、消息过滤、死信队列等
#   - 生态完善: 提供丰富的客户端 SDK 和管理工具
#
# 架构组件:
#   - NameServer: 命名服务，提供路由信息和管理功能
#   - Broker: 消息存储和转发服务，处理消息的收发
#   - Proxy: 代理服务，提供更丰富的 API 和协议支持
#
# 配置参数:
#   ROCKETMQ_VERSION      - RocketMQ 版本 (默认: 5.3.2)
#   ROCKETMQ_DATA_ROOT    - 数据根目录 (默认: /root/rocketmq)
#   NAMESRV_PORT          - NameServer 端口 (默认: 9876)
#   BROKER_PORT_1         - Broker 主端口 (默认: 10912)
#   BROKER_PORT_2         - Broker VIP 端口 (默认: 10911)
#   BROKER_PORT_3         - Broker HA 端口 (默认: 10909)
#   PROXY_PORT_1          - Proxy HTTP 端口 (默认: 8080)
#   PROXY_PORT_2          - Proxy gRPC 端口 (默认: 8081)
#   ROCKETMQ_DASHBOARD_PORT - Dashboard Web 控制台端口 (默认: 8888)
#   ROCKETMQ_CONTAINER_NAME_DASHBOARD - Dashboard 容器名 (默认: rmqdashboard)
#   ROCKETMQ_BROKER_IP - Broker 对外注册 IP（客户端连此 IP:10911），宿主机/虚拟机 IP，默认 192.168.3.100
#
# 端口说明:
#   9876  - NameServer 服务端口
#   10911 - Broker 主要通信端口
#   10912 - Broker 高可用通信端口
#   10909 - Broker 快速失败端口
#   8080  - Proxy HTTP API 端口
#   8081  - Proxy gRPC 端口
#   8888  - RocketMQ Dashboard Web 控制台（宿主机端口，容器内 Tomcat 监听 8082）
#
# 目录结构:
#   ${DATA_ROOT}/           - 根目录（默认 /root/rocketmq）
#     ├── conf/            - 配置文件目录（broker.conf）
#     ├── data/            - Broker 消息存储（挂载到容器 store）
#     ├── log/namesrv/      - NameServer 日志（单独挂载，避免与 Broker 混用）
#     ├── log/broker/       - Broker/Proxy 日志（单独挂载）
#     └── install_rocketmq.log - 安装日志
#
# 使用场景:
#   1. 开发环境: 单节点 RocketMQ 实例快速搭建
#   2. 测试环境: 完整的 RocketMQ 集群功能验证
#   3. 生产环境: 作为生产环境部署的参考配置
#
# 消息类型支持:
#   - 普通消息: 可靠的异步通信
#   - 顺序消息: 保证消息处理的顺序性
#   - 延迟消息: 支持定时消息投递
#   - 事务消息: 保证分布式事务的一致性
#   - 批量消息: 提高传输效率
#
# 管理工具:
#   1. RocketMQ Dashboard: Web 管理界面
#   2. mqadmin: 命令行管理工具
#   3. 客户端工具: 支持多种语言的客户端 SDK
#
# 注意事项:
#   ⚠️  单节点部署仅适用于开发/测试环境
#   ⚠️  生产环境至少需要 2 个 NameServer 和多 Broker 节点
#   ⚠️  注意数据目录的磁盘空间，消息数据增长较快
#   ⚠️  合理配置 JVM 参数，避免内存不足
#
# 故障排除:
#   - 查看日志: docker logs rmqnamesrv 或 docker logs rmqbroker
#   - 检查端口: netstat -tlnp | grep 9876
#   - 测试连接: telnet 192.168.3.100 9876
#   - 集群状态: 使用 mqadmin clusterList 查看
#
# 性能优化:
#   - 调整 JVM 堆内存大小
#   - 配置适当的消息存储策略
#   - 设置合理的刷盘策略
#   - 监控系统资源使用情况
#
# 系统要求:
#   - Docker 环境正常运行
#   - 足够的磁盘空间用于消息存储
#   - 网络端口 9876, 10911, 10912, 10909, 8080, 8081, 8888(Dashboard) 未被占用
#   - 建议配置至少 4GB 内存
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
DATA_ROOT="${ROCKETMQ_DATA_ROOT:-/root/rocketmq}"
CONTAINER_NAME_NS="${ROCKETMQ_CONTAINER_NAME_NS:-rmqnamesrv}"
CONTAINER_NAME_BROKER="${ROCKETMQ_CONTAINER_NAME_BROKER:-rmqbroker}"
ROCKETMQ_VERSION="${ROCKETMQ_VERSION:-5.3.2}"
ROCKETMQ_NETWORK="${ROCKETMQ_NETWORK:-rocketmq}"
NAMESRV_PORT="${NAMESRV_PORT:-9876}"
BROKER_PORT_1="${BROKER_PORT_1:-10912}"
BROKER_PORT_2="${BROKER_PORT_2:-10911}"
BROKER_PORT_3="${BROKER_PORT_3:-10909}"
PROXY_PORT_1="${PROXY_PORT_1:-8080}"
PROXY_PORT_2="${PROXY_PORT_2:-8081}"
CONTAINER_NAME_DASHBOARD="${ROCKETMQ_CONTAINER_NAME_DASHBOARD:-rmqdashboard}"
DASHBOARD_PORT="${ROCKETMQ_DASHBOARD_PORT:-8888}"
DASHBOARD_VERSION="${ROCKETMQ_DASHBOARD_VERSION:-latest}"
# Broker 对外 IP：Java 等客户端通过 NameServer 拿到的地址，须为宿主机可访问的 IP（如虚拟机 IP）
BROKER_IP="${ROCKETMQ_BROKER_IP:-192.168.3.100}"
LOG_FILE="${LOG_FILE:-${DATA_ROOT}/install_rocketmq.log}"

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

log_info "=== RocketMQ Docker 安装开始 ==="
log_info "参数: DATA_ROOT=${DATA_ROOT}, ROCKETMQ_VERSION=${ROCKETMQ_VERSION}"
log_info "NameServer 容器: ${CONTAINER_NAME_NS}, 端口: ${NAMESRV_PORT}"
log_info "Broker 容器: ${CONTAINER_NAME_BROKER}, 网络: ${ROCKETMQ_NETWORK}"
log_info "Broker 端口: ${BROKER_PORT_1}, ${BROKER_PORT_2}, ${BROKER_PORT_3}"
log_info "Proxy 端口: ${PROXY_PORT_1}, ${PROXY_PORT_2}"
log_info "Dashboard 容器: ${CONTAINER_NAME_DASHBOARD}, 端口: ${DASHBOARD_PORT}"
log_info "Broker 对外 IP: ${BROKER_IP}（客户端将连接此 IP:10911）"
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

# 1. 创建目录（NameServer 与 Broker 日志分目录挂载，避免混用）
log_info "创建目录结构: ${DATA_ROOT}/{conf,data,log/namesrv,log/broker}"
ensure_dir "${DATA_ROOT}"
ensure_dir "${DATA_ROOT}/conf"
ensure_dir "${DATA_ROOT}/data"
ensure_dir "${DATA_ROOT}/log/namesrv"
ensure_dir "${DATA_ROOT}/log/broker"

# 2. 准备 Broker 配置文件
BROKER_CONF_PATH="${DATA_ROOT}/conf/broker.conf"
log_info "准备 Broker 配置文件: ${BROKER_CONF_PATH}, brokerIP1=${BROKER_IP}"
cat > "${BROKER_CONF_PATH}" <<EOF
# Broker 配置文件
# Broker 对外注册 IP（客户端通过 NameServer 拿到此地址连接，须为宿主机/虚拟机 IP）
brokerIP1=${BROKER_IP}

# Broker 集群名称
brokerClusterName=DefaultCluster

# Broker 名称
brokerName=broker-a

# Broker ID，0 表示 Master，>0 表示 Slave
brokerId=0

# NameServer 地址
# namesrvAddr=127.0.0.1:9876

# 删除文件时间点，默认凌晨 4 点
deleteWhen=04

# 文件保留时间，默认 48 小时
fileReservedTime=48

# Broker 角色：ASYNC_MASTER, SYNC_MASTER, SLAVE
brokerRole=ASYNC_MASTER

# 刷盘方式：ASYNC_FLUSH, SYNC_FLUSH
flushDiskType=ASYNC_FLUSH

# 数据目录
storePathRootDir=/home/rocketmq/rocketmq-5.3.2/store

# CommitLog 目录
storePathCommitLog=/home/rocketmq/rocketmq-5.3.2/store/commitlog

# 消费队列目录
storePathConsumeQueue=/home/rocketmq/rocketmq-5.3.2/store/consumequeue

# 索引目录
storePathIndex=/home/rocketmq/rocketmq-5.3.2/store/index

# Checkpoint 文件路径
storeCheckpoint=/home/rocketmq/rocketmq-5.3.2/store/checkpoint

# Abort 文件路径
abortFile=/home/rocketmq/rocketmq-5.3.2/store/abort

# 限制消息大小，默认为 4M
maxMessageSize=4194304

# 是否允许 Broker 自动创建 Topic
autoCreateTopicEnable=true

# 是否允许 Broker 自动创建订阅组
autoCreateSubscriptionGroup=true

# 监听端口
listenPort=10911

# HA 监听端口
haListenPort=10912

# 发送消息线程池数量
sendMessageThreadPoolNums=16

# 拉取消息线程池数量
pullMessageThreadPoolNums=16

# 管理权限
brokerPermission=6

# 启用属性过滤
enablePropertyFilter=true
EOF

log_info "设置配置文件权限"
chmod 644 "${BROKER_CONF_PATH}"

# 2.1 准备 Proxy 配置文件（Broker 使用 --enable-proxy 时必须存在）
PROXY_CONF_PATH="${DATA_ROOT}/conf/rmq-proxy.json"
log_info "准备 Proxy 配置文件: ${PROXY_CONF_PATH}"
cat > "${PROXY_CONF_PATH}" <<'PROXYEOF'
{
  "rocketMQClusterName": "DefaultCluster",
  "proxyClusterName": "DefaultCluster",
  "grpcServerPort": 8081,
  "remotingServerPort": 8080,
  "enablePrintJstack": false,
  "metricsExporterType": "DISABLE"
}
PROXYEOF
chmod 644 "${PROXY_CONF_PATH}"

# 设置日志目录权限（RocketMQ 容器需要写入权限）
log_info "设置日志目录权限"
chmod 777 "${DATA_ROOT}/log/namesrv"
chmod 777 "${DATA_ROOT}/log/broker"

# 3. 创建 Docker 网络
log_info "创建 Docker 网络: ${ROCKETMQ_NETWORK}"
if docker network ls --format '{{.Name}}' | grep -qw "${ROCKETMQ_NETWORK}"; then
  log_info "网络 ${ROCKETMQ_NETWORK} 已存在，跳过创建"
else
  docker network create "${ROCKETMQ_NETWORK}"
  log_info "网络 ${ROCKETMQ_NETWORK} 创建成功"
fi

# 4. 检查并拉取 RocketMQ 镜像
log_info "检查并拉取 RocketMQ 镜像: apache/rocketmq:${ROCKETMQ_VERSION}"
if ! docker image inspect "apache/rocketmq:${ROCKETMQ_VERSION}" >/dev/null 2>&1; then
  log_info "本地不存在镜像 apache/rocketmq:${ROCKETMQ_VERSION}，开始拉取..."
  if docker pull "apache/rocketmq:${ROCKETMQ_VERSION}"; then
    log_info "镜像 apache/rocketmq:${ROCKETMQ_VERSION} 拉取成功"
  else
    log_error "镜像拉取失败"
    exit 1
  fi
else
  log_info "镜像 apache/rocketmq:${ROCKETMQ_VERSION} 已存在，跳过拉取"
fi

# 5. 启动 NameServer（已存在则跳过创建，仅确保运行中）
log_info "启动 NameServer 容器: ${CONTAINER_NAME_NS}"
if docker ps -a --format '{{.Names}}' | grep -qw "${CONTAINER_NAME_NS}"; then
  if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME_NS}"; then
    log_info "NameServer 容器 ${CONTAINER_NAME_NS} 已存在且正在运行，跳过创建"
  else
    log_info "NameServer 容器 ${CONTAINER_NAME_NS} 已存在但已停止，尝试启动..."
    if docker start "${CONTAINER_NAME_NS}"; then
      log_info "NameServer 容器 ${CONTAINER_NAME_NS} 启动成功"
    else
      log_error "NameServer 容器 ${CONTAINER_NAME_NS} 启动失败"
      exit 1
    fi
  fi
else
  NAMESRV_CONTAINER_ID=$(docker run -d \
    --name "${CONTAINER_NAME_NS}" \
    --restart=unless-stopped \
    --network "${ROCKETMQ_NETWORK}" \
    -p "${NAMESRV_PORT}:9876" \
    -u 0 \
    -e TZ=Asia/Shanghai \
    -v "${DATA_ROOT}/log/namesrv:/home/rocketmq/logs/rocketmqlogs" \
    "apache/rocketmq:${ROCKETMQ_VERSION}" \
    sh mqnamesrv)
  log_info "NameServer 容器已创建，ID: ${NAMESRV_CONTAINER_ID}"
fi

log_info "等待 NameServer 启动..."
sleep 5

# 验证 NameServer 启动
log_info "验证 NameServer 启动状态..."
if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME_NS}"; then
  log_info "NameServer 容器运行正常"

  # 检查 NameServer 启动日志
  log_info "NameServer 启动日志："
  docker logs "${CONTAINER_NAME_NS}" 2>&1 | head -20 | while IFS= read -r line; do
    log_info "  $line"
  done

  # 等待 NameServer 完全启动
  log_info "等待 NameServer 服务完全启动..."
  sleep 10

  # 检查 NameServer 是否响应
  if docker logs "${CONTAINER_NAME_NS}" 2>&1 | grep -q "The Name Server boot success"; then
    log_info "✓ NameServer 启动成功"
  else
    log_warn "NameServer 启动日志中未找到成功信息，但容器正在运行"
  fi
else
  log_error "NameServer 容器启动失败"
  exit 1
fi

# 6. 启动 Broker + Proxy（已存在则跳过创建，仅确保运行中）
log_info "启动 Broker + Proxy 容器: ${CONTAINER_NAME_BROKER}"

# 检测系统内存并调整配置
SYSTEM_MEMORY_KB=$(grep MemTotal /proc/meminfo | awk '{print $2}')
SYSTEM_MEMORY_GB=$((SYSTEM_MEMORY_KB / 1024 / 1024))

log_info "检测到系统内存: ${SYSTEM_MEMORY_GB}GB"

if [ $SYSTEM_MEMORY_GB -ge 8 ]; then
  # 8GB+ 系统：使用标准配置
  MEMORY_LIMIT="4g"
  MEMORY_SWAP="6g"
  JAVA_OPT="-Xms1g -Xmx2g -Xmn512m -XX:MaxDirectMemorySize=512m"
elif [ $SYSTEM_MEMORY_GB -ge 4 ]; then
  # 4-8GB 系统：使用中等配置
  MEMORY_LIMIT="3g"
  MEMORY_SWAP="4g"
  JAVA_OPT="-Xms512m -Xmx1g -Xmn256m -XX:MaxDirectMemorySize=256m"
else
  # 4GB 以下系统：使用最小配置
  MEMORY_LIMIT="2g"
  MEMORY_SWAP="3g"
  JAVA_OPT="-Xms256m -Xmx512m -Xmn128m -XX:MaxDirectMemorySize=128m -XX:+UseG1GC -XX:G1HeapRegionSize=2m"
fi

log_info "使用内存配置: 容器=${MEMORY_LIMIT}, JVM=${JAVA_OPT}"

if docker ps -a --format '{{.Names}}' | grep -qw "${CONTAINER_NAME_BROKER}"; then
  if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME_BROKER}"; then
    log_info "Broker 容器 ${CONTAINER_NAME_BROKER} 已存在且正在运行，跳过创建"
  else
    log_info "Broker 容器 ${CONTAINER_NAME_BROKER} 已存在但已停止，尝试启动..."
    if docker start "${CONTAINER_NAME_BROKER}"; then
      log_info "Broker 容器 ${CONTAINER_NAME_BROKER} 启动成功"
    else
      log_error "Broker 容器 ${CONTAINER_NAME_BROKER} 启动失败"
      exit 1
    fi
  fi
else
  BROKER_CONTAINER_ID=$(docker run -d \
    --name "${CONTAINER_NAME_BROKER}" \
    --restart=unless-stopped \
    --network "${ROCKETMQ_NETWORK}" \
    --memory="${MEMORY_LIMIT}" \
    --memory-swap="${MEMORY_SWAP}" \
    -u 0 \
    -p "${BROKER_PORT_1}:10912" \
    -p "${BROKER_PORT_2}:10911" \
    -p "${BROKER_PORT_3}:10909" \
    -p "${PROXY_PORT_1}:8080" \
    -p "${PROXY_PORT_2}:8081" \
    -e "NAMESRV_ADDR=${CONTAINER_NAME_NS}:9876" \
    -e "JAVA_OPT=${JAVA_OPT}" \
    -e TZ=Asia/Shanghai \
    -v "${DATA_ROOT}/conf:/home/rocketmq/rocketmq-5.3.2/conf" \
    -v "${DATA_ROOT}/data:/home/rocketmq/rocketmq-5.3.2/store" \
    -v "${DATA_ROOT}/log/broker:/home/rocketmq/logs/rocketmqlogs" \
    "apache/rocketmq:${ROCKETMQ_VERSION}" \
    sh mqbroker --enable-proxy \
    -c /home/rocketmq/rocketmq-5.3.2/conf/broker.conf)

  log_info "Broker 容器已创建，ID: ${BROKER_CONTAINER_ID}"
fi
log_info "等待 Broker + Proxy 启动..."
sleep 15

# 验证 Broker 启动
log_info "验证 Broker 启动状态..."
if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME_BROKER}"; then
  log_info "Broker 容器运行正常"

  # 检查 Broker 日志
  log_info "检查 Broker 启动日志..."
  if docker exec "${CONTAINER_NAME_BROKER}" tail -n 20 /home/rocketmq/logs/rocketmqlogs/proxy.log 2>/dev/null | grep -q "The broker boot success"; then
    log_info "✓ Broker 启动成功"
  else
    log_info "Broker 启动日志："
    docker exec "${CONTAINER_NAME_BROKER}" tail -n 20 /home/rocketmq/logs/rocketmqlogs/proxy.log 2>/dev/null | while IFS= read -r line; do
      log_info "  $line"
    done || log_warn "无法读取 Broker 日志"
  fi

  # 检查 Proxy 日志
  log_info "检查 Proxy 启动日志..."
  if docker exec "${CONTAINER_NAME_BROKER}" tail -n 20 /home/rocketmq/logs/rocketmqlogs/proxy.log 2>/dev/null | grep -q "The proxy boot success"; then
    log_info "✓ Proxy 启动成功"
  else
    docker exec "${CONTAINER_NAME_BROKER}" tail -n 20 /home/rocketmq/logs/rocketmqlogs/proxy.log 2>/dev/null | while IFS= read -r line; do
      log_info "  $line"
    done || log_warn "无法读取 Proxy 日志"
  fi
else
  log_error "Broker 容器启动失败"
  exit 1
fi

# 7. 等待服务完全就绪
log_info "等待 RocketMQ 集群完全就绪..."
sleep 10
log_info "RocketMQ 集群部署完成"

# 8. 启动 RocketMQ Dashboard（Web 控制台）
log_info "检查并启动 RocketMQ Dashboard: ${CONTAINER_NAME_DASHBOARD}"
if docker ps -a --format '{{.Names}}' | grep -qw "${CONTAINER_NAME_DASHBOARD}"; then
  if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME_DASHBOARD}"; then
    log_info "Dashboard 容器 ${CONTAINER_NAME_DASHBOARD} 已存在且正在运行，跳过。"
  else
    log_info "启动已存在的 Dashboard 容器..."
    docker start "${CONTAINER_NAME_DASHBOARD}" || log_warn "Dashboard 启动失败"
  fi
else
  log_info "拉取 RocketMQ Dashboard 镜像: apacherocketmq/rocketmq-dashboard:${DASHBOARD_VERSION}"
  if ! docker image inspect "apacherocketmq/rocketmq-dashboard:${DASHBOARD_VERSION}" >/dev/null 2>&1; then
    if docker pull "apacherocketmq/rocketmq-dashboard:${DASHBOARD_VERSION}"; then
      log_info "镜像 apacherocketmq/rocketmq-dashboard:${DASHBOARD_VERSION} 拉取成功"
    else
      log_warn "Dashboard 镜像拉取失败，跳过 Dashboard 部署。可稍后手动运行："
      log_warn "  docker run -d --name ${CONTAINER_NAME_DASHBOARD} --network ${ROCKETMQ_NETWORK} \\"
      log_warn "    -e JAVA_OPTS=\"-Drocketmq.namesrv.addr=${CONTAINER_NAME_NS}:9876\" \\"
      log_warn "    -p ${DASHBOARD_PORT}:8082 apacherocketmq/rocketmq-dashboard:${DASHBOARD_VERSION}"
    fi
  fi
  if docker image inspect "apacherocketmq/rocketmq-dashboard:${DASHBOARD_VERSION}" >/dev/null 2>&1; then
    docker run -d \
      --name "${CONTAINER_NAME_DASHBOARD}" \
      --restart=unless-stopped \
      --network "${ROCKETMQ_NETWORK}" \
      -e "JAVA_OPTS=-Drocketmq.namesrv.addr=${CONTAINER_NAME_NS}:9876" \
      -p "${DASHBOARD_PORT}:8082" \
      -e TZ=Asia/Shanghai \
      "apacherocketmq/rocketmq-dashboard:${DASHBOARD_VERSION}"
    log_info "✓ RocketMQ Dashboard 已启动，访问地址: http://192.168.3.100:${DASHBOARD_PORT}"
  fi
fi

log_info "=== RocketMQ Docker 安装完成 ==="
log_info ""
log_info "集群信息："
log_info "  NameServer: ${CONTAINER_NAME_NS} (端口: ${NAMESRV_PORT})"
log_info "  Broker: ${CONTAINER_NAME_BROKER} (端口: ${BROKER_PORT_1}, ${BROKER_PORT_2}, ${BROKER_PORT_3})"
log_info "  Proxy: ${CONTAINER_NAME_BROKER} (端口: ${PROXY_PORT_1}, ${PROXY_PORT_2})"
log_info "  Dashboard: ${CONTAINER_NAME_DASHBOARD} (端口: ${DASHBOARD_PORT}) → Web 控制台 http://192.168.3.100:${DASHBOARD_PORT}"
log_info ""
log_info "使用说明："
log_info "1. 查看服务状态："
log_info "   docker ps | grep rocketmq"
log_info ""
log_info "2. 查看 NameServer 日志："
log_info "   docker logs ${CONTAINER_NAME_NS}"
log_info ""
log_info "3. 查看 Broker 日志："
log_info "   docker logs ${CONTAINER_NAME_BROKER}"
log_info "   或: docker exec ${CONTAINER_NAME_BROKER} tail -f /home/rocketmq/logs/rocketmqlogs/proxy.log"
log_info ""
log_info "4. 数据目录："
log_info "   ${DATA_ROOT}/data"
log_info ""
log_info "5. 日志目录："
log_info "   NameServer: ${DATA_ROOT}/log/namesrv"
log_info "   Broker:     ${DATA_ROOT}/log/broker"
log_info ""
log_info "5.1 挂载信息（Broker 容器）："
log_info "   ${DATA_ROOT}/conf -> /home/rocketmq/rocketmq-5.3.2/conf"
log_info "   ${DATA_ROOT}/data -> /home/rocketmq/rocketmq-5.3.2/store"
log_info "   ${DATA_ROOT}/log/broker -> /home/rocketmq/logs/rocketmqlogs"
log_info ""
log_info "5.2 挂载信息（NameServer 容器）："
log_info "   ${DATA_ROOT}/log/namesrv -> /home/rocketmq/logs/rocketmqlogs"
log_info ""
log_info "6. 配置文件："
log_info "   ${BROKER_CONF_PATH}"
log_info ""
log_info "7. 停止服务："
log_info "   docker stop ${CONTAINER_NAME_BROKER}"
log_info "   docker stop ${CONTAINER_NAME_NS}"
log_info ""
log_info "8. 启动服务："
log_info "   docker start ${CONTAINER_NAME_NS}"
log_info "   docker start ${CONTAINER_NAME_BROKER}"
log_info ""
log_info "9. 查看/停止 Dashboard："
log_info "   docker logs ${CONTAINER_NAME_DASHBOARD}"
log_info "   docker stop ${CONTAINER_NAME_DASHBOARD}"
log_info ""
log_info "10. 清理（删除容器和网络）："
log_info "   docker rm -f ${CONTAINER_NAME_DASHBOARD} ${CONTAINER_NAME_BROKER} ${CONTAINER_NAME_NS}"
log_info "   docker network rm ${ROCKETMQ_NETWORK}"
log_info ""
log_info "注意：这是一个单节点单副本的 RocketMQ 集群，适合开发和测试环境。"
log_info "生产环境建议部署多副本集群以提高可用性。"
log_info ""
log_info "若 Java 客户端报错 connect to 172.18.x.x:10911 failed，说明 Broker 注册了 Docker 内网 IP。"
log_info "请确认 ${BROKER_CONF_PATH} 中 brokerIP1 为宿主机/虚拟机 IP（如 192.168.3.100），并执行："
log_info "   docker restart ${CONTAINER_NAME_BROKER}"
