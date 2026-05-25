#!/usr/bin/env bash
# ========================================================================================
# Node Exporter 卸载脚本
# 清理旧版 node_exporter（systemd 服务 + 二进制 + 数据目录）
# 使用: sudo bash uninstall_node_exporter.sh
# ========================================================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

echo ""
echo "=========================================="
echo "  Node Exporter 卸载"
echo "=========================================="
echo ""

[ "$EUID" -ne 0 ] && { log_error "请使用 sudo 运行"; exit 1; }

# 1. 停止并禁用 systemd 服务
log_info "停止 node_exporter 服务..."
if systemctl is-active --quiet node_exporter 2>/dev/null; then
    systemctl stop node_exporter
    log_info "  服务已停止"
else
    log_info "  服务未运行，跳过"
fi

if [ -f /etc/systemd/system/node_exporter.service ]; then
    systemctl disable node_exporter 2>/dev/null || true
    rm -f /etc/systemd/system/node_exporter.service
    systemctl daemon-reload
    log_info "  服务文件已删除"
else
    log_info "  服务文件不存在，跳过"
fi

# 2. 删除二进制文件
log_info "清理 node_exporter 二进制..."
if [ -f /usr/local/bin/node_exporter ]; then
    rm -f /usr/local/bin/node_exporter
    log_info "  /usr/local/bin/node_exporter 已删除"
else
    log_info "  二进制文件不存在，跳过"
fi

# 3. 删除数据目录
log_info "清理数据目录..."
if [ -d /opt/node_exporter ]; then
    rm -rf /opt/node_exporter
    log_info "  /opt/node_exporter 已删除"
else
    log_info "  数据目录不存在，跳过"
fi

echo ""
echo "=========================================="
echo -e "${GREEN}  Node Exporter 卸载完成${NC}"
echo "=========================================="
echo ""
echo "  如需要重新安装，运行:"
echo "    sudo bash component_install_prometheus_grafana.sh"
echo ""
