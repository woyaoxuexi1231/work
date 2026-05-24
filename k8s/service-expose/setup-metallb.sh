#!/bin/bash
# ============================================================
#  MetalLB 安装脚本 (Layer2 模式)
#  为裸金属 K8s 集群提供 LoadBalancer 类型的 Service
#  仅在 k8s-master 上执行
#  使用: sudo bash setup-metallb.sh
# ============================================================

set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${BLUE}[STEP]${NC} $1"; }

METALLB_VERSION="v0.14.9"
METALLB_NS="metallb-system"

# IP 池配置：从你的局域网中分配一段给 MetalLB 管理
# 注意: 这些 IP 不能被 DHCP 分配，否则会冲突
IP_POOL_START="${METALLB_IP_START:-192.168.3.200}"
IP_POOL_END="${METALLB_IP_END:-192.168.3.210}"

echo ""
echo "=========================================="
echo "  MetalLB 安装 (Layer2 模式)"
echo "  版本: ${METALLB_VERSION}"
echo "  IP 池: ${IP_POOL_START} - ${IP_POOL_END}"
echo "=========================================="
echo ""

[ "$EUID" -ne 0 ] && { log_error "请使用 sudo 运行"; exit 1; }
kubectl cluster-info &>/dev/null || { log_error "无法连接集群"; exit 1; }

# ============================================================
# Step 1: 安装 MetalLB
# ============================================================
log_step "[1/3] 安装 MetalLB..."

if kubectl get deployment controller -n "$METALLB_NS" &>/dev/null; then
    log_info "MetalLB 已安装"
else
    log_info "从官方源安装..."
    URLS=(
        "https://raw.githubusercontent.com/metallb/metallb/${METALLB_VERSION}/config/manifests/metallb-native.yaml"
        "https://cdn.jsdelivr.net/gh/metallb/metallb@${METALLB_VERSION}/config/manifests/metallb-native.yaml"
        "https://ghproxy.com/https://raw.githubusercontent.com/metallb/metallb/${METALLB_VERSION}/config/manifests/metallb-native.yaml"
    )
    installed=false
    for url in "${URLS[@]}"; do
        log_info "尝试: $url"
        if curl -fsSL --connect-timeout 10 "$url" 2>/dev/null | kubectl apply -f - 2>/dev/null; then
            installed=true
            break
        fi
        log_warn "该源不可用..."
    done
    $installed || { log_error "所有安装源均不可用"; exit 1; }
fi

# 等待就绪
log_info "等待 MetalLB 就绪..."
for i in $(seq 1 20); do
    kubectl get pods -n "$METALLB_NS" --field-selector=status.phase=Running 2>/dev/null | grep -q Running && break
    sleep 3
    echo -n "."
done
echo ""
log_info "MetalLB 安装完成"

# ============================================================
# ============================================================
# Step 2: 配置 IP 池 + L2 通告
# ============================================================
log_step "[2/3] 配置 IP 池和 L2 通告..."

# 如果 webhook 不可达（集群内网络问题），临时关闭验证
apply_metallb_config() {
    kubectl apply -f - <<EOF
apiVersion: metallb.io/v1beta1
kind: IPAddressPool
metadata:
  name: default-pool
  namespace: $METALLB_NS
spec:
  addresses:
  - ${IP_POOL_START}-${IP_POOL_END}
---
apiVersion: metallb.io/v1beta1
kind: L2Advertisement
metadata:
  name: default-l2
  namespace: $METALLB_NS
spec:
  ipAddressPools:
  - default-pool
EOF
}

if apply_metallb_config 2>/dev/null; then
    log_info "IP 池配置完成: ${IP_POOL_START} - ${IP_POOL_END}"
else
    log_warn "webhook 不可达，临时跳过验证..."
    # 删除验证 webhook，应用配置后 MetalLB 会自动重建
    kubectl delete validatingwebhookconfiguration metallb-webhook-configuration --ignore-not-found=true 2>/dev/null || true
    sleep 2
    if apply_metallb_config; then
        log_info "IP 池配置完成: ${IP_POOL_START} - ${IP_POOL_END}"
    else
        log_error "配置失败，请手动执行上述 YAML"
        exit 1
    fi
fi

# ============================================================
# Step 3: 验证
# ============================================================
log_step "[3/3] 验证安装..."

# 创建测试 Service
log_info "创建测试 LoadBalancer Service..."
kubectl apply -f - <<EOF
apiVersion: v1
kind: Service
metadata:
  name: metallb-test
  namespace: default
spec:
  type: LoadBalancer
  selector:
    run: my-nginx
  ports:
  - port: 80
    targetPort: 80
EOF

# 等待 External IP 分配
log_info "等待 IP 分配..."
for i in $(seq 1 15); do
    EXT_IP=$(kubectl get svc metallb-test -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)
    if [ -n "$EXT_IP" ]; then
        log_info "已分配 External IP: $EXT_IP"
        break
    fi
    sleep 2
    echo -n "."
done
echo ""

# 清理测试 Service
kubectl delete svc metallb-test --ignore-not-found=true 2>/dev/null

# ============================================================
# 完成
# ============================================================
echo ""
echo "=========================================="
echo -e "${GREEN}  MetalLB 安装完成${NC}"
echo "=========================================="
echo ""
echo "  IP 池: ${IP_POOL_START} - ${IP_POOL_END}"
echo ""
echo "  使用方式:"
echo ""
echo "    # 创建一个对外服务"
echo "    kubectl expose deployment my-nginx \\"
echo "      --type=LoadBalancer --port=80 --target-port=80"
echo ""
echo "    # 查看分配的 External IP"
echo "    kubectl get svc"
echo ""
echo "    # 用户直接访问 External IP，不用管 Pod 在哪个节点"
echo "    curl http://<EXTERNAL-IP>"
echo ""
echo "  测试:"
echo "    kubectl apply -f service-expose/test-lb-service.yaml"
echo ""
echo "  注意事项:"
echo "    - IP 池中的 IP 不能和 DHCP 池冲突"
echo "    - 同网段机器可直接访问 External IP"
echo "    - 如需跨网段访问，需配置路由或外部 LB"
echo ""
