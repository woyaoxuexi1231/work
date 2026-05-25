#!/usr/bin/env bash
# ========================================================================================
# ZooKeeper Docker 安装脚本 (ZooKeeper Docker Installer)
# ========================================================================================
#
# 功能说明:
#   本脚本用于通过 Docker 快速部署 Apache ZooKeeper 分布式协调服务。
#   ZooKeeper 是分布式系统的重要组件，提供配置管理、服务发现和分布式锁功能。
#
# 主要特性:
#   ✓ 自动配置: 智能配置集群参数和网络端口
#   ✓ 数据持久化: 配置数据和日志目录持久化存储
#   ✓ 健康检查: 自动验证服务启动状态和连接性
#   ✓ 多端口支持: 客户端、对等和选举端口配置
#
# 端口说明:
#   2181 - 客户端连接端口 (主要使用端口)
#   2888 - 集群对等通信端口
#   3888 - 集群选举端口
#   8181 - 管理界面端口 (AdminServer)
#
# 目录结构:
#   /root/zookeeper/     - 根目录
#     ├── data/         - 数据存储目录
#     ├── log/          - 日志存储目录
#     └── datalog/      - 事务日志目录
#
# 注意: 单节点部署仅适用于开发测试环境
# 作者: 系统运维脚本 | 版本: v1.0 | 更新时间: 2024-01
# ========================================================================================

# Ensure we are running under bash (Ubuntu /bin/sh 不支持 pipefail)
if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_ROOT="${ZOOKEEPER_DATA_ROOT:-/root/zookeeper}"
CONTAINER_NAME="${ZOOKEEPER_CONTAINER_NAME:-zookeeper}"
ZOOKEEPER_VERSION="${ZOOKEEPER_VERSION:-3.9}"
ZOOKEEPER_PORT="${ZOOKEEPER_PORT:-2181}"
ZOOKEEPER_PEER_PORT="${ZOOKEEPER_PEER_PORT:-2888}"
ZOOKEEPER_ELECTION_PORT="${ZOOKEEPER_ELECTION_PORT:-3888}"
ZOOKEEPER_ADMIN_PORT="${ZOOKEEPER_ADMIN_PORT:-8181}"
LOG_FILE="${LOG_FILE:-${DATA_ROOT}/install_zookeeper.log}"

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

log_info "=== ZooKeeper Docker 安装开始 ==="
log_info "参数: DATA_ROOT=${DATA_ROOT}, CONTAINER_NAME=${CONTAINER_NAME}, ZOOKEEPER_VERSION=${ZOOKEEPER_VERSION}"
log_info "ZooKeeper 端口: ${ZOOKEEPER_PORT}, Peer 端口: ${ZOOKEEPER_PEER_PORT}, Election 端口: ${ZOOKEEPER_ELECTION_PORT}"
log_info "管理端口: ${ZOOKEEPER_ADMIN_PORT}, 日志文件: ${LOG_FILE}"

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

if docker ps -a --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
  if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
    log_warn "容器 ${CONTAINER_NAME} 已存在且正在运行，跳过安装。"
  else
    log_warn "容器 ${CONTAINER_NAME} 已存在（已停止），如需重新创建请先删除该容器。"
  fi
  log_info "=== ZooKeeper Docker 安装结束（已存在，无需处理） ==="
  exit 0
fi

# 1. 创建目录
log_info "创建目录结构: ${DATA_ROOT}/{conf,data,log,datalog}"
ensure_dir "${DATA_ROOT}"
ensure_dir "${DATA_ROOT}/conf"
ensure_dir "${DATA_ROOT}/data"
ensure_dir "${DATA_ROOT}/log"
ensure_dir "${DATA_ROOT}/datalog"

# 2. 准备配置文件
CONF_PATH="${DATA_ROOT}/conf/zoo.cfg"
if [[ -f "${CONF_PATH}" ]]; then
  log_warn "检测到已有配置文件 ${CONF_PATH}，将直接复用。"
else
  log_info "写入默认配置到 ${CONF_PATH}"
  cat > "${CONF_PATH}" <<EOF
# ZooKeeper 基础配置
tickTime=2000
initLimit=10
syncLimit=5
maxClientCnxns=60
maxSessionTimeout=60000
minSessionTimeout=4000

# 数据目录配置
dataDir=/data
dataLogDir=/datalog

# 客户端端口
clientPort=${ZOOKEEPER_PORT}
clientPortAddress=0.0.0.0

# 管理服务器配置
admin.enableServer=true
admin.serverPort=${ZOOKEEPER_ADMIN_PORT}
admin.serverAddress=0.0.0.0

# 快照和日志配置
snapCount=100000
snapRetainCount=3
purgeInterval=1

# 预分配配置
preAllocSize=65536
snapSizeLimitInKb=4194304

# 性能调优配置
globalOutstandingLimit=1000
cnxTimeout=5
autopurge.snapRetainCount=3
autopurge.purgeInterval=1

# 安全配置
skipACL=yes

# 集群配置（单节点模式，如需集群请手动修改）
# server.1=localhost:2888:3888

# 日志配置
4lw.commands.whitelist=*
EOF
fi

# 创建 myid 文件（单节点模式）
MYID_PATH="${DATA_ROOT}/data/myid"
if [[ -f "${MYID_PATH}" ]]; then
  log_warn "检测到已有 myid 文件 ${MYID_PATH}，将直接复用。"
else
  log_info "创建 myid 文件 (ID: 1)"
  echo "1" > "${MYID_PATH}"
fi

# 创建自定义启动脚本，绕过ZooKeeper镜像的entrypoint问题
START_SCRIPT="${DATA_ROOT}/start-zookeeper.sh"
log_info "创建自定义启动脚本 ${START_SCRIPT}"
cat > "${START_SCRIPT}" <<'EOF'
#!/bin/bash
set -e

# 强制设置正确的目录权限
mkdir -p /data /datalog /logs
chmod -R 777 /data /datalog /logs

# 验证并修正配置文件（使用临时文件避免挂载文件系统问题）
echo "验证和修正配置文件..."
if [[ -f /conf/zoo.cfg ]]; then
  # 创建临时配置文件，修正端口设置
  cp /conf/zoo.cfg /tmp/zoo.cfg.tmp
  sed -i 's/admin\.serverPort=.*/admin.serverPort=8181/' /tmp/zoo.cfg.tmp
  cp /tmp/zoo.cfg.tmp /conf/zoo.cfg
  rm -f /tmp/zoo.cfg.tmp
  echo "配置文件已更新，管理端口设置为8181"
else
  echo "警告: 配置文件不存在"
fi

# 验证关键配置
if ! grep -q "^dataDir=/data$" /conf/zoo.cfg; then
  echo "警告: dataDir配置不正确，期望 '/data'"
fi
if ! grep -q "^dataLogDir=/datalog$" /conf/zoo.cfg; then
  echo "警告: dataLogDir配置不正确，期望 '/datalog'"
fi
if ! grep -q "admin\.serverPort=8181" /conf/zoo.cfg; then
  echo "警告: admin.serverPort配置不正确，期望 '8181'"
fi

# 创建myid文件
if [[ ! -f /data/myid ]]; then
  echo "1" > /data/myid
fi

# 设置环境变量强制使用正确的路径
export ZOO_DATA_DIR=/data
export ZOO_DATA_LOG_DIR=/datalog
export ZOO_LOG_DIR=/logs
export ZOOCFGDIR=/conf
export ZOOCFG=zoo.cfg
export ZOO_ADMIN_PORT=8181

echo "启动ZooKeeper..."
exec /docker-entrypoint.sh zkServer.sh start-foreground
EOF

chmod +x "${START_SCRIPT}"

log_info "设置目录与配置文件权限"
chmod 777 "${DATA_ROOT}/data" "${DATA_ROOT}/log" "${DATA_ROOT}/datalog"
chmod 644 "${CONF_PATH}"
chmod 644 "${MYID_PATH}"

# 3. 检查并拉取镜像（若本地无镜像）
log_info "检查并拉取镜像 zookeeper:${ZOOKEEPER_VERSION}"
if ! docker image inspect "zookeeper:${ZOOKEEPER_VERSION}" >/dev/null 2>&1; then
  log_info "本地不存在镜像 zookeeper:${ZOOKEEPER_VERSION}，开始拉取..."
  if docker pull "zookeeper:${ZOOKEEPER_VERSION}"; then
    log_info "镜像 zookeeper:${ZOOKEEPER_VERSION} 拉取成功"
  else
    log_error "镜像拉取失败"
    exit 1
  fi
else
  log_info "镜像 zookeeper:${ZOOKEEPER_VERSION} 已存在，跳过拉取"
fi

# 4. 启动容器
log_info "启动容器 ${CONTAINER_NAME}"

# 检查安全机制并设置相应的挂载选项
VOLUME_OPTS=""
if command -v getenforce >/dev/null 2>&1 && [[ "$(getenforce)" == "Enforcing" ]]; then
  log_warn "检测到SELinux已启用，为卷挂载添加 :Z 参数"
  VOLUME_OPTS=":Z"
elif command -v apparmor_status >/dev/null 2>&1; then
  log_warn "检测到AppArmor可能启用，尝试使用特权模式"
fi

# 额外的权限检查和设置
log_info "检查并设置SELinux上下文（如果需要）"
if command -v chcon >/dev/null 2>&1; then
  chcon -Rt svirt_sandbox_file_t "${DATA_ROOT}/data" "${DATA_ROOT}/datalog" "${DATA_ROOT}/log" 2>/dev/null || {
    log_warn "无法设置SELinux上下文，继续..."
  }
fi

# 确保父目录也有适当权限
log_info "设置父目录权限"
chmod 755 "${DATA_ROOT}"
chown root:root "${DATA_ROOT}" 2>/dev/null || log_warn "无法更改父目录所有者"

log_info "启动容器前进行最终权限检查..."
# 在容器启动前验证挂载目录权限
for dir in "${DATA_ROOT}/data" "${DATA_ROOT}/datalog" "${DATA_ROOT}/log"; do
  if [[ ! -w "$dir" ]]; then
    log_error "目录 $dir 没有写权限，尝试修复..."
    chmod -R 777 "$dir" || log_error "无法修复权限"
  fi
done

CONTAINER_ID=$(docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart=always \
  --privileged=true \
  --user root \
  --security-opt label:disable \
  --entrypoint=/start-zookeeper.sh \
  -p "${ZOOKEEPER_PORT}:${ZOOKEEPER_PORT}" \
  -p "${ZOOKEEPER_PEER_PORT}:${ZOOKEEPER_PEER_PORT}" \
  -p "${ZOOKEEPER_ELECTION_PORT}:${ZOOKEEPER_ELECTION_PORT}" \
  -p "${ZOOKEEPER_ADMIN_PORT}:${ZOOKEEPER_ADMIN_PORT}" \
  -e TZ=Asia/Shanghai \
  -e ZOO_LOG4J_PROP=WARN,CONSOLE \
  -v "${START_SCRIPT}":/start-zookeeper.sh \
  -v "${CONF_PATH}":/conf/zoo.cfg \
  -v "${DATA_ROOT}/data":/data${VOLUME_OPTS} \
  -v "${DATA_ROOT}/datalog":/datalog${VOLUME_OPTS} \
  -v "${DATA_ROOT}/log":/logs${VOLUME_OPTS} \
  "zookeeper:${ZOOKEEPER_VERSION}")

log_info "容器已创建，ID: ${CONTAINER_ID}"
log_info "等待容器启动..."
sleep 5

# 目录准备现在由自定义启动脚本处理

# 显示容器启动日志
log_info "容器启动日志："
if docker logs "${CONTAINER_NAME}" 2>&1 | head -20 | while IFS= read -r line; do
  log_info "  $line"
done; then
  log_info "容器日志显示完成"
else
  log_warn "读取容器日志时出现警告，但继续执行"
fi

if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
  log_info "容器 ${CONTAINER_NAME} 启动成功。"
  log_info "ZooKeeper 端口: ${ZOOKEEPER_PORT}"
  log_info "管理界面: http://192.168.3.100:${ZOOKEEPER_ADMIN_PORT}/commands"
else
  log_error "容器未在预期时间内启动，请检查日志：docker logs ${CONTAINER_NAME}"
  exit 1
fi

# 5. 等待 ZooKeeper 服务就绪
log_info "等待 ZooKeeper 服务就绪..."
MAX_RETRIES=30
RETRY_COUNT=0
ZOOKEEPER_READY=false

while [[ $RETRY_COUNT -lt $MAX_RETRIES ]]; do
  if docker exec "${CONTAINER_NAME}" nc -z localhost "${ZOOKEEPER_PORT}" 2>/dev/null; then
    # 尝试连接 ZooKeeper 并发送 ruok 命令
    if docker exec "${CONTAINER_NAME}" bash -c "echo 'ruok' | nc localhost ${ZOOKEEPER_PORT}" 2>/dev/null | grep -q "imok"; then
      ZOOKEEPER_READY=true
      log_info "ZooKeeper 服务已就绪"
      break
    fi
  fi
  RETRY_COUNT=$((RETRY_COUNT + 1))
  if [[ $((RETRY_COUNT % 5)) -eq 0 ]]; then
    log_info "等待 ZooKeeper 服务启动中... (${RETRY_COUNT}/${MAX_RETRIES})"
  fi
  sleep 2
done

if [[ "${ZOOKEEPER_READY}" != "true" ]]; then
  log_warn "ZooKeeper 服务网络检查未通过，但容器仍在运行中"
  log_warn "这可能是正常的，ZooKeeper 可能仍在初始化中"
  log_warn "请稍后手动检查服务状态："
  log_warn "  docker exec -it ${CONTAINER_NAME} bash -c \"echo 'ruok' | nc localhost ${ZOOKEEPER_PORT}\""
  log_warn "  或使用: docker exec -it ${CONTAINER_NAME} bash -c \"echo 'stat' | nc localhost ${ZOOKEEPER_PORT}\""
  log_warn "  正常响应应为: imok 或包含 Mode: 信息"
else
  # 验证 ZooKeeper 状态
  log_info "验证 ZooKeeper 服务状态..."
  if docker exec "${CONTAINER_NAME}" bash -c "echo 'stat' | nc localhost ${ZOOKEEPER_PORT}" 2>/dev/null | grep -q "Mode:"; then
    log_info "✓ ZooKeeper 服务状态验证成功"
  else
    log_warn "ZooKeeper 服务状态验证失败，但服务可能仍在启动中"
  fi
fi

log_info "=== ZooKeeper Docker 安装完成 ==="
log_info "常用命令："
log_info "  查看状态: docker exec -it ${CONTAINER_NAME} bash -c \"echo 'stat' | nc localhost ${ZOOKEEPER_PORT}\""
log_info "  健康检查: docker exec -it ${CONTAINER_NAME} bash -c \"echo 'ruok' | nc localhost ${ZOOKEEPER_PORT}\""
log_info "  管理界面: http://192.168.3.100:${ZOOKEEPER_ADMIN_PORT}/commands"
log_info "  查看日志: docker logs ${CONTAINER_NAME}"
log_info "  进入容器: docker exec -it ${CONTAINER_NAME} bash"
log_info "  停止服务: docker stop ${CONTAINER_NAME}"
log_info "  启动服务: docker start ${CONTAINER_NAME}"
