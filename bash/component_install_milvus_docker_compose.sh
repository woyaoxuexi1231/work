#!/usr/bin/env bash
# ========================================================================================
# Milvus Docker Compose 安装脚本 (Milvus Docker Compose Installer)
# ========================================================================================
#
# 功能说明:
#   本脚本用于通过 Docker Compose 快速部署 Milvus 向量数据库系统。
#   Milvus 是专为向量相似度搜索和 AI 应用设计的大规模向量数据库，
#   提供高性能的向量检索和分析能力。
#
# 主要特性:
#   ✓ 完整生态: 包含 etcd 和 Milvus，依赖外部 MinIO 服务
#   ✓ 生产就绪: 使用独立的存储和协调服务
#   ✓ 高可用性: 支持数据持久化和自动重启
#   ✓ 易于扩展: 可轻松升级为分布式集群
#   ✓ 性能优化: 默认配置针对性能和稳定性优化
#
# Milvus 简介:
#   Milvus 是一个开源的向量数据库，具有以下特点：
#   - 向量相似度搜索: 支持多种距离度量算法
#   - 高性能索引: 支持多种向量索引算法 (HNSW、IVF 等)
#   - 水平扩展: 支持分布式部署和分片
#   - 丰富的数据类型: 支持多种向量和标量数据类型
#   - 实时更新: 支持实时数据插入和更新
#   - 元数据管理: 完整的集合和分区管理
#
# 系统架构:
#   - Milvus: 核心向量数据库服务
#   - etcd: 分布式键值存储，用于元数据管理
#   - MinIO: 外部对象存储服务，用于向量数据存储
#
# 主要功能:
#   - 向量 CRUD: 创建、读取、更新、删除向量数据
#   - 相似度搜索: 支持多种相似度度量 (L2、IP、Cosine 等)
#   - 过滤搜索: 支持标量字段过滤和混合搜索
#   - 批量操作: 支持批量向量插入和搜索
#   - 数据分区: 支持集合分区提高查询性能
#   - 索引管理: 支持多种索引类型和参数调优
#
# 配置参数:
#   MILVUS_VERSION      - Milvus 版本 (默认: v2.6.7)
#   MILVUS_PORT         - Milvus 服务端口 (默认: 19530)
#   ETCD_PORT           - etcd 服务端口 (默认: 2379)
#   MINIO_PORT          - MinIO API 端口 (默认: 9000)
#   MINIO_CONSOLE_PORT  - MinIO 控制台端口 (默认: 9001)
#   MINIO_ROOT_USER     - MinIO 访问密钥 (默认: minioadmin)
#   MINIO_ROOT_PASSWORD - MinIO 秘密密钥 (默认: minioadmin)
#
# 端口说明:
#   19530 - Milvus 主服务端口
#   2379  - etcd 客户端端口
#   9091  - etcd 指标端口
#   9000  - MinIO API 端口
#   9001  - MinIO 控制台端口
#
# 目录结构:
#   /root/milvus-compose/     - 根目录
#     ├── milvus/            - Milvus 数据目录
#     ├── etcd/              - etcd 数据目录
#     ├── minio/             - MinIO 数据目录
#     └── install_milvus_compose.log - 安装日志
#
# 访问方式:
#   1. Milvus SDK: 连接到 192.168.3.100:19530
#   2. MinIO 控制台: http://192.168.3.100:9001
#   3. etcd 客户端: 192.168.3.100:2379
#
# 支持的 SDK:
#   - Python: pymilvus
#   - Go: go-sdk
#   - Java: java-sdk
#   - Node.js: node-sdk
#
# 向量索引类型:
#   - FLAT: 暴力搜索，精确但慢
#   - IVF_FLAT: 倒排文件，平衡性能和准确性
#   - IVF_SQ8: 量化压缩，节省空间
#   - IVF_PQ: 乘积量化，更高的压缩率
#   - HNSW: 图索引，最佳搜索性能
#   - ANNOY: 基于树的索引
#
# 距离度量:
#   - L2: 欧几里得距离
#   - IP: 内积距离
#   - COSINE: 余弦相似度
#   - HAMMING: 汉明距离 (二进制向量)
#   - JACCARD: 杰卡德相似度 (稀疏向量)
#
# 注意事项:
#   ⚠️  单机部署仅适用于开发/测试环境
#   ⚠️  生产环境建议使用分布式集群部署
#   ⚠️  向量数据量大时需要足够的存储空间
#   ⚠️  内存需求较高，建议配置足够的 RAM
#   ⚠️  首次启动可能需要较长时间建立索引
#
# 故障排除:
#   - 查看服务状态: docker-compose ps
#   - 查看日志: docker-compose logs [service_name]
#   - 检查端口占用: netstat -tlnp | grep 19530
#   - Milvus 健康检查: curl http://192.168.3.100:19530/health
#   - etcd 健康检查: docker exec etcd etcdctl endpoint health
#   - MinIO 健康检查: curl http://192.168.3.100:9000/minio/health/live
#
# 性能调优:
#   - 调整 Milvus 内存配置
#   - 选择合适的索引类型和参数
#   - 配置适当的分片数量
#   - 监控系统资源使用情况
#   - 定期优化和重建索引
#
# 系统要求:
#   - Docker 和 Docker Compose 环境正常运行
#   - 足够的内存 (建议至少 8GB)
#   - 足够的磁盘空间用于向量数据存储
#   - 网络端口 19530, 2379, 9000, 9001 未被占用
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
DATA_ROOT="${MILVUS_DATA_ROOT:-/root/milvus-compose}"
COMPOSE_PROJECT_NAME="${MILVUS_COMPOSE_PROJECT_NAME:-milvus}"
MILVUS_VERSION="${MILVUS_VERSION:-v2.6.7}"
MILVUS_PORT="${MILVUS_PORT:-19530}"
ETCD_PORT="${ETCD_PORT:-2379}"
MINIO_PORT="${MINIO_PORT:-9000}"
MINIO_CONSOLE_PORT="${MINIO_CONSOLE_PORT:-9001}"
MINIO_ROOT_USER="${MINIO_ROOT_USER:-minioadmin}"
MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD:-minioadmin}"
DOWNLOAD_COMPOSE_FILE="${DOWNLOAD_COMPOSE_FILE:-no}"
ENABLE_ATTU="${ENABLE_ATTU:-yes}"
ATTU_PORT="${ATTU_PORT:-3000}"
# Attu 版本需要与 Milvus 版本兼容
if [[ "${MILVUS_VERSION}" =~ ^v?2\.(6|7|8|9) ]]; then
  ATTU_VERSION="${ATTU_VERSION:-v2.3.4}"
else
  ATTU_VERSION="${ATTU_VERSION:-v2.3.4}"
fi
LOG_FILE="${LOG_FILE:-${DATA_ROOT}/install_milvus_compose.log}"

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

# 检测 MinIO 服务是否已安装并运行
check_minio_service() {
  local minio_container_name="${MINIO_CONTAINER_NAME:-minio}"

  # 检查容器是否存在且正在运行
  if docker ps --format '{{.Names}}' | grep -qw "${minio_container_name}"; then
    log_info "检测到 MinIO 容器 '${minio_container_name}' 正在运行"

    # 检查 MinIO 服务健康状态
    if docker exec "${minio_container_name}" curl -f "http://localhost:9000/minio/health/live" >/dev/null 2>&1; then
      log_info "✓ MinIO 服务健康检查通过"
      return 0
    else
      log_warn "MinIO 容器运行中但健康检查失败，将尝试重新启动"
      docker restart "${minio_container_name}" >/dev/null 2>&1
      sleep 5
      if docker exec "${minio_container_name}" curl -f "http://localhost:9000/minio/health/live" >/dev/null 2>&1; then
        log_info "✓ MinIO 服务重新启动后健康检查通过"
        return 0
      else
        log_error "MinIO 服务健康检查失败，请手动检查"
        return 1
      fi
    fi
  else
    log_warn "未检测到运行中的 MinIO 服务，将自动安装..."
    return 1
  fi
}

# 安装 MinIO 服务
install_minio_service() {
  local minio_install_script="${SCRIPT_DIR}/component-base/component_install_minio_docker.sh"

  if [[ ! -f "${minio_install_script}" ]]; then
    log_error "MinIO 安装脚本不存在: ${minio_install_script}"
    exit 1
  fi

  log_info "开始安装 MinIO 服务..."
  log_info "执行脚本: ${minio_install_script}"

  # 设置 MinIO 安装参数，与当前脚本保持一致
  export MINIO_PORT_API="${MINIO_PORT}"
  export MINIO_PORT_CONSOLE="${MINIO_CONSOLE_PORT}"
  export MINIO_ACCESS_KEY="${MINIO_ROOT_USER}"
  export MINIO_SECRET_KEY="${MINIO_ROOT_PASSWORD}"

  # 执行 MinIO 安装脚本
  if bash "${minio_install_script}"; then
    log_info "✓ MinIO 服务安装成功"
    return 0
  else
    log_error "MinIO 服务安装失败"
    exit 1
  fi
}

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

# 解析命令行参数（在日志函数定义之后）
while [[ $# -gt 0 ]]; do
  case $1 in
    --download|-d)
      DOWNLOAD_COMPOSE_FILE="yes"
      shift
      ;;
    --no-download)
      DOWNLOAD_COMPOSE_FILE="no"
      shift
      ;;
    *)
      log_warn "未知参数: $1，将被忽略"
      shift
      ;;
  esac
done

trap 'log_error "安装过程中出现错误，退出。"' ERR

log_info "=== Milvus Standalone Docker Compose 安装开始 ==="
log_info "方案: Docker Compose (独立 etcd、MinIO、Milvus 服务)"
log_info "参数: DATA_ROOT=${DATA_ROOT}, COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME}, MILVUS_VERSION=${MILVUS_VERSION}"
log_info "端口: Milvus=${MILVUS_PORT}, etcd=${ETCD_PORT}, MinIO=${MINIO_PORT}, MinIO Console=${MINIO_CONSOLE_PORT}, Attu=${ATTU_PORT}"
log_info "MinIO 用户名: ${MINIO_ROOT_USER}"
log_info "配置文件: ${DOWNLOAD_COMPOSE_FILE} (no=使用内置配置, yes=下载官方配置)"
log_info "Attu 管理界面: 默认启用（端口 ${ATTU_PORT}）"
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

# 检查 docker-compose 是否已安装
if ! command -v docker-compose >/dev/null 2>&1 && ! docker compose version >/dev/null 2>&1; then
  log_error "未检测到 docker-compose，请先安装 docker-compose"
  log_error "安装方法："
  log_error "  apt-get update && apt-get install -y docker-compose"
  log_error "或者使用 Docker Compose V2: apt-get install -y docker-compose-plugin"
  exit 1
fi

# 确定使用的 docker-compose 命令
if docker compose version >/dev/null 2>&1; then
  DOCKER_COMPOSE_CMD="docker compose"
  log_info "使用 Docker Compose V2"
else
  DOCKER_COMPOSE_CMD="docker-compose"
  log_info "使用 Docker Compose V1"
fi

# 检查并安装依赖服务
log_info "检查依赖服务..."

# 检查 MinIO 服务
if ! check_minio_service; then
  install_minio_service
fi

# 检查容器是否已存在
COMPOSE_FILE="${DATA_ROOT}/docker-compose.yml"
if [[ -f "${COMPOSE_FILE}" ]]; then
  cd "${DATA_ROOT}"
  if ${DOCKER_COMPOSE_CMD} ps --services --filter "status=running" 2>/dev/null | grep -q "etcd\|standalone"; then
    log_warn "Milvus 相关容器已存在且正在运行，跳过安装。"
    log_info "如需重新安装，请先停止并删除容器："
    log_info "  cd ${DATA_ROOT} && ${DOCKER_COMPOSE_CMD} down"
    log_info "=== Milvus Standalone Docker Compose 安装结束（已存在，无需处理） ==="
    exit 0
  fi
fi

# 1. 创建目录结构
log_info "创建目录结构: ${DATA_ROOT}/volumes/{milvus,etcd}"
ensure_dir "${DATA_ROOT}"
ensure_dir "${DATA_ROOT}/volumes"
ensure_dir "${DATA_ROOT}/volumes/milvus"
ensure_dir "${DATA_ROOT}/volumes/etcd"

# 2. 创建或下载 docker-compose.yml 文件
log_info "准备 docker-compose.yml 配置文件"

# 尝试从 GitHub 下载官方配置文件（仅在明确指定时）
if [[ "${DOWNLOAD_COMPOSE_FILE}" == "yes" ]]; then
  log_info "尝试从 GitHub 下载官方 docker-compose.yml..."
  cd "${DATA_ROOT}"
  
  # 尝试下载官方配置文件
  if command -v wget >/dev/null 2>&1; then
    if wget -q "https://github.com/milvus-io/milvus/releases/download/${MILVUS_VERSION}/milvus-standalone-docker-compose.yml" -O docker-compose.yml.tmp 2>/dev/null; then
      mv docker-compose.yml.tmp docker-compose.yml
      log_info "✓ 成功从 GitHub 下载官方 docker-compose.yml"
      DOWNLOADED=true
    else
      log_warn "从 GitHub 下载失败，将使用内置配置"
      DOWNLOADED=false
    fi
  elif command -v curl >/dev/null 2>&1; then
    if curl -sSLf "https://github.com/milvus-io/milvus/releases/download/${MILVUS_VERSION}/milvus-standalone-docker-compose.yml" -o docker-compose.yml.tmp 2>/dev/null; then
      mv docker-compose.yml.tmp docker-compose.yml
      log_info "✓ 成功从 GitHub 下载官方 docker-compose.yml"
      DOWNLOADED=true
    else
      log_warn "从 GitHub 下载失败，将使用内置配置"
      DOWNLOADED=false
    fi
  else
    log_warn "未找到 wget 或 curl，将使用内置配置"
    DOWNLOADED=false
  fi
else
  DOWNLOADED=false
fi

# 如果下载失败或未启用下载，使用内置配置
if [[ "${DOWNLOADED}" != "true" ]]; then
  if [[ "${DOWNLOAD_COMPOSE_FILE}" == "yes" ]]; then
    log_warn "下载失败，将使用内置配置"
  else
    log_info "使用内置 docker-compose.yml 配置文件（默认）"
  fi
  log_info "创建内置 docker-compose.yml 配置文件"
  cat > "${COMPOSE_FILE}" <<EOF
version: '3.5'

services:
  etcd:
    container_name: ${COMPOSE_PROJECT_NAME}-etcd
    image: quay.io/coreos/etcd:v3.5.25
    restart: unless-stopped
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
      - ETCD_SNAPSHOT_COUNT=50000
    volumes:
      - ${DATA_ROOT}/volumes/etcd:/etcd
    command: etcd -advertise-client-urls=http://etcd:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd
    healthcheck:
      test: ["CMD", "etcdctl", "endpoint", "health"]
      interval: 30s
      timeout: 20s
      retries: 3

  standalone:
    container_name: ${COMPOSE_PROJECT_NAME}-standalone
    image: milvusdb/milvus:${MILVUS_VERSION}
    restart: unless-stopped
    command: ["milvus", "run", "standalone"]
    security_opt:
    - seccomp:unconfined
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: 192.168.3.100:9000
      MQ_TYPE: woodpecker
    volumes:
      - ${DATA_ROOT}/volumes/milvus:/var/lib/milvus
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9091/healthz"]
      interval: 30s
      start_period: 90s
      timeout: 20s
      retries: 3
    ports:
      - "${MILVUS_PORT}:19530"
      - "9091:9091"
    depends_on:
      - "etcd"

  attu:
    container_name: ${COMPOSE_PROJECT_NAME}-attu
    image: zilliz/attu:${ATTU_VERSION}
    restart: unless-stopped
    environment:
      MILVUS_URL: standalone:19530
    ports:
      - "${ATTU_PORT}:3000"
    depends_on:
      - "standalone"

networks:
  default:
    name: ${COMPOSE_PROJECT_NAME}
EOF
  log_info "内置 docker-compose.yml 已创建"
fi

log_info "docker-compose.yml 位置: ${COMPOSE_FILE}"

# 3. 设置目录权限
log_info "设置目录权限"
chmod 755 "${DATA_ROOT}"
chmod 755 "${DATA_ROOT}/volumes"
chmod 755 "${DATA_ROOT}/volumes/milvus"
chmod 755 "${DATA_ROOT}/volumes/etcd"
chmod 644 "${COMPOSE_FILE}"

# 4. 检查并拉取镜像
log_info "检查并拉取 Docker 镜像..."
cd "${DATA_ROOT}"

log_info "检查并拉取 etcd 镜像..."
if ! docker image inspect "quay.io/coreos/etcd:v3.5.25" >/dev/null 2>&1; then
  log_info "本地不存在镜像 quay.io/coreos/etcd:v3.5.25，开始拉取..."
  docker pull quay.io/coreos/etcd:v3.5.25 || {
    log_error "etcd 镜像拉取失败"
    exit 1
  }
else
  log_info "镜像 quay.io/coreos/etcd:v3.5.25 已存在，跳过拉取"
fi

log_info "检查并拉取 Milvus 镜像..."
if ! docker image inspect "milvusdb/milvus:${MILVUS_VERSION}" >/dev/null 2>&1; then
  log_info "本地不存在镜像 milvusdb/milvus:${MILVUS_VERSION}，开始拉取..."
  docker pull "milvusdb/milvus:${MILVUS_VERSION}" || {
    log_error "Milvus 镜像拉取失败"
    exit 1
  }
else
  log_info "镜像 milvusdb/milvus:${MILVUS_VERSION} 已存在，跳过拉取"
fi

log_info "检查并拉取 Attu 镜像..."
if ! docker image inspect "zilliz/attu:${ATTU_VERSION}" >/dev/null 2>&1; then
  log_info "本地不存在镜像 zilliz/attu:${ATTU_VERSION}，开始拉取..."
  docker pull "zilliz/attu:${ATTU_VERSION}" || {
    log_error "Attu 镜像拉取失败"
    exit 1
  }
else
  log_info "镜像 zilliz/attu:${ATTU_VERSION} 已存在，跳过拉取"
fi

log_info "所有镜像检查/拉取完成"

# 5. 启动服务
log_info "启动 Milvus 服务..."
cd "${DATA_ROOT}"

if ${DOCKER_COMPOSE_CMD} up -d; then
  log_info "容器启动命令执行成功"
else
  log_error "容器启动失败"
  exit 1
fi

log_info "等待服务启动..."
sleep 10

# 显示容器状态
log_info "容器状态："
${DOCKER_COMPOSE_CMD} ps

# 6. 等待服务就绪
log_info "等待 Milvus 服务就绪..."
MAX_RETRIES=60
RETRY_COUNT=0
MILVUS_READY=false

while [[ $RETRY_COUNT -lt $MAX_RETRIES ]]; do
  if docker exec "${COMPOSE_PROJECT_NAME}-standalone" curl -f http://localhost:9091/healthz >/dev/null 2>&1; then
    MILVUS_READY=true
    log_info "Milvus 服务已就绪"
    break
  fi
  RETRY_COUNT=$((RETRY_COUNT + 1))
  if [[ $((RETRY_COUNT % 10)) -eq 0 ]]; then
    log_info "等待 Milvus 服务启动中... (${RETRY_COUNT}/${MAX_RETRIES})"
  fi
  sleep 2
done

if [[ "${MILVUS_READY}" != "true" ]]; then
  log_warn "Milvus 服务在预期时间内未就绪，但容器已启动"
  log_warn "请检查日志：docker logs ${COMPOSE_PROJECT_NAME}-standalone"
else
  log_info "✓ Milvus 服务启动成功"
fi

# 检查其他服务
log_info "检查 etcd 服务..."
if docker exec "${COMPOSE_PROJECT_NAME}-etcd" etcdctl endpoint health >/dev/null 2>&1; then
  log_info "✓ etcd 服务正常"
else
  log_warn "etcd 服务可能未就绪，请检查：docker logs ${COMPOSE_PROJECT_NAME}-etcd"
fi

log_info "=== Milvus Standalone Docker Compose 安装完成 ==="
log_info ""
log_info "使用说明："
log_info "1. 服务访问："
log_info "   Milvus API: 192.168.3.100:${MILVUS_PORT}"
log_info "   Milvus 健康检查: http://192.168.3.100:9091/healthz"
log_info "   Milvus 指标端点: http://192.168.3.100:9091/metrics (Prometheus)"
log_info "   Milvus 内置 WebUI: http://192.168.3.100:9091/webui/"
log_info "   Attu WebUI (推荐): http://192.168.3.100:${ATTU_PORT}"
log_info "   MinIO Console: http://192.168.3.100:${MINIO_CONSOLE_PORT}"
log_info ""
log_info "   注意："
log_info "   - Milvus 内置 WebUI 访问路径为 /webui/ (不是根路径)"
log_info "   - 部署 Attu 后，Milvus 内置 WebUI 可能不可用（Milvus 2.6+ 策略）"
log_info "   - 推荐使用 Attu 进行管理: http://192.168.3.100:${ATTU_PORT}"
log_info ""
log_info "2. MinIO 访问凭证："
log_info "   用户名: ${MINIO_ROOT_USER}"
log_info "   密码: ${MINIO_ROOT_PASSWORD}"
log_info ""
log_info "3. 数据目录："
log_info "   Milvus: ${DATA_ROOT}/volumes/milvus"
log_info "   etcd: ${DATA_ROOT}/volumes/etcd"
log_info "   MinIO: /root/minio/data (外部服务)"
log_info ""
log_info "4. 查看日志："
log_info "   Milvus: docker logs ${COMPOSE_PROJECT_NAME}-standalone"
log_info "   etcd: docker logs ${COMPOSE_PROJECT_NAME}-etcd"
log_info "   Attu: docker logs ${COMPOSE_PROJECT_NAME}-attu"
log_info "   MinIO: docker logs minio"
log_info "   所有服务: cd ${DATA_ROOT} && ${DOCKER_COMPOSE_CMD} logs -f"
log_info ""
log_info "5. 停止/启动服务："
log_info "   停止: cd ${DATA_ROOT} && ${DOCKER_COMPOSE_CMD} down"
log_info "   启动: cd ${DATA_ROOT} && ${DOCKER_COMPOSE_CMD} up -d"
log_info "   重启: cd ${DATA_ROOT} && ${DOCKER_COMPOSE_CMD} restart"
log_info "   查看状态: cd ${DATA_ROOT} && ${DOCKER_COMPOSE_CMD} ps"
log_info ""
log_info "6. 配置文件："
log_info "   ${COMPOSE_FILE}"
log_info ""
log_info "7. 连接 Milvus（Python 示例）："
log_info "   from pymilvus import connections"
log_info "   connections.connect("
log_info "     alias=\"default\","
log_info "     host=\"192.168.3.100\","
log_info "     port=\"${MILVUS_PORT}\""
log_info "   )"
log_info ""
log_info "注意：此方案使用独立的 etcd 和 Milvus 服务，依赖外部 MinIO 对象存储服务，适合生产环境部署。"
log_info ""
log_info "配置说明："
log_info "- 默认使用脚本内置的 docker-compose.yml 配置"
log_info "- 使用官方推荐的组件版本：etcd v3.5.25, Milvus v2.6.7"
log_info "- MinIO 使用外部独立服务，地址为 host.docker.internal:9000"
log_info "- etcd advertise-client-urls 使用服务名 (etcd:2379)"
log_info "- 添加了 MQ_TYPE: woodpecker 和 security_opt 配置（Milvus 2.6+ 必需）"
log_info "- 网络配置使用 default 网络并重命名为项目名称"
log_info "- Milvus 9091 端口提供：/healthz (健康检查), /metrics (Prometheus), /webui/ (内置 WebUI)"
log_info "- Milvus 2.6+ 策略：部署 Attu 后，内置 WebUI 可能被禁用（推荐使用 Attu）"
log_info "- Web 管理界面：Attu（端口 ${ATTU_PORT}）或 Milvus 内置 WebUI（端口 9091，路径 /webui/）"
log_info "- 配置结构与官方 milvus-standalone-docker-compose.yml 保持一致"
log_info "- 所有服务配置了 restart: unless-stopped，支持开机自动重启"
log_info ""
log_info "使用官方配置文件："
log_info "  如需使用官方配置文件，请使用以下命令："
log_info "  bash install_milvus_docker_compose.sh --download"
log_info "  或设置环境变量："
log_info "  DOWNLOAD_COMPOSE_FILE=yes bash install_milvus_docker_compose.sh"

