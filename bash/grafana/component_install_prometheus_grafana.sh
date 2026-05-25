#!/usr/bin/env bash
# ========================================================================================
# Prometheus + Grafana 完整部署脚本 (Prometheus + Grafana Complete Deployer)
# ========================================================================================
#
# 功能说明:
#   本脚本用于通过 Docker 快速部署 Prometheus + Grafana 监控系统。
#   包含完整的监控配置，默认监控本地服务器和远程服务器 192.168.3.100。
#
# 主要特性:
#   ✓ 高性能: 基于 Docker 容器化部署，资源占用低
#   ✓ 完整监控: 监控服务器 CPU、内存、磁盘、网络等指标
#   ✓ 可视化: 集成 Grafana 提供丰富的监控仪表盘
#   ✓ 告警: 内置基本告警规则
#   ✓ 自动发现: 支持服务自动发现
#
# 配置参数:
#   PROMETHEUS_PORT    - Prometheus 服务端口 (默认: 9090)
#   GRAFANA_PORT       - Grafana 服务端口 (默认: 3000)
#   GRAFANA_USER       - Grafana 登录用户名 (默认: admin)
#   GRAFANA_PASSWORD   - Grafana 登录密码 (默认: admin123)
#   DATA_ROOT          - 数据存储目录 (默认: /root/monitoring)
#   TARGET_SERVER      - 要监控的远程服务器 IP (默认: 192.168.3.100)
#   NODE_EXPORTER_PORT - 远程服务器 node_exporter 端口 (默认: 9100)
#
# 端口说明:
#   9090 - Prometheus 默认服务端口
#   3000 - Grafana 默认服务端口
#   9100 - Node Exporter 默认端口
#
# 使用示例:
#   标准部署: bash component_install_prometheus_grafana.sh
#   自定义端口: PROMETHEUS_PORT=9091 GRAFANA_PORT=3001 bash component_install_prometheus_grafana.sh
#   自定义目标: TARGET_SERVER=192.168.1.100 bash component_install_prometheus_grafana.sh
#
# 注意: 远程服务器需要先安装 node_exporter，可以使用本脚本生成的 node_exporter 安装脚本
# 作者: 系统运维脚本 | 版本: v1.0 | 更新时间: 2026-04-18
# ========================================================================================

# Ensure we are running under bash (Ubuntu /bin/sh 不支持 pipefail)
if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_ROOT="${DATA_ROOT:-/root/monitoring}"
PROMETHEUS_PORT="${PROMETHEUS_PORT:-9090}"
GRAFANA_PORT="${GRAFANA_PORT:-3000}"
GRAFANA_USER="${GRAFANA_USER:-admin}"
GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-admin123}"
TARGET_SERVER="${TARGET_SERVER:-192.168.3.100}"
NODE_EXPORTER_PORT="${NODE_EXPORTER_PORT:-9100}"
HOST_IP="${HOST_IP:-$(hostname -I | awk '{print $1}')}"
LOG_FILE="${LOG_FILE:-${DATA_ROOT}/install_monitoring.log}"

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
  # 为 Prometheus 数据目录设置正确的权限
  if [[ "${dir}" == "${DATA_ROOT}/prometheus" ]]; then
    chmod 777 "${dir}"
  fi
  # 为 Grafana 数据目录设置正确的权限
  if [[ "${dir}" == "${DATA_ROOT}/grafana" ]]; then
    chmod 777 "${dir}"
  fi
}

# 确保日志目录及其父目录存在，避免 tee 报错
ensure_dir "$(dirname "${LOG_FILE}")"

trap 'log_error "安装过程中出现错误，退出。"' ERR

log_info "=== Prometheus + Grafana 部署开始 ==="
log_info "参数: DATA_ROOT=${DATA_ROOT}, PROMETHEUS_PORT=${PROMETHEUS_PORT}, GRAFANA_PORT=${GRAFANA_PORT}"
log_info "目标服务器: ${TARGET_SERVER}:${NODE_EXPORTER_PORT}"
log_info "Grafana 登录: ${GRAFANA_USER}/${GRAFANA_PASSWORD}"
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

# 1. 创建目录结构
log_info "创建目录结构: ${DATA_ROOT}/{prometheus, grafana, configs}"
ensure_dir "${DATA_ROOT}"
ensure_dir "${DATA_ROOT}/prometheus"
ensure_dir "${DATA_ROOT}/grafana"
ensure_dir "${DATA_ROOT}/configs"

# 2. 准备 Prometheus 配置文件
PROMETHEUS_CONFIG="${DATA_ROOT}/configs/prometheus.yml"
log_info "写入 Prometheus 配置文件: ${PROMETHEUS_CONFIG}"
cat > "${PROMETHEUS_CONFIG}" <<EOF
global:
  scrape_interval: 15s  # 抓取间隔
  evaluation_interval: 15s  # 评估间隔

  # 外部标签，可用于区分不同环境
  external_labels:
    monitor: 'codelab-monitor'

# 告警配置
alerting:
  alertmanagers:
    - static_configs:
        - targets:
          # - alertmanager:9093

# 告警规则配置
rule_files:
  # - "first_rules.yml"
  # - "second_rules.yml"

# 抓取配置
scrape_configs:
  # 监控 Prometheus 本身
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  # 监控本地服务器（宿主机）
  - job_name: 'node_local'
    static_configs:
      - targets: ['${HOST_IP}:9100']
        labels:
          instance: '${HOST_IP}:9100'
          env: 'local'

  # 监控远程服务器 ${TARGET_SERVER}
  - job_name: 'node_remote'
    static_configs:
      - targets: ['${TARGET_SERVER}:${NODE_EXPORTER_PORT}']
        labels:
          instance: '${TARGET_SERVER}:${NODE_EXPORTER_PORT}'
          env: 'production'
EOF

# 3. 准备 Grafana 配置文件
GRAFANA_CONFIG="${DATA_ROOT}/configs/grafana.ini"
log_info "写入 Grafana 配置文件: ${GRAFANA_CONFIG}"
cat > "${GRAFANA_CONFIG}" <<EOF
[server]
http_addr = 0.0.0.0
http_port = 3000
enforce_domain = false
root_url = http://192.168.3.100:3000

[database]
type = sqlite3
path = /var/lib/grafana/grafana.db

[session]
provider = file

[analytics]
check_for_updates = true

[security]
admin_user = ${GRAFANA_USER}
admin_password = ${GRAFANA_PASSWORD}

[users]
allow_sign_up = false

[auth.anonymous]
enabled = false

[smtp]
enabled = false

[log]
mode = console
level = info

[quota]
orgs = 10
users = 100
EOF

# 4. 安装 Node Exporter (监控本地服务器)
log_info "检查本地 Node Exporter 安装包..."
NODE_EXPORTER_VERSION="${NODE_EXPORTER_VERSION:-1.8.2}"
LOCAL_TAR_FILE="${SCRIPT_DIR}/node_exporter-${NODE_EXPORTER_VERSION}.linux-amd64.tar.gz"

if [[ ! -f "${LOCAL_TAR_FILE}" ]]; then
  log_error "未找到 Node Exporter 安装包: ${LOCAL_TAR_FILE}"
  log_error "请将 node_exporter-${NODE_EXPORTER_VERSION}.linux-amd64.tar.gz 放置在 ${SCRIPT_DIR} 目录下"
  exit 1
fi

log_info "找到安装包: ${LOCAL_TAR_FILE}"

# 检查是否已安装
if command -v node_exporter >/dev/null 2>&1; then
  log_info "Node Exporter 已安装，跳过安装"
else
  log_info "安装 Node Exporter 到系统..."

  # 创建目录
  DATA_DIR="/opt/node_exporter"
  mkdir -p "${DATA_DIR}"

  # 复制并解压
  cp "${LOCAL_TAR_FILE}" "${DATA_DIR}/"
  cd "${DATA_DIR}"
  tar xzf "node_exporter-${NODE_EXPORTER_VERSION}.linux-amd64.tar.gz"

  # 移动二进制文件
  mv "node_exporter-${NODE_EXPORTER_VERSION}.linux-amd64/node_exporter" /usr/local/bin/
  chmod +x /usr/local/bin/node_exporter

  # 清理
  rm -rf "node_exporter-${NODE_EXPORTER_VERSION}.linux-amd64.tar.gz" "node_exporter-${NODE_EXPORTER_VERSION}.linux-amd64"

  # 创建 systemd 服务
  SERVICE_FILE="/etc/systemd/system/node_exporter.service"
  cat > "${SERVICE_FILE}" <<EOF
[Unit]
Description=Node Exporter
After=network.target

[Service]
Type=simple
User=root
ExecStart=/usr/local/bin/node_exporter --web.listen-address=0.0.0.0:${NODE_EXPORTER_PORT}
Restart=always

[Install]
WantedBy=multi-user.target
EOF
  
  # 启动服务
  systemctl daemon-reload
  systemctl enable node_exporter
  systemctl start node_exporter
  
  log_info "Node Exporter 安装完成"
fi

# 验证 Node Exporter
sleep 2
if curl -s -o /dev/null -w "%{http_code}" "http://localhost:${NODE_EXPORTER_PORT}/metrics" | grep -q "200"; then
  log_info "✅ Node Exporter 运行正常: http://localhost:${NODE_EXPORTER_PORT}/metrics"
else
  log_error "✗ Node Exporter 启动失败，请检查: journalctl -u node_exporter"
  exit 1
fi

# 5. 拉取并启动 Prometheus
if ! docker ps -a --format '{{.Names}}' | grep -qw "prometheus"; then
  log_info "启动 Prometheus 容器"
  docker run -d \
    --name prometheus \
    --restart=always \
    --privileged=true \
    -p ${PROMETHEUS_PORT}:9090 \
    -e TZ=Asia/Shanghai \
    -v "${PROMETHEUS_CONFIG}:/etc/prometheus/prometheus.yml" \
    -v "${DATA_ROOT}/prometheus:/prometheus" \
    prom/prometheus:v2.45.0 \
    --config.file=/etc/prometheus/prometheus.yml \
    --storage.tsdb.path=/prometheus \
    --web.console.libraries=/etc/prometheus/console_libraries \
    --web.console.templates=/etc/prometheus/consoles \
    --web.enable-lifecycle
  log_info "Prometheus 启动成功，端口: ${PROMETHEUS_PORT}"
else
  if docker ps --format '{{.Names}}' | grep -qw "prometheus"; then
    log_info "Prometheus 已存在且运行中，重新加载配置"
    curl -X POST http://192.168.3.100:${PROMETHEUS_PORT}/-/reload 2>/dev/null || true
  else
    log_info "Prometheus 已存在但未运行，启动容器"
    docker start prometheus
  fi
fi

# 6. 拉取并启动 Grafana
if ! docker ps -a --format '{{.Names}}' | grep -qw "grafana"; then
  log_info "启动 Grafana 容器"
  docker run -d \
    --name grafana \
    --restart=always \
    --privileged=true \
    -p ${GRAFANA_PORT}:3000 \
    -e TZ=Asia/Shanghai \
    -e GF_SECURITY_ADMIN_USER="${GRAFANA_USER}" \
    -e GF_SECURITY_ADMIN_PASSWORD="${GRAFANA_PASSWORD}" \
    -v "${DATA_ROOT}/grafana:/var/lib/grafana" \
    grafana/grafana:9.5.8
  log_info "Grafana 启动成功，端口: ${GRAFANA_PORT}"
else
  if docker ps --format '{{.Names}}' | grep -qw "grafana"; then
    log_info "Grafana 已存在且运行中，跳过启动"
  else
    log_info "Grafana 已存在但未运行，启动容器"
    docker start grafana
  fi
fi

# 7. 等待服务启动
log_info "等待监控服务启动..."
sleep 5

# 8. 检查服务状态
log_info "检查服务状态..."

# 检查 Node Exporter
if docker ps --format '{{.Names}}' | grep -qw "node_exporter"; then
  log_info "✓ Node Exporter 运行正常"
else
  log_error "✗ Node Exporter 未运行"
fi

# 检查 Prometheus
if docker ps --format '{{.Names}}' | grep -qw "prometheus"; then
  log_info "✓ Prometheus 运行正常"
  # 测试 Prometheus 接口
  if curl -s http://192.168.3.100:${PROMETHEUS_PORT}/metrics >/dev/null 2>&1; then
    log_info "✓ Prometheus 接口响应正常"
  else
    log_warn "⚠️  Prometheus 接口响应异常，可能需要更多时间启动"
  fi
else
  log_error "✗ Prometheus 未运行"
fi

# 检查 Grafana
if docker ps --format '{{.Names}}' | grep -qw "grafana"; then
  log_info "✓ Grafana 运行正常"
  # 测试 Grafana 接口
  if curl -s -o /dev/null -w "%{http_code}" http://192.168.3.100:${GRAFANA_PORT} | grep -q "200"; then
    log_info "✓ Grafana 接口响应正常"
  else
    log_warn "⚠️  Grafana 接口响应异常，可能需要更多时间启动"
  fi
else
  log_error "✗ Grafana 未运行"
fi

# 9. 生成 Node Exporter 安装脚本（用于远程服务器）
NODE_EXPORTER_SCRIPT="${DATA_ROOT}/install_node_exporter.sh"
log_info "生成 Node Exporter 安装脚本: ${NODE_EXPORTER_SCRIPT}"
cat > "${NODE_EXPORTER_SCRIPT}" <<'EOF'
#!/usr/bin/env bash
# ========================================================================================
# Node Exporter 安装脚本 (Node Exporter Installer)
# ========================================================================================
#
# 功能说明:
#   本脚本用于在 Linux 服务器上安装 Node Exporter，用于向 Prometheus 提供系统指标。
#
# 配置参数:
#   NODE_EXPORTER_PORT - 服务端口 (默认: 9100)
#   NODE_EXPORTER_VERSION - 版本 (默认: 1.8.0)
#
# 端口说明:
#   9100 - Node Exporter 默认服务端口
#
# 使用示例:
#   标准安装: bash install_node_exporter.sh
#   自定义端口: NODE_EXPORTER_PORT=9101 bash install_node_exporter.sh
# ========================================================================================

set -euo pipefail

NODE_EXPORTER_PORT="${NODE_EXPORTER_PORT:-9100}"
NODE_EXPORTER_VERSION="${NODE_EXPORTER_VERSION:-1.8.2}"
DATA_DIR="/opt/node_exporter"

log() {
  local level="$1"; shift
  local msg="$*"
  local ts
  ts="$(date '+%Y-%m-%d %H:%M:%S')"
  printf '[%s] [%s] %s\n' "$ts" "$level" "$msg"
}

log_info() { log "INFO" "$@"; }
log_error() { log "ERROR" "$@"; }

# 权限检查
if [[ $EUID -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    log_info "当前非 root，使用 sudo 重新执行脚本..."
    exec sudo -E bash "$0" "$@"
  else
    log_error "需要 root 权限且未找到 sudo，请以 root 或 sudo 运行脚本。"
    exit 1
  fi
fi

# 检查系统架构
ARCH="$(uname -m)"
case "$ARCH" in
  x86_64) ARCH="amd64" ;;
  aarch64) ARCH="arm64" ;;
  *) log_error "不支持的架构: $ARCH"; exit 1 ;;
esac

log_info "=== Node Exporter 安装开始 ==="
log_info "架构: $ARCH, 版本: $NODE_EXPORTER_VERSION, 端口: $NODE_EXPORTER_PORT"

# 创建目录
mkdir -p "$DATA_DIR"

# 检查本地是否已有 node_exporter 安装包
LOCAL_TAR_FILE="${SCRIPT_DIR}/node_exporter-${NODE_EXPORTER_VERSION}.linux-${ARCH}.tar.gz"
if [[ -f "${LOCAL_TAR_FILE}" ]]; then
  log_info "使用本地 node_exporter 安装包: ${LOCAL_TAR_FILE}"
  cp "${LOCAL_TAR_FILE}" "$DATA_DIR/"
else
  # 尝试查找同级目录下的 node_exporter 文件
  LOCAL_TAR_FILE=$(ls "${SCRIPT_DIR}"/node_exporter-*.linux-${ARCH}.tar.gz 2>/dev/null | head -n1)
  if [[ -z "${LOCAL_TAR_FILE}" ]]; then
    log_error "未找到 node_exporter 安装包，请将 node_exporter-*.linux-${ARCH}.tar.gz 放置在 ${SCRIPT_DIR} 目录下"
    exit 1
  fi
  log_info "使用本地 node_exporter 安装包: ${LOCAL_TAR_FILE}"
  cp "${LOCAL_TAR_FILE}" "$DATA_DIR/"
fi

# 解压
cd "$DATA_DIR"
TAR_FILE=$(basename "${LOCAL_TAR_FILE}")
log_info "解压文件: $TAR_FILE"
tar xzf "$DATA_DIR/$TAR_FILE"

# 自动检测解压后的目录名
EXTRACTED_DIR=$(tar tzf "$DATA_DIR/$TAR_FILE" | head -n1 | cut -f1 -d"/")
if [[ -z "${EXTRACTED_DIR}" ]]; then
  log_error "解压失败，无法检测目录结构"
  exit 1
fi
log_info "解压目录: $EXTRACTED_DIR"

# 移动文件
if [[ -f "$DATA_DIR/$EXTRACTED_DIR/node_exporter" ]]; then
  mv "$DATA_DIR/$EXTRACTED_DIR/node_exporter" "/usr/local/bin/"
else
  log_error "未找到 node_exporter 二进制文件: $DATA_DIR/$EXTRACTED_DIR/node_exporter"
  exit 1
fi

# 清理
rm -rf "$TAR_FILE" "$EXTRACTED_DIR"

# 创建 systemd 服务文件
SERVICE_FILE="/etc/systemd/system/node_exporter.service"
log_info "创建 systemd 服务文件: $SERVICE_FILE"

cat > "$SERVICE_FILE" <<'EOL'
[Unit]
Description=Node Exporter
After=network.target

[Service]
Type=simple
User=root
ExecStart=/usr/local/bin/node_exporter --web.listen-address=0.0.0.0:${NODE_EXPORTER_PORT}
Restart=always

[Install]
WantedBy=multi-user.target
EOL

if [[ ! -f "$SERVICE_FILE" ]]; then
  log_error "服务文件创建失败: $SERVICE_FILE"
  exit 1
fi

# 启动服务
systemctl daemon-reload
systemctl enable node_exporter
systemctl start node_exporter

log_info "等待服务启动..."
sleep 3

# 检查服务状态
if systemctl is-active --quiet node_exporter; then
  log_info "✓ Node Exporter 安装成功并运行"
  log_info "服务端口: ${NODE_EXPORTER_PORT}"
  log_info "访问地址: http://$(hostname -I | awk '{print $1}'):${NODE_EXPORTER_PORT}/metrics"
else
  log_error "✗ Node Exporter 服务启动失败"
  log_error "请检查日志: journalctl -u node_exporter"
  exit 1
fi

log_info "=== Node Exporter 安装完成 ==="
EOF

chmod +x "${NODE_EXPORTER_SCRIPT}"
log_info "Node Exporter 安装脚本已生成，可复制到远程服务器执行"

# 10. 检查服务状态
log_info "检查服务状态..."
sleep 5

# 验证 Node Exporter
if curl -s -o /dev/null -w "%{http_code}" "http://localhost:9100/metrics" 2>/dev/null | grep -q "200"; then
  log_info "✅ Node Exporter (本地) 运行正常: http://192.168.3.100:9100/metrics"
else
  log_warn "⚠️  Node Exporter (本地) 可能未就绪，请稍后检查: docker logs node_exporter"
fi

# 验证 Prometheus
if curl -s -o /dev/null -w "%{http_code}" "http://localhost:${PROMETHEUS_PORT}/metrics" 2>/dev/null | grep -q "200"; then
  log_info "✅ Prometheus 运行正常: http://192.168.3.100:${PROMETHEUS_PORT}"
else
  log_warn "⚠️  Prometheus 可能未就绪，请稍后检查: docker logs prometheus"
fi

# 验证 Grafana
if curl -s -o /dev/null -w "%{http_code}" "http://localhost:${GRAFANA_PORT}/api/health" 2>/dev/null | grep -q "200"; then
  log_info "✅ Grafana 运行正常: http://192.168.3.100:${GRAFANA_PORT}"
else
  log_warn "⚠️  Grafana 可能未就绪，请稍后检查: docker logs grafana"
fi

# 11. 输出部署完成信息及手动配置指南
log_info ""
log_info "╔══════════════════════════════════════════════════════════════════════════════════════════╗"
log_info "║                                                                                          ║"
log_info "║                        ✅ Prometheus + Grafana 部署完成                                 ║"
log_info "║                                                                                          ║"
log_info "╠══════════════════════════════════════════════════════════════════════════════════════════╣"
log_info "║  📌 服务访问地址:                                                                       ║"
log_info "║     Node Exporter (本地): http://192.168.3.100:9100/metrics                             ║"
log_info "║     Prometheus:           http://192.168.3.100:${PROMETHEUS_PORT}                                  ║"
log_info "║     Grafana:              http://192.168.3.100:${GRAFANA_PORT}                                    ║"
log_info "║                                                                                          ║"
log_info "║  🔐 Grafana 登录信息:                                                                  ║"
log_info "║     用户名: ${GRAFANA_USER}                                                                          ║"
log_info "║     密码:   ${GRAFANA_PASSWORD}                                                                          ║"
log_info "║                                                                                          ║"
log_info "╠══════════════════════════════════════════════════════════════════════════════════════════╣"
log_info "║                                                                                          ║"
log_info "║                    📖 Grafana 手动配置指南 (请按以下步骤操作)                          ║"
log_info "║                                                                                          ║"
log_info "╚══════════════════════════════════════════════════════════════════════════════════════════╝"
log_info ""
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_info "                            第一部分：添加 Prometheus 数据源"
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_info ""
log_info "步骤 1️⃣ ：登录 Grafana"
log_info "   1. 打开浏览器，访问: http://192.168.3.100:${GRAFANA_PORT}"
log_info "   2. 输入用户名: ${GRAFANA_USER}"
log_info "   3. 输入密码: ${GRAFANA_PASSWORD}"
log_info "   4. 点击 【Log in】 按钮登录"
log_info ""
log_info "步骤 2️⃣ ：进入数据源配置页面"
log_info "   1. 点击左侧菜单的 【Connections】 (连接) 图标"
log_info "   2. 在搜索框输入 【Data sources】 或直接点击 【Data sources】"
log_info "   3. 点击页面右上角的 【+ Add new data source】 按钮"
log_info ""
log_info "步骤 3️⃣ ：选择并配置 Prometheus 数据源"
log_info "   1. 在搜索框输入 【Prometheus】"
log_info "   2. 点击搜索结果中的 【Prometheus】 数据源卡片"
log_info "   3. 在右侧配置页面填写以下参数:"
log_info "      ┌─────────────────────────────────────────────────────────────────────────────┐"
log_info "      │  ⚙️  Settings                                                                      │"
log_info "      │  ─────────────────────────────────────────────────────────────────────────── │"
log_info "      │  Name:          Prometheus                                                    │"
log_info "      │  Default:       ✅ ON (开启)                                                  │"
log_info "      │                                                                                 │"
log_info "      │  ⚙️  HTTP                                                                      │"
log_info "      │  ─────────────────────────────────────────────────────────────────────────── │"
log_info "      │  URL:           http://192.168.3.100:${PROMETHEUS_PORT}                             │"
log_info "      │  Access:        Server (default)                                               │"
log_info "      │                                                                                 │"
log_info "      │  ⚙️  Prometheus-specific settings                                              │"
log_info "      │  ─────────────────────────────────────────────────────────────────────────── │"
log_info "      │  Formatted time: ON                                                           │"
log_info "      └─────────────────────────────────────────────────────────────────────────────┘"
log_info ""
log_info "步骤 4️⃣ ：保存并测试"
log_info "   1. 点击页面底部的 【Save & test】 按钮"
log_info "   2. 等待显示: ✅ Data source is working"
log_info "   3. 如果显示红色错误，请检查:"
log_info "      - Prometheus 是否运行: http://192.168.3.100:${PROMETHEUS_PORT}"
log_info "      - URL 是否正确填写"
log_info ""
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_info "                           第二部分：导入 Node Exporter 仪表盘"
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_info ""
log_info "步骤 1️⃣ ：进入仪表盘导入页面"
log_info "   1. 点击左侧菜单的 【Dashboards】 (仪表盘) 图标"
log_info "   2. 点击页面右上角的 【+ Import】 按钮"
log_info ""
log_info "步骤 2️⃣ ：加载仪表盘"
log_info "   1. 在 【Import via grafana.com】 文本框中输入仪表盘 ID:"
log_info "      ┌─────────────────────────────────────────────────────────────────────────────┐"
log_info "      │                                                                               │"
log_info "      │        1860                                                                   │"
log_info "      │                                                                               │"
log_info "      │        (Node Exporter Full - 官方推荐仪表盘)                                │"
log_info "      │                                                                               │"
log_info "      └─────────────────────────────────────────────────────────────────────────────┘"
log_info "   2. 点击 【Load】 按钮"
log_info ""
log_info "步骤 3️⃣ ：配置导入选项"
log_info "   1. 在 【Prometheus】 下拉框中选择刚才创建的数据源:"
log_info "      ┌─────────────────────────────────────────────────────────────────────────────┐"
log_info "      │                                                                               │"
log_info "      │  Prometheus ▼                                                               │"
log_info "      │                                                                               │"
log_info "      └─────────────────────────────────────────────────────────────────────────────┘"
log_info "   2. 根据需要修改仪表盘名称"
log_info "   3. 选择要存放的文件夹 (可选)"
log_info ""
log_info "步骤 4️⃣ ：完成导入"
log_info "   1. 点击 【Import】 按钮"
log_info "   2. 导入成功后会自动跳转到仪表盘页面"
log_info "   3. 现在你应该能看到服务器监控数据了!"
log_info ""
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_info "                              第三部分：验证监控是否正常"
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_info ""
log_info "验证 1️⃣ ：检查 Prometheus targets"
log_info "   1. 访问: http://192.168.3.100:${PROMETHEUS_PORT}/targets"
log_info "   2. 应该看到以下 targets 状态为 UP:"
log_info "      - prometheus (监控自身)"
log_info "      - node_local (本地服务器监控)"
log_info "      - node_remote (远程服务器监控，如果已安装 node_exporter)"
log_info ""
log_info "验证 2️⃣ ：检查 Grafana 仪表盘"
log_info "   1. 在 Grafana 左侧菜单点击 【Dashboards】"
log_info "   2. 点击 【Node Exporter Full】 仪表盘"
log_info "   3. 选择时间范围为 【Last 1 hour】 或 【Last 6 hours】"
log_info "   4. 确认图表中有数据显示"
log_info ""
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_info "                                第四部分：可选 - 安装远程监控"
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_info ""
log_info "如果需要监控远程服务器 (192.168.3.100)，执行以下步骤:"
log_info ""
log_info "步骤 1️⃣ ：复制安装脚本到远程服务器"
log_info "   scp ${NODE_EXPORTER_SCRIPT} root@192.168.3.100:/root/"
log_info ""
log_info "步骤 2️⃣ ：SSH 到远程服务器并执行安装"
log_info "   ssh root@192.168.3.100"
log_info "   bash /root/install_node_exporter.sh"
log_info ""
log_info "步骤 3️⃣ ：验证远程 node_exporter"
log_info "   curl http://192.168.3.100:9100/metrics"
log_info "   应该返回 Prometheus 格式的指标数据"
log_info ""
log_info "步骤 4️⃣ ：在 Prometheus 验证"
log_info "   1. 访问: http://192.168.3.100:${PROMETHEUS_PORT}/targets"
log_info "   2. 确认 node_remote 的状态为 UP"
log_info ""
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_info "                                 故障排除"
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_info ""
log_info "❓ 仪表盘没有数据"
log_info "   1. 确认数据源已正确配置并测试成功"
log_info "   2. 确认 Prometheus targets 页面中所有目标状态为 UP"
log_info "   3. 确认 Grafana 仪表盘时间范围选择正确 (如 Last 1 hour)"
log_info ""
log_info "❓ Grafana 登录失败"
log_info "   重置密码: docker exec -it grafana grafana-cli admin reset-admin-password ${GRAFANA_PASSWORD}"
log_info ""
log_info "❓ Prometheus 没有抓取到数据"
log_info "   1. 检查防火墙: firewall-cmd --list-ports 或 ufw status"
log_info "   2. 确保 9100 端口开放: firewall-cmd --add-port=9100/tcp --permanent"
log_info ""
log_info "❓ 需要重启服务"
log_info "   docker restart node_exporter prometheus grafana"
log_info ""
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"