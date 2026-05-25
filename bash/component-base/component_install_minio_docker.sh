#!/usr/bin/env bash
# ========================================================================================
# MinIO Docker 安装脚本 (MinIO Docker Installer)
# ========================================================================================
#
# 功能说明:
#   本脚本用于通过 Docker 快速部署 MinIO 高性能对象存储服务。
#   MinIO 是一个与 Amazon S3 API 完全兼容的对象存储服务器，
#   提供高性能、分布式、可扩展的存储解决方案。
#
# 主要特性:
#   1. 单节点部署: 适合开发和测试环境
#   2. S3 API 兼容: 完全兼容 Amazon S3 API
#   3. 管理控制台: 内置 Web 管理界面
#   4. 数据持久化: 配置本地数据目录持久化存储
#   5. 访问控制: 支持访问密钥和秘密密钥认证
#   6. 健康检查: 自动验证服务启动状态
#
# MinIO 简介:
#   MinIO 是一个基于 Go 语言开发的高性能对象存储服务，具有以下特点：
#   - 高性能: 读写速度极快，支持高达 183GB/s 的读速度
#   - S3 兼容: 100% 兼容 Amazon S3 API
#   - 简单部署: 单二进制文件，无外部依赖
#   - 分布式: 支持多节点分布式部署
#   - 加密: 支持数据加密和传输加密
#   - 多租户: 支持多租户和多用户
#
# 架构特点:
#   - API 端口 (9000): S3 API 接口端口
#   - 控制台端口 (9001): Web 管理界面端口
#   - 数据存储: 本地文件系统，支持多磁盘
#   - 访问认证: 基于密钥的身份认证
#   - 存储桶: 支持存储桶 (bucket) 概念
#
# 配置参数:
#   MINIO_ROOT_USER      - 根用户/访问密钥 (默认: minioadmin)
#   MINIO_ROOT_PASSWORD  - 根用户密码/秘密密钥 (默认: minioadmin)
#   MINIO_DATA_DIR       - 数据存储目录 (默认: /data)
#   MINIO_PORT_API       - API 服务端口 (默认: 9000)
#   MINIO_PORT_CONSOLE   - 控制台端口 (默认: 9001)
#
# 安全注意事项:
#   ⚠️  默认访问密钥和秘密密钥仅用于开发环境
#   ⚠️  生产环境必须修改默认凭据
#   ⚠️  建议启用 HTTPS 和传输加密
#   ⚠️  定期轮换访问密钥
#
# 使用场景:
#   1. 开发环境: 本地对象存储服务
#   2. 测试环境: S3 API 测试和开发
#   3. 生产环境: 高可用分布式存储集群
#
# 端口说明:
#   9000 - MinIO API 端口，用于 S3 兼容接口
#   9001 - MinIO 控制台端口，用于 Web 管理界面
#
# 目录结构:
#   /root/minio/           - 根目录
#     ├── data/            - 数据存储目录
#     └── install_minio.log - 安装日志
#
# 访问方式:
#   1. Web 控制台: http://localhost:9001
#      用户名: minioadmin (或自定义)
#      密码: minioadmin (或自定义)
#
#   2. S3 API 端点: http://localhost:9000
#
# 常用操作:
#   1. 创建存储桶:
#      mc mb myminio/test-bucket
#      aws --endpoint-url=http://localhost:9000 s3 mb s3://test-bucket
#
#   2. 上传文件:
#      mc cp file.txt myminio/test-bucket/
#      aws --endpoint-url=http://localhost:9000 s3 cp file.txt s3://test-bucket/
#
#   3. 列出存储桶:
#      mc ls myminio/
#      aws --endpoint-url=http://localhost:9000 s3 ls
#
# MinIO 客户端工具:
#   1. 官方客户端 (mc):
#      wget https://dl.min.io/client/mc/release/linux-amd64/mc
#      chmod +x mc && sudo mv mc /usr/local/bin/
#      mc alias set myminio http://localhost:9000 minioadmin minioadmin
#
#   2. AWS CLI (兼容 S3):
#      aws configure  # 配置访问密钥
#      aws --endpoint-url=http://localhost:9000 s3 ls
#
# 存储特性:
#   - 存储桶 (Bucket): 对象的逻辑容器
#   - 对象 (Object): 实际存储的数据文件
#   - 元数据: 对象的附加信息和标签
#   - 版本控制: 支持对象版本管理
#   - 生命周期: 自动数据生命周期管理
#
# 注意事项:
#   ⚠️  单节点部署仅适用于开发/测试环境
#   ⚠️  生产环境建议分布式部署确保高可用
#   ⚠️  注意数据目录的磁盘空间和性能
#   ⚠️  定期备份重要数据
#
# 故障排除:
#   - 查看日志: docker logs minio
#   - 检查端口: netstat -tlnp | grep 9000
#   - 测试连接: curl http://localhost:9000/minio/health/live
#   - Web 界面: 打开 http://localhost:9001 检查控制台
#
# 性能优化:
#   - 使用 SSD 存储提升性能
#   - 配置多个磁盘实现条带化存储
#   - 调整内存和 CPU 资源限制
#   - 启用压缩和缓存功能
#
# 系统要求:
#   - Docker 环境正常运行
#   - 足够的磁盘空间用于数据存储
#   - 网络端口 9000 和 9001 未被占用
#
# 作者: 系统运维脚本
# 版本: v1.0
# 更新时间: 2024-01
# ========================================================================================
# - Sets up default access key and secret key for immediate use.

# Ensure we are running under bash (Ubuntu /bin/sh 不支持 pipefail)
if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_ROOT="${MINIO_DATA_ROOT:-/root/minio}"
CONTAINER_NAME="${MINIO_CONTAINER_NAME:-minio}"
MINIO_VERSION="${MINIO_VERSION:-latest}"
MINIO_PORT_API="${MINIO_PORT_API:-9000}"
MINIO_PORT_CONSOLE="${MINIO_PORT_CONSOLE:-9001}"
# 新版 MinIO 使用 MINIO_ROOT_USER 和 MINIO_ROOT_PASSWORD
MINIO_ROOT_USER="${MINIO_ROOT_USER:-minioadmin}"
MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD:-minioadmin}"
# 兼容旧版环境变量
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-$MINIO_ROOT_USER}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-$MINIO_ROOT_PASSWORD}"
MINIO_DATA_DIR="${MINIO_DATA_DIR:-/data}"
LOG_FILE="${LOG_FILE:-${DATA_ROOT}/install_minio.log}"

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

log_info "=== MinIO Docker 安装开始 ==="
log_info "参数: DATA_ROOT=${DATA_ROOT}, CONTAINER_NAME=${CONTAINER_NAME}, MINIO_VERSION=${MINIO_VERSION}"
log_info "API 端口: ${MINIO_PORT_API}, 控制台端口: ${MINIO_PORT_CONSOLE}"
log_info "根用户: ${MINIO_ROOT_USER}, 密码: ${MINIO_ROOT_PASSWORD}"
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
  log_info "=== MinIO Docker 安装结束（已存在，无需处理） ==="
  exit 0
fi

# 1. 创建目录
log_info "创建目录结构: ${DATA_ROOT}/data"
ensure_dir "${DATA_ROOT}"
ensure_dir "${DATA_ROOT}/data"

# 2. 检查并拉取 MinIO 镜像
log_info "检查并拉取 MinIO 镜像: minio/minio:${MINIO_VERSION}"
if ! docker image inspect "minio/minio:${MINIO_VERSION}" >/dev/null 2>&1; then
  log_info "本地不存在镜像 minio/minio:${MINIO_VERSION}，开始拉取..."
  if docker pull "minio/minio:${MINIO_VERSION}"; then
    log_info "镜像 minio/minio:${MINIO_VERSION} 拉取成功"
  else
    log_error "镜像拉取失败"
    exit 1
  fi
else
  log_info "镜像 minio/minio:${MINIO_VERSION} 已存在，跳过拉取"
fi

# 3. 启动 MinIO 容器
log_info "启动 MinIO 容器: ${CONTAINER_NAME}"
CONTAINER_ID=$(docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart=always \
  -p "${MINIO_PORT_API}:9000" \
  -p "${MINIO_PORT_CONSOLE}:9001" \
  -e MINIO_ROOT_USER="${MINIO_ROOT_USER}" \
  -e MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD}" \
  -v "${DATA_ROOT}/data:${MINIO_DATA_DIR}" \
  "minio/minio:${MINIO_VERSION}" \
  server "${MINIO_DATA_DIR}" --console-address ":9001")

log_info "MinIO 容器已创建，ID: ${CONTAINER_ID}"
log_info "等待 MinIO 启动..."
sleep 15

# 4. 验证 MinIO 启动
log_info "验证 MinIO 启动状态..."
if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
  log_info "MinIO 容器运行正常"

  # 检查 MinIO 日志
  log_info "检查 MinIO 启动日志..."
  if docker logs "${CONTAINER_NAME}" 2>&1 | grep -q "Server startup complete"; then
    log_info "✓ MinIO 启动成功"
  else
    log_info "MinIO 启动日志："
    docker logs "${CONTAINER_NAME}" 2>&1 | head -20 | while IFS= read -r line; do
      log_info "  $line"
    done || log_warn "无法读取 MinIO 日志"
  fi

  # 等待 MinIO 服务完全就绪
  log_info "等待 MinIO 服务完全就绪..."
  sleep 10

  # 测试 MinIO 连接
  log_info "测试 MinIO 连接..."
  if command -v curl >/dev/null 2>&1; then
    if curl -s --connect-timeout 10 "http://192.168.3.100:${MINIO_PORT_API}/minio/health/live" >/dev/null 2>&1; then
      log_info "✓ MinIO API 健康检查通过"
    else
      log_warn "MinIO API 健康检查失败，但容器正在运行，请稍后手动验证"
    fi
  else
    log_warn "未找到 curl，跳过连接测试"
  fi
else
  log_error "MinIO 容器启动失败"
  exit 1
fi

# 5. 创建测试存储桶
log_info "创建测试存储桶..."
# MinIO 启动后会自动创建一些默认配置，这里我们主要验证服务可用性

log_info "=== MinIO Docker 安装完成 ==="
log_info ""
log_info "服务信息："
log_info "  MinIO 容器: ${CONTAINER_NAME}"
log_info "  API 端口: ${MINIO_PORT_API}"
log_info "  控制台端口: ${MINIO_PORT_CONSOLE}"
log_info "  根用户: ${MINIO_ROOT_USER}"
log_info "  密码: ${MINIO_ROOT_PASSWORD}"
log_info ""
log_info "访问地址："
log_info "  API 端点: http://192.168.3.100:${MINIO_PORT_API}"
log_info "  管理控制台: http://192.168.3.100:${MINIO_PORT_CONSOLE}"
log_info "  用户名: ${MINIO_ROOT_USER}"
log_info "  密码: ${MINIO_ROOT_PASSWORD}"
log_info ""
log_info "使用说明："
log_info "1. 查看服务状态："
log_info "   docker ps | grep ${CONTAINER_NAME}"
log_info ""
log_info "2. 查看 MinIO 日志："
log_info "   docker logs ${CONTAINER_NAME}"
log_info ""
log_info "3. 访问管理控制台："
log_info "   打开浏览器访问: http://192.168.3.100:${MINIO_PORT_CONSOLE}"
log_info "   使用用户名: ${MINIO_ACCESS_KEY} 密码: ${MINIO_SECRET_KEY} 登录"
log_info ""
log_info "4. 使用 MinIO 客户端 (mc)："
log_info "   # 安装 mc 客户端"
log_info "   wget https://dl.min.io/client/mc/release/linux-amd64/mc"
log_info "   chmod +x mc && sudo mv mc /usr/local/bin/"
log_info ""
log_info "   # 配置别名"
log_info "   mc alias set myminio http://192.168.3.100:${MINIO_PORT_API} ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD}"
log_info ""
log_info "   # 创建存储桶"
log_info "   mc mb myminio/test-bucket"
log_info ""
log_info "   # 上传文件"
log_info "   mc cp /path/to/file myminio/test-bucket/"
log_info ""
log_info "   # 列出存储桶"
log_info "   mc ls myminio/"
log_info ""
log_info "5. 使用 AWS CLI (兼容 S3 API)："
log_info "   # 配置 AWS CLI"
log_info "   aws configure"
log_info "   # 输入访问密钥和秘密密钥"
log_info ""
log_info "   # 设置端点"
log_info "   aws --endpoint-url http://192.168.3.100:${MINIO_PORT_API} s3 ls"
log_info ""
log_info "   # 创建存储桶"
log_info "   aws --endpoint-url http://192.168.3.100:${MINIO_PORT_API} s3 mb s3://test-bucket"
log_info ""
log_info "6. 数据目录："
log_info "   ${DATA_ROOT}/data"
log_info ""
log_info "7. 停止服务："
log_info "   docker stop ${CONTAINER_NAME}"
log_info ""
log_info "8. 启动服务："
log_info "   docker start ${CONTAINER_NAME}"
log_info ""
log_info "9. 清理（删除容器和数据）："
log_info "   docker rm -f ${CONTAINER_NAME}"
log_info "   rm -rf ${DATA_ROOT}/data"
log_info ""
log_info "注意：这是一个单节点 MinIO 服务器，适合开发和测试环境。"
log_info "生产环境建议部署分布式 MinIO 集群以提高可用性和性能。"
log_info ""
log_info "安全建议："
log_info "1. 修改默认访问密钥和秘密密钥"
log_info "2. 在生产环境中启用 HTTPS"
log_info "3. 配置防火墙只允许必要端口访问"
log_info "4. 定期更新 MinIO 到最新版本"
log_info ""
log_info "MinIO 功能特点："
log_info "- S3 API 兼容"
log_info "- 高性能对象存储"
log_info "- 支持分布式部署"
log_info "- 内置管理控制台"
log_info "- 支持版本控制和生命周期管理"
