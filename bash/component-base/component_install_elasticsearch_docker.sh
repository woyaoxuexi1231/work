#!/usr/bin/env bash
# ========================================================================================
# Elasticsearch Docker 安装脚本 (Elasticsearch Docker Installer)
# ========================================================================================
#
# 功能说明:
#   本脚本用于通过 Docker 快速部署 Elasticsearch 分布式全文搜索引擎。
#   Elasticsearch 是一个基于 Lucene 的分布式搜索和分析引擎，具有高性能和易扩展性。
#
# 主要特性:
#   ✓ 全文搜索: 强大的全文搜索和分析能力
#   ✓ 分布式: 支持横向扩展和数据分片
#   ✓ REST API: 基于 HTTP 的完整 REST API
#   ✓ 实时性: 近实时的数据索引和搜索
#   ✓ 多租户: 支持多个索引和类型
#   ✓ 可视化: 与 Kibana 完美集成
#
# 核心概念:
#   - 索引 (Index): 数据存储的逻辑容器
#   - 类型 (Type): 索引内的数据分类 (7.0+ 已弃用)
#   - 文档 (Document): JSON 格式的数据记录
#   - 分片 (Shard): 数据分布的基本单位
#   - 副本 (Replica): 数据备份和故障转移
#
# 配置参数:
#   ELASTICSEARCH_PORT       - HTTP 端口 (默认: 9200)
#   ELASTICSEARCH_TRANSPORT_PORT - 集群通信端口 (默认: 9300)
#   ES_JAVA_OPTS             - JVM 参数 (默认: -Xms512m -Xmx512m)
#
# 端口说明:
#   9200 - HTTP API 端口 (主要使用端口)
#   9300 - 集群节点间通信端口
#
# 访问示例:
#   集群健康: curl http://localhost:9200/_cluster/health?pretty
#   节点信息: curl http://localhost:9200/_nodes
#   索引列表: curl http://localhost:9200/_cat/indices
#
# 主要功能:
#   - 全文搜索和分析
#   - 结构化搜索
#   - 统计和聚合
#   - 地理空间搜索
#   - 实时数据分析
#
# 注意: 单节点部署，生产环境建议集群架构
# 作者: 系统运维脚本 | 版本: v1.0 | 更新时间: 2024-01
# ========================================================================================

# Ensure we are running under bash (Ubuntu /bin/sh 不支持 pipefail)
if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_ROOT="${ELASTICSEARCH_DATA_ROOT:-/root/elasticsearch}"
CONTAINER_NAME="${ELASTICSEARCH_CONTAINER_NAME:-elasticsearch}"
ELASTICSEARCH_VERSION="${ELASTICSEARCH_VERSION:-8.11.0}"
ELASTICSEARCH_PORT="${ELASTICSEARCH_PORT:-9200}"
ELASTICSEARCH_TRANSPORT_PORT="${ELASTICSEARCH_TRANSPORT_PORT:-9300}"
ELASTICSEARCH_PASSWORD="${ELASTICSEARCH_PASSWORD:-}"
ELASTICSEARCH_USER="${ELASTICSEARCH_USER:-elastic}"
ES_JAVA_OPTS="${ES_JAVA_OPTS:--Xms512m -Xmx512m}"
LOG_FILE="${LOG_FILE:-${DATA_ROOT}/install_elasticsearch.log}"

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

log_info "=== Elasticsearch Docker 安装开始 ==="
log_info "参数: DATA_ROOT=${DATA_ROOT}, CONTAINER_NAME=${CONTAINER_NAME}, ELASTICSEARCH_VERSION=${ELASTICSEARCH_VERSION}"
log_info "端口: HTTP=${ELASTICSEARCH_PORT}, Transport=${ELASTICSEARCH_TRANSPORT_PORT}"
log_info "Java 选项: ${ES_JAVA_OPTS}"
if [[ -n "${ELASTICSEARCH_PASSWORD}" ]]; then
  log_info "Elasticsearch 密码已设置（用户: ${ELASTICSEARCH_USER}，长度: ${#ELASTICSEARCH_PASSWORD}）"
else
  log_info "Elasticsearch 密码未设置（将使用默认密码或禁用安全功能）"
fi
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

if docker ps -a --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
  if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
    log_warn "容器 ${CONTAINER_NAME} 已存在且正在运行，跳过安装。"
  else
    log_warn "容器 ${CONTAINER_NAME} 已存在（已停止），如需重新创建请先删除该容器。"
  fi
  log_info "=== Elasticsearch Docker 安装结束（已存在，无需处理） ==="
  exit 0
fi

# 检查并设置系统参数 vm.max_map_count（Elasticsearch 需要）
log_info "检查系统参数 vm.max_map_count..."
CURRENT_VM_MAX_MAP_COUNT=$(sysctl -n vm.max_map_count 2>/dev/null || echo "0")
REQUIRED_VM_MAX_MAP_COUNT=262144

if [[ "${CURRENT_VM_MAX_MAP_COUNT}" -lt "${REQUIRED_VM_MAX_MAP_COUNT}" ]]; then
  log_warn "vm.max_map_count 当前值: ${CURRENT_VM_MAX_MAP_COUNT}，需要至少 ${REQUIRED_VM_MAX_MAP_COUNT}"
  log_info "设置 vm.max_map_count=${REQUIRED_VM_MAX_MAP_COUNT}..."
  sysctl -w vm.max_map_count=${REQUIRED_VM_MAX_MAP_COUNT} || {
    log_error "无法设置 vm.max_map_count，请手动执行：sysctl -w vm.max_map_count=${REQUIRED_VM_MAX_MAP_COUNT}"
    exit 1
  }
  log_info "已临时设置 vm.max_map_count=${REQUIRED_VM_MAX_MAP_COUNT}"
  log_warn "注意：此设置重启后会失效，建议永久设置："
  log_warn "  echo 'vm.max_map_count=${REQUIRED_VM_MAX_MAP_COUNT}' >> /etc/sysctl.conf"
  log_warn "  sysctl -p"
else
  log_info "vm.max_map_count 已满足要求: ${CURRENT_VM_MAX_MAP_COUNT}"
fi

# 1. 创建目录
log_info "创建目录结构: ${DATA_ROOT}/{conf,data,log,plugins}"
ensure_dir "${DATA_ROOT}"
ensure_dir "${DATA_ROOT}/conf"
ensure_dir "${DATA_ROOT}/data"
ensure_dir "${DATA_ROOT}/log"
ensure_dir "${DATA_ROOT}/plugins"

# 2. 准备配置文件
CONF_PATH="${DATA_ROOT}/conf/elasticsearch.yml"
if [[ -f "${CONF_PATH}" ]]; then
  log_warn "检测到已有配置文件 ${CONF_PATH}，将更新相关设置。"
  # 备份原配置
  cp "${CONF_PATH}" "${CONF_PATH}.backup.$(date +%Y%m%d_%H%M%S)" 2>/dev/null || true
else
  log_info "写入默认配置到 ${CONF_PATH}"
  cat > "${CONF_PATH}" <<EOF
# Elasticsearch 配置文件

# 集群配置
cluster.name: docker-cluster
node.name: node-1
node.roles: [ master, data, ingest ]

# 网络配置
network.host: 0.0.0.0
http.port: 9200
transport.port: 9300

# 路径配置
path.data: /usr/share/elasticsearch/data
path.logs: /usr/share/elasticsearch/logs

# 发现配置（单节点模式）
discovery.type: single-node

# 安全配置（Elasticsearch 8.x 默认启用安全功能）
# 如果设置了密码，将通过环境变量传递
# xpack.security.enabled: true
# xpack.security.transport.ssl.enabled: true
# xpack.security.http.ssl.enabled: false

# 其他配置
action.auto_create_index: true
EOF
fi

# 设置目录权限（Elasticsearch 容器内用户 UID 1000 需要访问权限）
log_info "设置目录与配置文件权限"
chmod 777 "${DATA_ROOT}/data"
chmod 777 "${DATA_ROOT}/log"
chmod 755 "${DATA_ROOT}/plugins"
chmod 644 "${CONF_PATH}"

# 3. 检查并拉取镜像（若本地无镜像）
log_info "检查并拉取镜像 elasticsearch:${ELASTICSEARCH_VERSION}"
if ! docker image inspect "elasticsearch:${ELASTICSEARCH_VERSION}" >/dev/null 2>&1; then
  log_info "本地不存在镜像 elasticsearch:${ELASTICSEARCH_VERSION}，开始拉取..."
  if docker pull "elasticsearch:${ELASTICSEARCH_VERSION}"; then
    log_info "镜像 elasticsearch:${ELASTICSEARCH_VERSION} 拉取成功"
  else
    log_error "镜像拉取失败"
    exit 1
  fi
else
  log_info "镜像 elasticsearch:${ELASTICSEARCH_VERSION} 已存在，跳过拉取"
fi

# 4. 启动容器
log_info "启动容器 ${CONTAINER_NAME}"

# 构建 docker run 命令
DOCKER_RUN_CMD=(
  docker run -d
  --name "${CONTAINER_NAME}"
  --restart=always
  --privileged=true
  -p "${ELASTICSEARCH_PORT}:9200"
  -p "${ELASTICSEARCH_TRANSPORT_PORT}:9300"
  -e "discovery.type=single-node"
  -e "ES_JAVA_OPTS=${ES_JAVA_OPTS}"
  -e TZ=Asia/Shanghai
  -v "${CONF_PATH}:/usr/share/elasticsearch/config/elasticsearch.yml"
  -v "${DATA_ROOT}/data:/usr/share/elasticsearch/data"
  -v "${DATA_ROOT}/log:/usr/share/elasticsearch/logs"
  -v "${DATA_ROOT}/plugins:/usr/share/elasticsearch/plugins"
)

# 如果设置了密码，通过环境变量传递
if [[ -n "${ELASTICSEARCH_PASSWORD}" ]]; then
  DOCKER_RUN_CMD+=(
    -e "ELASTIC_PASSWORD=${ELASTICSEARCH_PASSWORD}"
    -e "xpack.security.enabled=true"
  )
  log_info "已启用 Elasticsearch 安全功能，密码将通过环境变量设置"
else
  # Elasticsearch 8.x 默认启用安全功能，如果没有设置密码，禁用安全功能
  DOCKER_RUN_CMD+=(
    -e "xpack.security.enabled=false"
  )
  log_info "已禁用 Elasticsearch 安全功能（无密码模式）"
fi

DOCKER_RUN_CMD+=("elasticsearch:${ELASTICSEARCH_VERSION}")

CONTAINER_ID=$("${DOCKER_RUN_CMD[@]}")

log_info "容器已创建，ID: ${CONTAINER_ID}"
log_info "等待容器启动..."
sleep 5

# 显示容器启动日志
log_info "容器启动日志："
docker logs "${CONTAINER_NAME}" 2>&1 | head -30 | while IFS= read -r line || [ -n "$line" ]; do
  log_info "  $line"
done

if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
  log_info "容器 ${CONTAINER_NAME} 启动成功。"
  log_info "端口映射: HTTP ${ELASTICSEARCH_PORT} -> 9200, Transport ${ELASTICSEARCH_TRANSPORT_PORT} -> 9300"
  if [[ -n "${ELASTICSEARCH_PASSWORD}" ]]; then
    log_info "Elasticsearch 用户: ${ELASTICSEARCH_USER}"
    log_info "Elasticsearch 密码: ${ELASTICSEARCH_PASSWORD}"
  else
    log_info "Elasticsearch 密码: 未设置（安全功能已禁用）"
  fi
else
  log_error "容器未在预期时间内启动，请检查日志：docker logs ${CONTAINER_NAME}"
  exit 1
fi

# 5. 等待 Elasticsearch 服务就绪并测试连接
log_info "等待 Elasticsearch 服务就绪..."
MAX_RETRIES=60
RETRY_COUNT=0
ES_READY=false

while [[ $RETRY_COUNT -lt $MAX_RETRIES ]]; do
  # 检查容器是否还在运行
  if ! docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
    log_error "容器 ${CONTAINER_NAME} 已停止运行"
    log_error "请检查日志：docker logs ${CONTAINER_NAME}"
    exit 1
  fi
  
  # 检查 Elasticsearch 是否就绪
  if [[ -n "${ELASTICSEARCH_PASSWORD}" ]]; then
    # 使用密码认证
    if curl -s -u "${ELASTICSEARCH_USER}:${ELASTICSEARCH_PASSWORD}" \
      "http://192.168.3.100:${ELASTICSEARCH_PORT}/_cluster/health" >/dev/null 2>&1; then
      ES_READY=true
      log_info "Elasticsearch 服务已就绪"
      break
    fi
  else
    # 无密码模式
    if curl -s "http://192.168.3.100:${ELASTICSEARCH_PORT}/_cluster/health" >/dev/null 2>&1; then
      ES_READY=true
      log_info "Elasticsearch 服务已就绪"
      break
    fi
  fi
  
  RETRY_COUNT=$((RETRY_COUNT + 1))
  if [[ $((RETRY_COUNT % 10)) -eq 0 ]]; then
    log_info "等待 Elasticsearch 服务启动中... (${RETRY_COUNT}/${MAX_RETRIES})"
  fi
  sleep 2
done

if [[ "${ES_READY}" != "true" ]]; then
  log_warn "Elasticsearch 服务在预期时间内未就绪，但容器已启动"
  log_warn "请手动检查：docker logs ${CONTAINER_NAME}"
  log_warn "Elasticsearch 可能需要更长时间初始化，请稍后手动验证连接"
else
  # 测试 Elasticsearch 连接和基本操作
  log_info "测试 Elasticsearch 连接..."
  
  if [[ -n "${ELASTICSEARCH_PASSWORD}" ]]; then
    # 使用密码认证测试
    CLUSTER_HEALTH=$(curl -s -u "${ELASTICSEARCH_USER}:${ELASTICSEARCH_PASSWORD}" \
      "http://192.168.3.100:${ELASTICSEARCH_PORT}/_cluster/health?pretty" 2>/dev/null || echo "")
    if [[ -n "${CLUSTER_HEALTH}" ]]; then
      log_info "✓ Elasticsearch 连接测试成功"
      # 提取集群状态
      CLUSTER_STATUS=$(echo "${CLUSTER_HEALTH}" | grep -o '"status"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 || echo "")
      if [[ -n "${CLUSTER_STATUS}" ]]; then
        log_info "  集群状态: ${CLUSTER_STATUS}"
      fi
    else
      log_warn "Elasticsearch 连接测试失败，但服务可能正常运行"
    fi
  else
    # 无密码模式测试
    CLUSTER_HEALTH=$(curl -s "http://192.168.3.100:${ELASTICSEARCH_PORT}/_cluster/health?pretty" 2>/dev/null || echo "")
    if [[ -n "${CLUSTER_HEALTH}" ]]; then
      log_info "✓ Elasticsearch 连接测试成功"
      # 提取集群状态
      CLUSTER_STATUS=$(echo "${CLUSTER_HEALTH}" | grep -o '"status"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 || echo "")
      if [[ -n "${CLUSTER_STATUS}" ]]; then
        log_info "  集群状态: ${CLUSTER_STATUS}"
      fi
    else
      log_warn "Elasticsearch 连接测试失败，但服务可能正常运行"
    fi
  fi
  
  # 获取 Elasticsearch 版本信息
  if [[ -n "${ELASTICSEARCH_PASSWORD}" ]]; then
    VERSION_INFO=$(curl -s -u "${ELASTICSEARCH_USER}:${ELASTICSEARCH_PASSWORD}" \
      "http://192.168.3.100:${ELASTICSEARCH_PORT}/" 2>/dev/null | grep -o '"number"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 || echo "")
  else
    VERSION_INFO=$(curl -s "http://192.168.3.100:${ELASTICSEARCH_PORT}/" 2>/dev/null | grep -o '"number"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 || echo "")
  fi
  if [[ -n "${VERSION_INFO}" ]]; then
    log_info "  Elasticsearch 版本: ${VERSION_INFO}"
  fi
fi

log_info "=== Elasticsearch Docker 安装完成 ==="
log_info ""
log_info "使用说明："
log_info "1. 连接 Elasticsearch："
if [[ -n "${ELASTICSEARCH_PASSWORD}" ]]; then
  log_info "   本地连接: curl -u ${ELASTICSEARCH_USER}:${ELASTICSEARCH_PASSWORD} http://192.168.3.100:${ELASTICSEARCH_PORT}/"
  log_info "   容器内连接: docker exec -it ${CONTAINER_NAME} curl -u ${ELASTICSEARCH_USER}:${ELASTICSEARCH_PASSWORD} http://localhost:9200/"
else
  log_info "   本地连接: curl http://192.168.3.100:${ELASTICSEARCH_PORT}/"
  log_info "   容器内连接: docker exec -it ${CONTAINER_NAME} curl http://localhost:9200/"
fi
log_info ""
log_info "2. 连接字符串："
if [[ -n "${ELASTICSEARCH_PASSWORD}" ]]; then
  log_info "   http://${ELASTICSEARCH_USER}:${ELASTICSEARCH_PASSWORD}@192.168.3.100:${ELASTICSEARCH_PORT}"
else
  log_info "   http://192.168.3.100:${ELASTICSEARCH_PORT}"
fi
log_info ""
log_info "3. Spring Boot 配置示例："
if [[ -n "${ELASTICSEARCH_PASSWORD}" ]]; then
  log_info "   spring:"
  log_info "     elasticsearch:"
  log_info "       uris: http://192.168.3.100:${ELASTICSEARCH_PORT}"
  log_info "       username: ${ELASTICSEARCH_USER}"
  log_info "       password: ${ELASTICSEARCH_PASSWORD}"
else
  log_info "   spring:"
  log_info "     elasticsearch:"
  log_info "       uris: http://192.168.3.100:${ELASTICSEARCH_PORT}"
fi
log_info ""
log_info "4. 常用命令："
log_info "   查看日志: docker logs ${CONTAINER_NAME}"
log_info "   查看日志（实时）: docker logs -f ${CONTAINER_NAME}"
log_info "   进入容器: docker exec -it ${CONTAINER_NAME} bash"
log_info "   查看集群健康状态:"
if [[ -n "${ELASTICSEARCH_PASSWORD}" ]]; then
  log_info "     curl -u ${ELASTICSEARCH_USER}:${ELASTICSEARCH_PASSWORD} http://192.168.3.100:${ELASTICSEARCH_PORT}/_cluster/health?pretty"
else
  log_info "     curl http://192.168.3.100:${ELASTICSEARCH_PORT}/_cluster/health?pretty"
fi
log_info "   查看节点信息:"
if [[ -n "${ELASTICSEARCH_PASSWORD}" ]]; then
  log_info "     curl -u ${ELASTICSEARCH_USER}:${ELASTICSEARCH_PASSWORD} http://192.168.3.100:${ELASTICSEARCH_PORT}/_nodes?pretty"
else
  log_info "     curl http://192.168.3.100:${ELASTICSEARCH_PORT}/_nodes?pretty"
fi
log_info "   查看索引列表:"
if [[ -n "${ELASTICSEARCH_PASSWORD}" ]]; then
  log_info "     curl -u ${ELASTICSEARCH_USER}:${ELASTICSEARCH_PASSWORD} http://192.168.3.100:${ELASTICSEARCH_PORT}/_cat/indices?v"
else
  log_info "     curl http://192.168.3.100:${ELASTICSEARCH_PORT}/_cat/indices?v"
fi
log_info ""
log_info "5. 数据目录："
log_info "   ${DATA_ROOT}/data"
log_info ""
log_info "6. 配置文件："
log_info "   ${CONF_PATH}"
log_info ""
log_info "7. 停止/启动容器："
log_info "   docker stop ${CONTAINER_NAME}"
log_info "   docker start ${CONTAINER_NAME}"
log_info ""
log_info "8. 创建索引示例："
if [[ -n "${ELASTICSEARCH_PASSWORD}" ]]; then
  log_info "   curl -X PUT -u ${ELASTICSEARCH_USER}:${ELASTICSEARCH_PASSWORD} http://192.168.3.100:${ELASTICSEARCH_PORT}/my_index"
else
  log_info "   curl -X PUT http://192.168.3.100:${ELASTICSEARCH_PORT}/my_index"
fi
log_info ""
log_info "9. 注意事项："
log_info "   - Elasticsearch 需要 vm.max_map_count >= 262144"
log_info "   - 如果重启后需要永久设置，请执行："
log_info "     echo 'vm.max_map_count=262144' >> /etc/sysctl.conf"
log_info "     sysctl -p"
log_info "   - 默认 Java 堆内存设置为 512MB，可通过 ES_JAVA_OPTS 环境变量调整"
log_info "   - 生产环境建议启用安全功能并设置强密码"

