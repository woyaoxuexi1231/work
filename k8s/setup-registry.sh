#!/bin/bash
# ============================================================
#  本地 Docker Registry 搭建
#  在 k8s-master 上执行
#  使用: sudo bash setup-registry.sh
# ============================================================

set -e

REGISTRY_PORT="${REGISTRY_PORT:-5000}"
REGISTRY_IP="${REGISTRY_IP:-192.168.3.100}"
REGISTRY="${REGISTRY_IP}:${REGISTRY_PORT}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

echo ""
echo "=========================================="
echo "  本地 Docker Registry 搭建"
echo "  地址: ${REGISTRY}"
echo "=========================================="
echo ""

[ "$EUID" -ne 0 ] && { log_error "请使用 sudo 运行"; exit 1; }

# 1. 启动 Registry
if docker ps --format '{{.Names}}' | grep -qx "registry"; then
    log_info "Registry 已在运行"
else
    docker rm -f registry 2>/dev/null || true
    docker run -d --name registry --restart=always \
        -p ${REGISTRY_PORT}:5000 -v /root/registry-data:/var/lib/registry registry:2
    log_info "Registry 启动成功"
fi

sleep 2
curl -s http://${REGISTRY}/v2/ &>/dev/null && log_info "Registry API 正常" || log_warn "Registry 可能未就绪"

# 2. 配置本机 containerd 信任 registry
if grep -q "${REGISTRY}" /etc/containerd/config.toml 2>/dev/null; then
    log_info "本机 containerd 已配置，跳过"
else
    cp /etc/containerd/config.toml /etc/containerd/config.toml.bak.$(date +%Y%m%d_%H%M%S)
    cat >> /etc/containerd/config.toml <<EOF

# Local registry
[plugins."io.containerd.grpc.v1.cri".registry.mirrors."${REGISTRY}"]
  endpoint = ["http://${REGISTRY}"]
[plugins."io.containerd.grpc.v1.cri".registry.configs."${REGISTRY}".tls]
  insecure_skip_verify = true
EOF
    systemctl restart containerd
    log_info "本机 containerd 配置完成"
fi

echo ""
echo "=========================================="
echo -e "${GREEN}  Registry 搭建完成${NC}"
echo "=========================================="
echo ""
echo "  ⚠️  k8s-node1 / k8s-node2 还需手动执行:"
echo ""
echo "    sudo tee -a /etc/containerd/config.toml <<'EOF'"
echo ""
echo "    [plugins.\"io.containerd.grpc.v1.cri\".registry.mirrors.\"${REGISTRY}\"]"
echo "      endpoint = [\"http://${REGISTRY}\"]"
echo "    [plugins.\"io.containerd.grpc.v1.cri\".registry.configs.\"${REGISTRY}\".tls]"
echo "      insecure_skip_verify = true"
echo "    EOF"
echo ""
echo "    sudo systemctl restart containerd"
echo ""
echo "  ⚠️  Windows Docker Desktop → Settings → Docker Engine:"
echo "    { \"insecure-registries\": [\"${REGISTRY}\"] }"
echo "    → Apply & Restart"
echo ""
