#!/bin/bash
# ============================================================
#  containerd docker.io 镜像加速修复脚本
#  解决: Failed to pull image "xxx": dial tcp ... i/o timeout
#  在所有节点执行（master / node1 / node2）
#  使用: sudo bash fix-containerd-mirror.sh
# ============================================================

set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

CONTAINERD_CONFIG="/etc/containerd/config.toml"

echo "=========================================="
echo "  containerd docker.io 镜像加速"
echo "=========================================="
echo ""

[ "$EUID" -ne 0 ] && { log_error "请使用 sudo 运行"; exit 1; }
[ ! -f "$CONTAINERD_CONFIG" ] && { log_error "$CONTAINERD_CONFIG 不存在，请先运行 setup-common.sh"; exit 1; }

# 检查是否已配置
if grep -q 'endpoint.*mirror' "$CONTAINERD_CONFIG" 2>/dev/null; then
    log_info "已配置镜像加速，跳过"
    exit 0
fi

log_info "添加 docker.io 镜像加速..."
cp "$CONTAINERD_CONFIG" "${CONTAINERD_CONFIG}.bak.$(date +%Y%m%d_%H%M%S)"

cat >> "$CONTAINERD_CONFIG" <<'EOF'

# fix-containerd-mirror: docker.io mirror for China
# 如需阿里云个人加速器: https://cr.console.aliyun.com → 镜像加速器
[plugins."io.containerd.grpc.v1.cri".registry.mirrors."docker.io"]
  endpoint = ["https://docker.m.daocloud.io", "https://docker.mirrors.ustc.edu.cn"]
EOF

log_info "重启 containerd..."
systemctl restart containerd
sleep 2

if systemctl is-active --quiet containerd; then
    log_info "containerd 运行正常"
else
    log_error "containerd 未运行，请检查: journalctl -u containerd"
    exit 1
fi

echo ""
echo "=========================================="
echo -e "${GREEN}  镜像加速配置完成${NC}"
echo "=========================================="
echo ""
echo "  已配置镜像源:"
echo "    docker.m.daocloud.io"
echo "    docker.mirrors.ustc.edu.cn"
echo ""
echo "  如需使用阿里云个人加速器:"
echo "    登录 https://cr.console.aliyun.com/cn-hangzhou/instances/mirrors"
echo "    获取您的专属加速器地址，替换 $CONTAINERD_CONFIG 中的 endpoint"
echo ""
echo "  在 worker 节点也运行此脚本后，重建拉取失败的 Pod:"
echo "    kubectl delete pod <pod-name>"
echo ""
