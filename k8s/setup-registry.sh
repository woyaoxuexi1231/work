#!/bin/bash
# ============================================================
#  本地 Docker Registry 搭建脚本
#  在 k8s-master 上执行，所有 K8s 节点都能拉取镜像
#  使用: sudo bash setup-registry.sh
# ============================================================

set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${BLUE}[STEP]${NC} $1"; }

REGISTRY_PORT="${REGISTRY_PORT:-5000}"
REGISTRY_IP="${REGISTRY_IP:-192.168.3.100}"
REGISTRY="${REGISTRY_IP}:${REGISTRY_PORT}"
CONTAINERD_CONFIG="/etc/containerd/config.toml"

echo ""
echo "=========================================="
echo "  本地 Docker Registry 搭建"
echo "  地址: ${REGISTRY}"
echo "=========================================="
echo ""

[ "$EUID" -ne 0 ] && { log_error "请使用 sudo 运行"; exit 1; }
kubectl cluster-info &>/dev/null || { log_error "无法连接集群（本脚本在 k8s-master 上执行）"; exit 1; }

# ============================================================
# Step 1: 在 k8s-master 上启动 Registry
# ============================================================
log_step "[1/3] 启动 Registry 容器..."

if docker ps --format '{{.Names}}' | grep -qx "registry"; then
    log_info "Registry 已在运行"
else
    docker rm -f registry 2>/dev/null || true
    docker run -d \
        --name registry \
        --restart=always \
        -p ${REGISTRY_PORT}:5000 \
        -v /root/registry-data:/var/lib/registry \
        registry:2
    log_info "Registry 启动成功: ${REGISTRY}"
fi

# 验证
sleep 2
if curl -s http://${REGISTRY}/v2/ &>/dev/null; then
    log_info "Registry API 正常响应"
else
    log_warn "Registry 可能未完全就绪，稍后验证"
fi

# ============================================================
# Step 2: 配置所有节点的 containerd 信任本地 Registry
# ============================================================
log_step "[2/3] 配置 containerd 信任本地 Registry..."

configure_node() {
    local node_ip="$1"
    local node_name="$2"
    local is_local="${3:-false}"

    log_info "配置节点: ${node_name} (${node_ip})"

    if $is_local; then
        # 本机直接操作
        if grep -q "${REGISTRY}" "${CONTAINERD_CONFIG}" 2>/dev/null; then
            log_info "  ${node_name} 已配置，跳过"
            return
        fi
        cp "${CONTAINERD_CONFIG}" "${CONTAINERD_CONFIG}.bak.$(date +%Y%m%d_%H%M%S)"
        cat >> "${CONTAINERD_CONFIG}" <<EOF

# Local registry
[plugins."io.containerd.grpc.v1.cri".registry.mirrors."${REGISTRY}"]
  endpoint = ["http://${REGISTRY}"]
[plugins."io.containerd.grpc.v1.cri".registry.configs."${REGISTRY}".tls]
  insecure_skip_verify = true
EOF
        systemctl restart containerd
        sleep 2
        log_info "  ${node_name} 配置完成"
    else
        # 远程节点通过 SSH 操作
        if ssh root@${node_ip} "grep -q '${REGISTRY}' ${CONTAINERD_CONFIG} 2>/dev/null" 2>/dev/null; then
            log_info "  ${node_name} 已配置，跳过"
            return
        fi
        ssh root@${node_ip} "cp ${CONTAINERD_CONFIG} ${CONTAINERD_CONFIG}.bak.\$(date +%Y%m%d_%H%M%S)" 2>/dev/null || true
        ssh root@${node_ip} "tee -a ${CONTAINERD_CONFIG} > /dev/null <<'EOF'

# Local registry
[plugins.\"io.containerd.grpc.v1.cri\".registry.mirrors.\"${REGISTRY}\"]
  endpoint = [\"http://${REGISTRY}\"]
[plugins.\"io.containerd.grpc.v1.cri\".registry.configs.\"${REGISTRY}\".tls]
  insecure_skip_verify = true
EOF" 2>/dev/null || {
            log_warn "  ${node_name} SSH 失败，请手动配置"
            return
        }
        ssh root@${node_ip} "systemctl restart containerd" 2>/dev/null
        log_info "  ${node_name} 配置完成"
    fi
}

# 配置本机（master）
configure_node "${REGISTRY_IP}" "k8s-master" true

# 配置 worker 节点
for node_name in k8s-node1 k8s-node2; do
    node_ip=$(getent hosts "${node_name}" 2>/dev/null | awk '{print $1}' || true)
    if [ -n "$node_ip" ]; then
        configure_node "$node_ip" "$node_name" false
    else
        log_warn "无法解析 ${node_name}，跳过（可能未在 /etc/hosts 中配置）"
    fi
done

# ============================================================
# Step 3: 配置 Windows 端 Docker 信任本地 Registry（提示）
# ============================================================
log_step "[3/3] Registry 搭建完成"

echo ""
echo "=========================================="
echo -e "${GREEN}  本地 Registry 搭建完成${NC}"
echo "=========================================="
echo ""
echo "  Registry 地址: ${REGISTRY}"
echo "  数据存储:      /root/registry-data/"
echo ""
echo "  Windows 上使用方式:"
echo ""
echo "    # 构建镜像时直接打上 registry 地址的 tag"
echo "    docker build -t ${REGISTRY}/poker-tracker:1.0.0 ."
echo ""
echo "    # 或者给已有镜像打 tag"
echo "    docker tag poker-tracker:1.0.0 ${REGISTRY}/poker-tracker:1.0.0"
echo ""
echo "    # 推送到本地 registry"
echo "    docker push ${REGISTRY}/poker-tracker:1.0.0"
echo ""
echo "  K8s YAML 中的镜像名改为:"
echo "    image: ${REGISTRY}/poker-tracker:1.0.0"
echo "    imagePullPolicy: Always"
echo ""
echo "  ⚠️  Windows Docker 默认只信任 HTTPS registry。"
echo "    需要在 Docker Desktop 设置中:"
echo "    Settings → Docker Engine → 添加:"
echo "    {"
echo "      \"insecure-registries\": [\"${REGISTRY}\"]"
echo "    }"
echo "    → Apply & Restart"
echo ""
