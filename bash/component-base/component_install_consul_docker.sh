#!/usr/bin/env bash
# ========================================================================================
# Consul Docker 安装脚本 (Consul Docker Installer)
# ========================================================================================
#
# 功能说明:
#   本脚本用于通过 Docker 快速部署 Consul 服务网格解决方案。
#   Consul 是一个服务网络（Service Mesh）解决方案，提供服务发现、健康检查、
#   KV 存储、安全服务通信和多数据中心支持。
#
# 主要特性:
#   1. 服务发现: 支持 DNS 和 HTTP API 两种方式发现服务
#   2. 健康检查: 提供多种健康检查机制（HTTP/TCP/脚本/TTL）
#   3. KV 存储: 分布式键值存储，支持动态配置
#   4. 安全服务通信: 自动 TLS 加密和身份验证
#   5. 多数据中心: 原生支持多数据中心部署
#   6. 服务网格: Sidecar 代理支持，实现服务间安全通信
#
# Consul 简介:
#   Consul 是 HashiCorp 公司开源的工具，用于实现分布式系统的服务发现与配置。
#   与其他同类产品相比，Consul 更易于使用，并且天然支持多数据中心。
#   主要特点：
#   - Raft 算法保证一致性
#   - 健康检查机制完善
#   - 支持多数据中心
#   - 内置 Web UI
#   - 集成 Service Mesh 能力
#
# 架构特点:
#   - HTTP API 端口 (8500): RESTful API 和 Web UI
#   - DNS 端口 (8600): DNS 服务接口
#   - Server RPC 端口 (8300): Server 节点间通信
#   - Serf LAN 端口 (8301/8302): 局域网/广域网 gossip 协议
#   - 数据存储: 基于 Raft 协议的分布式存储
#   - 运行模式: 支持 Server 和 Client 两种模式
#
# 配置参数:
#   CONSUL_PORT_HTTP     - HTTP API 端口 (默认: 8500)
#   CONSUL_PORT_DNS      - DNS 端口 (默认: 8600)
#   CONSUL_MODE          - 运行模式: server/client (默认: server)
#   CONSUL_BOOTSTRAP     - 是否启动引导模式 (默认: true，单节点使用)
#   CONSUL_DATA_ROOT     - 数据目录 (默认: /root/consul)
#   CONSUL_UI            - 是否启用 Web UI (默认: true)
#
# 安全注意事项:
#   ⚠️  默认无访问控制，生产环境必须启用 ACL
#   ⚠️  生产环境建议启用 TLS 加密
#   ⚠️  建议配置防火墙限制端口访问
#   ⚠️  定期备份 KV 存储数据
#
# 使用场景:
#   1. 开发环境: 单节点 Server 模式
#   2. 测试环境: 单节点 Server + ACL
#   3. 生产环境: 3-5 节点 Server 集群 + ACL + TLS
#
# 端口说明:
#   8500 - HTTP API 和 Web UI 端口
#   8600 - DNS 服务端口
#   8300 - Server RPC 端口（Server 节点间通信）
#   8301 - Serf LAN 端口（局域网 gossip）
#   8302 - Serf WAN 端口（广域网 gossip，多数据中心）
#
# 目录结构:
#   /root/consul/            - 根目录
#     ├── config/            - 配置文件目录
#     ├── data/              - 数据目录（Raft 日志）
#     └── install_consul.log - 安装日志
#
# 访问方式:
#   1. Web 控制台: http://localhost:8500/ui
#      默认无需认证（生产环境建议启用 ACL）
#
#   2. HTTP API: http://localhost:8500/v1
#      示例: curl http://localhost:8500/v1/catalog/services
#
#   3. DNS 查询: dig @127.0.0.1 -p 8600 my-service.service.consul
#
#   4. 客户端配置:
#      spring.cloud.consul.host=127.0.0.1
#      spring.cloud.consul.port=8500
#
# 常用操作:
#   1. 服务注册:
#      curl -X PUT http://localhost:8500/v1/agent/service/register \
#        -d '{"ID": "my-service", "Name": "my-service", "Port": 8080}'
#
#   2. 服务发现:
#      curl http://localhost:8500/v1/catalog/services
#      curl http://localhost:8500/v1/catalog/service/my-service
#
#   3. KV 存储操作:
#      # 设置值
#      curl -X PUT http://localhost:8500/v1/kv/config/key1 -d 'value1'
#      # 获取值
#      curl http://localhost:8500/v1/kv/config/key1
#      # 删除值
#      curl -X DELETE http://localhost:8500/v1/kv/config/key1
#
#   4. 健康检查:
#      curl http://localhost:8500/v1/health/checks/my-service
#
# Consul 客户端工具:
#   1. Consul CLI:
#      consul members              # 查看集群成员
#      consul catalog services     # 查看服务列表
#      consul kv get config/key1   # 获取 KV 值
#
#   2. DNS 查询:
#      dig @127.0.0.1 -p 8600 my-service.service.consul
#      nslookup -port=8600 my-service.service.consul 127.0.0.1
#
# 服务特性:
#   - 服务注册与发现: 自动注册和健康检查
#   - 健康检查: HTTP/TCP/脚本/TTL 多种方式
#   - KV 存储: 分布式键值存储，支持 Watch
#   - 多数据中心: 原生支持跨数据中心通信
#   - Service Mesh: Sidecar 代理，mTLS 加密
#   - ACL 系统: 细粒度访问控制
#
# 注意事项:
#   ⚠️  单节点 Server 仅适用于开发/测试环境
#   ⚠️  生产环境建议 3-5 个 Server 节点确保高可用
#   ⚠️  Server 节点数必须为奇数（Raft 协议要求）
#   ⚠️  注意数据目录的磁盘性能和空间
#   ⚠️  定期备份 KV 存储和 Raft 日志
#
# 故障排除:
#   - 查看日志: docker logs consul
#   - 检查端口: netstat -tlnp | grep 8500
#   - 测试连接: curl http://localhost:8500/v1/status/leader
#   - Web 界面: 打开 http://localhost:8500/ui 检查控制台
#   - 集群状态: docker exec consul consul members
#
# 性能优化:
#   - 使用 SSD 存储提升 Raft 日志性能
#   - 配置合理的日志级别（生产环境建议 INFO）
#   - 监控 Consul 指标（Prometheus 集成）
#   - 定期清理过期服务和 KV 数据
#
# 系统要求:
#   - Docker 环境正常运行
#   - 至少 512MB 可用内存
#   - 网络端口 8500、8600 未被占用
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
DATA_ROOT="${CONSUL_DATA_ROOT:-/root/consul}"
CONTAINER_NAME="${CONSUL_CONTAINER_NAME:-consul}"
CONSUL_VERSION="${CONSUL_VERSION:-1.15.4}"
CONSUL_MODE="${CONSUL_MODE:-server}"
CONSUL_BOOTSTRAP="${CONSUL_BOOTSTRAP:-true}"
CONSUL_UI="${CONSUL_UI:-true}"
CONSUL_PORT_HTTP="${CONSUL_PORT_HTTP:-8500}"
CONSUL_PORT_DNS="${CONSUL_PORT_DNS:-8600}"
LOG_FILE="${LOG_FILE:-${DATA_ROOT}/install_consul.log}"

# Consul HCL expects booleans for `server` and an integer for `bootstrap_expect`.
# This script historically accepted user-friendly env vars:
# - CONSUL_MODE: server|client
# - CONSUL_BOOTSTRAP: true|false (single-node dev/test convenience)
CONSUL_SERVER_BOOL="false"
if [[ "${CONSUL_MODE,,}" == "server" ]]; then
  CONSUL_SERVER_BOOL="true"
fi

# Allow overriding explicitly with CONSUL_BOOTSTRAP_EXPECT; otherwise derive a sane default.
CONSUL_BOOTSTRAP_EXPECT="${CONSUL_BOOTSTRAP_EXPECT:-}"
if [[ -z "${CONSUL_BOOTSTRAP_EXPECT}" ]]; then
  if [[ "${CONSUL_BOOTSTRAP,,}" == "true" ]]; then
    CONSUL_BOOTSTRAP_EXPECT="1"
  else
    # Default quorum-friendly value; adjust via CONSUL_BOOTSTRAP_EXPECT for real clusters.
    CONSUL_BOOTSTRAP_EXPECT="3"
  fi
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

log_info "=== Consul Docker 安装开始 ==="
log_info "参数: DATA_ROOT=${DATA_ROOT}, CONTAINER_NAME=${CONTAINER_NAME}, CONSUL_VERSION=${CONSUL_VERSION}"
log_info "运行模式: ${CONSUL_MODE}, 引导模式: ${CONSUL_BOOTSTRAP}, Web UI: ${CONSUL_UI}"
log_info "HTTP 端口: ${CONSUL_PORT_HTTP}, DNS 端口: ${CONSUL_PORT_DNS}"
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
  log_info "=== Consul Docker 安装结束（已存在，无需处理） ==="
  exit 0
fi

# 1. 创建目录
log_info "创建目录结构: ${DATA_ROOT}/{config,data}"
ensure_dir "${DATA_ROOT}"
ensure_dir "${DATA_ROOT}/config"
ensure_dir "${DATA_ROOT}/data"

# 2. 准备配置文件
CONFIG_PATH="${DATA_ROOT}/config/consul.hcl"
log_info "写入配置文件到 ${CONFIG_PATH}"

cat > "${CONFIG_PATH}" <<EOF
# Consul 配置文件

# 数据中心名称
datacenter = "dc1"

# 节点名称
node_name = "consul-server-1"

# 运行模式（server/client）
server = ${CONSUL_SERVER_BOOL}

# 期望 Server 节点数（单节点用 1；生产建议 3 或 5）
bootstrap_expect = ${CONSUL_BOOTSTRAP_EXPECT}

# 数据目录
data_dir = "/consul/data"

# 日志级别（TRACE/DEBUG/INFO/WARN/ERR）
log_level = "INFO"

# 客户端监听地址（0.0.0.0 允许所有地址访问）
client_addr = "0.0.0.0"

# UI 配置
ui_config {
  enabled = ${CONSUL_UI}
}

# 端口配置
ports {
  http = ${CONSUL_PORT_HTTP}
  dns  = ${CONSUL_PORT_DNS}
}

# DNS 配置
dns_config {
  allow_stale = true
  max_stale   = "5s"
  node_ttl    = "0s"
  service_ttl = {
    "*" = "0s"
  }
}

# 性能配置
performance {
  raft_multiplier = 1
}

# ACL 配置（生产环境建议启用）
# acl {
#   enabled        = true
#   default_policy = "deny"
#   tokens {
#     initial_management = "your-root-token"
#   }
# }

# 自动清理配置（可选）
# leave_on_terminate = true
# skip_leave_on_interrupt = true
EOF

log_info "配置文件创建成功"

# 3. 检查并拉取 Consul 镜像
log_info "检查并拉取 Consul 镜像: consul:${CONSUL_VERSION}"
if ! docker image inspect "consul:${CONSUL_VERSION}" >/dev/null 2>&1; then
  log_info "本地不存在镜像 consul:${CONSUL_VERSION}，开始拉取..."
  if docker pull "consul:${CONSUL_VERSION}"; then
    log_info "镜像 consul:${CONSUL_VERSION} 拉取成功"
  else
    log_error "镜像拉取失败"
    exit 1
  fi
else
  log_info "镜像 consul:${CONSUL_VERSION} 已存在，跳过拉取"
fi

# 4. 启动 Consul 容器
log_info "启动 Consul 容器: ${CONTAINER_NAME}"
CONTAINER_ID=$(docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart=always \
  -p "${CONSUL_PORT_HTTP}:${CONSUL_PORT_HTTP}" \
  -p "${CONSUL_PORT_DNS}:${CONSUL_PORT_DNS}/tcp" \
  -p "${CONSUL_PORT_DNS}:${CONSUL_PORT_DNS}/udp" \
  -v "${CONFIG_PATH}:/consul/config/consul.hcl" \
  -v "${DATA_ROOT}/data:/consul/data" \
  "consul:${CONSUL_VERSION}" \
  agent -config-dir=/consul/config)

log_info "Consul 容器已创建，ID: ${CONTAINER_ID}"
log_info "等待 Consul 启动..."
sleep 10

# 5. 验证 Consul 启动
log_info "验证 Consul 启动状态..."
if docker ps --format '{{.Names}}' | grep -qw "${CONTAINER_NAME}"; then
  log_info "Consul 容器运行正常"

  # 检查 Consul 日志
  log_info "检查 Consul 启动日志..."
  if docker logs "${CONTAINER_NAME}" 2>&1 | grep -q "Consul agent running"; then
    log_info "✓ Consul 启动成功"
  else
    log_info "Consul 启动日志："
    docker logs "${CONTAINER_NAME}" 2>&1 | head -20 | while IFS= read -r line; do
      log_info "  $line"
    done || log_warn "无法读取 Consul 日志"
  fi

  # 等待 Consul 服务完全就绪
  log_info "等待 Consul 服务完全就绪..."
  sleep 5

  # 测试 Consul 连接
  log_info "测试 Consul 连接..."
  if command -v curl >/dev/null 2>&1; then
    if curl -s --connect-timeout 10 "http://192.168.3.100:${CONSUL_PORT_HTTP}/v1/status/leader" >/dev/null 2>&1; then
      log_info "✓ Consul HTTP API 健康检查通过"
      
      # 显示 Leader 信息
      LEADER=$(curl -s "http://192.168.3.100:${CONSUL_PORT_HTTP}/v1/status/leader")
      log_info "当前 Leader: ${LEADER}"
    else
      log_warn "Consul HTTP API 健康检查失败，但容器正在运行，请稍后手动验证"
    fi
  else
    log_warn "未找到 curl，跳过连接测试"
  fi
  
  # 测试 DNS 服务
  if command -v dig >/dev/null 2>&1; then
    if dig @192.168.3.100 -p "${CONSUL_PORT_DNS}" consul.service.consul +short >/dev/null 2>&1; then
      log_info "✓ Consul DNS 服务正常"
    else
      log_warn "Consul DNS 服务检查失败（可选）"
    fi
  else
    log_warn "未找到 dig，跳过 DNS 测试"
  fi
else
  log_error "Consul 容器启动失败"
  exit 1
fi

log_info "=== Consul Docker 安装完成 ==="
log_info ""
log_info "服务信息："
log_info "  Consul 容器: ${CONTAINER_NAME}"
log_info "  运行模式: ${CONSUL_MODE}"
log_info "  数据中心: dc1"
log_info "  HTTP 端口: ${CONSUL_PORT_HTTP}"
log_info "  DNS 端口: ${CONSUL_PORT_DNS}"
log_info ""
log_info "访问地址："
log_info "  Web 控制台: http://192.168.3.100:${CONSUL_PORT_HTTP}/ui"
log_info "  HTTP API: http://192.168.3.100:${CONSUL_PORT_HTTP}/v1"
log_info "  DNS 服务: 192.168.3.100:${CONSUL_PORT_DNS}"
log_info ""
log_info "Spring Cloud 配置示例："
log_info "  # application.yml"
log_info "  spring:"
log_info "    cloud:"
log_info "      consul:"
log_info "        host: 192.168.3.100"
log_info "        port: ${CONSUL_PORT_HTTP}"
log_info "        discovery:"
log_info "          service-name: \${spring.application.name}"
log_info "          prefer-ip-address: true"
log_info "        config:"
log_info "          enabled: true"
log_info "          format: YAML"
log_info "          data-key: data"
log_info ""
log_info "使用说明："
log_info "1. 查看服务状态："
log_info "   docker ps | grep ${CONTAINER_NAME}"
log_info ""
log_info "2. 查看 Consul 日志："
log_info "   docker logs ${CONTAINER_NAME}"
log_info "   docker logs -f ${CONTAINER_NAME}  # 实时查看"
log_info ""
log_info "3. 访问管理控制台："
log_info "   打开浏览器访问: http://192.168.3.100:${CONSUL_PORT_HTTP}/ui"
log_info ""
log_info "4. 查看集群成员："
log_info "   docker exec ${CONTAINER_NAME} consul members"
log_info ""
log_info "5. 查看服务列表："
log_info "   curl http://192.168.3.100:${CONSUL_PORT_HTTP}/v1/catalog/services"
log_info ""
log_info "6. 服务注册示例："
log_info "   curl -X PUT http://192.168.3.100:${CONSUL_PORT_HTTP}/v1/agent/service/register \
"
log_info "     -d '{\"ID\": \"my-service\", \"Name\": \"my-service\", \"Port\": 8080}'"
log_info ""
log_info "7. KV 存储操作："
log_info "   # 设置值"
log_info "   curl -X PUT http://192.168.3.100:${CONSUL_PORT_HTTP}/v1/kv/config/key1 -d 'value1'"
log_info "   # 获取值"
log_info "   curl http://192.168.3.100:${CONSUL_PORT_HTTP}/v1/kv/config/key1?raw"
log_info "   # 删除值"
log_info "   curl -X DELETE http://192.168.3.100:${CONSUL_PORT_HTTP}/v1/kv/config/key1"
log_info ""
log_info "8. DNS 查询："
log_info "   dig @192.168.3.100 -p ${CONSUL_PORT_DNS} my-service.service.consul"
log_info ""
log_info "9. 数据目录："
log_info "   配置: ${DATA_ROOT}/config"
log_info "   数据: ${DATA_ROOT}/data"
log_info ""
log_info "10. 停止服务："
log_info "   docker stop ${CONTAINER_NAME}"
log_info ""
log_info "11. 启动服务："
log_info "   docker start ${CONTAINER_NAME}"
log_info ""
log_info "12. 清理（删除容器和数据）："
log_info "   docker rm -f ${CONTAINER_NAME}"
log_info "   rm -rf ${DATA_ROOT}"
log_info ""
log_info "注意：当前使用单节点 Server 模式，适合开发和测试环境。"
log_info "生产环境建议部署 3-5 个 Server 节点集群以确保高可用性。"
log_info ""
log_info "安全建议："
log_info "1. 生产环境启用 ACL 访问控制"
log_info "2. 启用 TLS 加密通信"
log_info "3. 配置防火墙只允许必要端口访问"
log_info "4. 定期备份 KV 存储数据"
log_info "5. 定期更新 Consul 到最新版本"
log_info ""
log_info "Consul 功能特点："
log_info "- 服务发现和服务健康检查"
log_info "- 分布式 KV 存储"
log_info "- 多数据中心支持"
log_info "- Service Mesh（服务网格）"
log_info "- 支持 Spring Cloud、Kubernetes、Docker 等"
log_info "- 提供 DNS 和 HTTP API 两种接口"
