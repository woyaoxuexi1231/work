#!/bin/bash
# ============================================================
#  Kubernetes Dashboard 安装脚本
#  仅在 k8s-master 上执行，前提: 集群已初始化
#  使用: sudo bash setup-dashboard.sh
# ============================================================

set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${BLUE}[STEP]${NC} $1"; }

DASHBOARD_NS="kubernetes-dashboard"
NODE_PORT="30443"
CONTAINERD_CONFIG="/etc/containerd/config.toml"

# 获取 Master IP（优先级: 环境变量 > /etc/hosts > 默认路由 > 兜底）
detect_master_ip() {
    [ -n "${K8S_MASTER_IP:-}" ] && { echo "$K8S_MASTER_IP"; return; }
    getent hosts k8s-master 2>/dev/null | awk '{print $1; exit}' && return
    ip route get 1.1.1.1 2>/dev/null | awk '{for (i=1;i<=NF;i++) if($i=="src"){print $(i+1);exit}}' && return
    hostname -I 2>/dev/null | awk '{print $1}'
}
MASTER_IP="$(detect_master_ip)"
TOKEN_FILE="/root/k8s-dashboard-token.txt"

echo ""
echo "=========================================="
echo "  Kubernetes Dashboard 安装"
echo "=========================================="
echo ""

[ "$EUID" -ne 0 ] && { log_error "请使用 sudo 运行"; exit 1; }
kubectl cluster-info &>/dev/null || { log_error "无法连接集群"; exit 1; }

# ============================================================
# Step 0: 配置 containerd 镜像加速 (解决国内拉不到 docker.io)
# ============================================================
log_step "[0/4] 配置 containerd 镜像加速..."

if [ -f "$CONTAINERD_CONFIG" ]; then
    # 用唯一标记检测是否已配置
    if grep -q 'endpoint.*docker.m.daocloud' "$CONTAINERD_CONFIG" 2>/dev/null; then
        log_info "containerd 已配置 docker.io 镜像加速"
    else
        log_info "添加 docker.io 镜像加速配置..."
        cp "$CONTAINERD_CONFIG" "${CONTAINERD_CONFIG}.bak.$(date +%Y%m%d_%H%M%S)"

        cat >> "$CONTAINERD_CONFIG" <<'EOF'

# setup-dashboard.sh: docker.io mirror for China
[plugins."io.containerd.grpc.v1.cri".registry.mirrors."docker.io"]
  endpoint = ["https://docker.m.daocloud.io", "https://docker.mirrors.ustc.edu.cn"]
EOF
        systemctl restart containerd
        sleep 3
        log_info "containerd 镜像加速配置完成"
    fi
fi

# ============================================================
# Step 1: 安装 Metrics Server（始终用本地配置 + 阿里云镜像）
# ============================================================
log_step "[1/4] 安装 Metrics Server..."

if kubectl get deployment metrics-server -n kube-system &>/dev/null; then
    # 检查是否是 ImagePullBackOff（之前从官方源装的可能拉不到镜像）
    ms_status=$(kubectl get pods -n kube-system -l k8s-app=metrics-server \
        -o jsonpath='{.items[0].status.containerStatuses[0].state.waiting.reason}' 2>/dev/null || true)
    if [ "$ms_status" = "ImagePullBackOff" ] || [ "$ms_status" = "ErrImagePull" ]; then
        log_warn "Metrics Server 镜像拉取失败，重新安装（使用阿里云镜像）..."
        kubectl delete deployment metrics-server -n kube-system --ignore-not-found=true 2>/dev/null
        kubectl delete -f /tmp/metrics-server.yaml --ignore-not-found=true 2>/dev/null
    else
        log_info "Metrics Server 已安装且正常，跳过"
    fi
fi

if ! kubectl get deployment metrics-server -n kube-system &>/dev/null; then
    cat > /tmp/metrics-server.yaml <<'YAML'
apiVersion: v1
kind: ServiceAccount
metadata:
  labels: {k8s-app: metrics-server}
  name: metrics-server
  namespace: kube-system
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  labels: {k8s-app: metrics-server, rbac.authorization.k8s.io/aggregate-to-admin: "true", rbac.authorization.k8s.io/aggregate-to-edit: "true", rbac.authorization.k8s.io/aggregate-to-view: "true"}
  name: system:aggregated-metrics-reader
rules:
- apiGroups: [metrics.k8s.io]
  resources: [pods, nodes]
  verbs: [get, list, watch]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  labels: {k8s-app: metrics-server}
  name: system:metrics-server
rules:
- apiGroups: [""]
  resources: [nodes/metrics]
  verbs: [get]
- apiGroups: [""]
  resources: [pods, nodes]
  verbs: [get, list, watch]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  labels: {k8s-app: metrics-server}
  name: metrics-server-auth-reader
  namespace: kube-system
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: extension-apiserver-authentication-reader
subjects:
- kind: ServiceAccount
  name: metrics-server
  namespace: kube-system
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  labels: {k8s-app: metrics-server}
  name: metrics-server:system:auth-delegator
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: system:auth-delegator
subjects:
- kind: ServiceAccount
  name: metrics-server
  namespace: kube-system
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  labels: {k8s-app: metrics-server}
  name: system:metrics-server
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: system:metrics-server
subjects:
- kind: ServiceAccount
  name: metrics-server
  namespace: kube-system
---
apiVersion: v1
kind: Service
metadata:
  labels: {k8s-app: metrics-server}
  name: metrics-server
  namespace: kube-system
spec:
  ports: [{name: https, port: 443, protocol: TCP, targetPort: https}]
  selector: {k8s-app: metrics-server}
---
apiVersion: apps/v1
kind: Deployment
metadata:
  labels: {k8s-app: metrics-server}
  name: metrics-server
  namespace: kube-system
spec:
  replicas: 1
  selector:
    matchLabels: {k8s-app: metrics-server}
  template:
    metadata:
      labels: {k8s-app: metrics-server}
    spec:
      containers:
      - args:
        - --cert-dir=/tmp
        - --secure-port=10250
        - --kubelet-preferred-address-types=InternalIP,ExternalIP,Hostname
        - --kubelet-use-node-status-port
        - --metric-resolution=15s
        - --kubelet-insecure-tls
        image: registry.cn-hangzhou.aliyuncs.com/google_containers/metrics-server:v0.7.2
        imagePullPolicy: IfNotPresent
        name: metrics-server
        ports: [{containerPort: 10250, name: https, protocol: TCP}]
        resources: {requests: {cpu: 100m, memory: 200Mi}}
      nodeSelector: {kubernetes.io/os: linux}
      serviceAccountName: metrics-server
---
apiVersion: apiregistration.k8s.io/v1
kind: APIService
metadata:
  labels: {k8s-app: metrics-server}
  name: v1beta1.metrics.k8s.io
spec:
  group: metrics.k8s.io
  groupPriorityMinimum: 100
  insecureSkipTLSVerify: true
  service: {name: metrics-server, namespace: kube-system}
  version: v1beta1
  versionPriority: 100
YAML
    kubectl apply -f /tmp/metrics-server.yaml
    rm -f /tmp/metrics-server.yaml
fi

# 等待就绪
log_info "等待 Metrics Server Pod 就绪..."
for i in $(seq 1 30); do
    kubectl get pods -n kube-system -l k8s-app=metrics-server --field-selector=status.phase=Running 2>/dev/null | grep -q Running && break
    sleep 4
    echo -n "."
done
echo ""
log_info "Metrics Server 完成"

# ============================================================
# Step 2: 安装 Dashboard
# ============================================================
log_step "[2/4] 安装 Dashboard..."

if kubectl get deployment kubernetes-dashboard -n "$DASHBOARD_NS" &>/dev/null; then
    log_info "Dashboard 已安装，重建以使用镜像加速..."
    kubectl delete deployment kubernetes-dashboard dashboard-metrics-scraper -n "$DASHBOARD_NS" --ignore-not-found=true 2>/dev/null
fi

# 下载 Dashboard YAML，然后尝试多个国内镜像源替换镜像
DASHBOARD_URL="https://raw.githubusercontent.com/kubernetes/dashboard/v2.7.0/aio/deploy/recommended.yaml"
DASHBOARD_YAML_ORIG="/tmp/k8s-dashboard-orig.yaml"
DASHBOARD_YAML="/tmp/k8s-dashboard-recommended.yaml"

log_info "下载 Dashboard 部署文件..."
for url in \
    "https://raw.githubusercontent.com/kubernetes/dashboard/v2.7.0/aio/deploy/recommended.yaml" \
    "https://cdn.jsdelivr.net/gh/kubernetes/dashboard@v2.7.0/aio/deploy/recommended.yaml" \
    "https://ghproxy.com/https://raw.githubusercontent.com/kubernetes/dashboard/v2.7.0/aio/deploy/recommended.yaml"; do
    if curl -fsSL --connect-timeout 10 "$url" -o "$DASHBOARD_YAML_ORIG" 2>/dev/null; then
        log_info "下载成功: $url"
        break
    fi
done
[ -f "$DASHBOARD_YAML_ORIG" ] || { log_error "无法下载 Dashboard YAML，请检查网络"; exit 1; }

# 国内可用镜像源列表（格式: 镜像前缀|dashboard镜像名|scraper镜像名）
MIRROR_LIST=(
    "swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io|kubernetesui/dashboard:v2.7.0|kubernetesui/metrics-scraper:v1.0.9"
    "docker.1panel.live|kubernetesui/dashboard:v2.7.0|kubernetesui/metrics-scraper:v1.0.9"
    "dockerhub.icu|kubernetesui/dashboard:v2.7.0|kubernetesui/metrics-scraper:v1.0.9"
    "registry.cn-hangzhou.aliyuncs.com/google_containers|kubernetes-dashboard:v2.7.0|metrics-scraper:v1.0.9"
)
img_ok=false
for entry in "${MIRROR_LIST[@]}"; do
    IFS='|' read -r prefix dash_img scraper_img <<< "$entry"
    test_img="${prefix}/${dash_img}"
    log_info "尝试镜像: $test_img"
    # 用 crictl 测试能否拉取
    if crictl pull "$test_img" &>/dev/null; then
        log_info "镜像可用: $prefix"
        # 替换 YAML 中的镜像地址
        cp "$DASHBOARD_YAML_ORIG" "$DASHBOARD_YAML"
        sed -i "s|kubernetesui/dashboard:v2.7.0|${prefix}/kubernetesui/dashboard:v2.7.0|g" "$DASHBOARD_YAML"
        sed -i "s|kubernetesui/metrics-scraper:v1.0.9|${prefix}/kubernetesui/metrics-scraper:v1.0.9|g" "$DASHBOARD_YAML"
        # 阿里云的镜像名不含 kubernetesui/ 前缀
        if [[ "$prefix" == *"aliyuncs"* ]]; then
            cp "$DASHBOARD_YAML_ORIG" "$DASHBOARD_YAML"
            sed -i "s|kubernetesui/dashboard:v2.7.0|${prefix}/kubernetes-dashboard:v2.7.0|g" "$DASHBOARD_YAML"
            sed -i "s|kubernetesui/metrics-scraper:v1.0.9|${prefix}/metrics-scraper:v1.0.9|g" "$DASHBOARD_YAML"
        fi
        img_ok=true
        break
    fi
    log_warn "镜像不可用: $prefix"
done

if ! $img_ok; then
    log_error "所有国内镜像源均不可用！"
    log_info "请手动在服务器上拉取镜像后重新运行脚本"
    rm -f "$DASHBOARD_YAML_ORIG"
    exit 1
fi

log_info "应用 Dashboard 配置..."
kubectl apply --validate=false -f "$DASHBOARD_YAML"
rm -f "$DASHBOARD_YAML" "$DASHBOARD_YAML_ORIG"

# ============================================================
# Step 3: 配置 NodePort
# ============================================================
log_step "[3/4] 配置 NodePort..."

kubectl patch svc kubernetes-dashboard -n "$DASHBOARD_NS" \
    -p "{\"spec\":{\"type\":\"NodePort\",\"ports\":[{\"port\":443,\"targetPort\":8443,\"nodePort\":${NODE_PORT}}]}}" 2>/dev/null || true

# ============================================================
# Step 4: 创建管理员 + Token
# ============================================================
log_step "[4/4] 创建管理员账户..."

if ! kubectl get serviceaccount admin-user -n "$DASHBOARD_NS" &>/dev/null; then
    kubectl apply -f - <<EOF
apiVersion: v1
kind: ServiceAccount
metadata:
  name: admin-user
  namespace: $DASHBOARD_NS
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: admin-user
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: cluster-admin
subjects:
- kind: ServiceAccount
  name: admin-user
  namespace: $DASHBOARD_NS
EOF
fi

TOKEN=$(kubectl create token admin-user -n "$DASHBOARD_NS" --duration=87600h 2>/dev/null || true)
[ -n "$TOKEN" ] && echo "$TOKEN" > "$TOKEN_FILE" && chmod 600 "$TOKEN_FILE"

# ============================================================
# 等待 Pod 就绪
# ============================================================
log_info "等待 Dashboard Pod 就绪..."
for i in $(seq 1 30); do
    ready=$(kubectl get pods -n "$DASHBOARD_NS" \
        --field-selector=status.phase=Running 2>/dev/null | grep -c Running || true)
    if [ "$ready" -ge 2 ]; then
        log_info "Dashboard Pod 已就绪"
        break
    fi
    sleep 4
    echo -n "."
done
echo ""

# 检查 Pod 状态
log_info "Dashboard Pod 状态:"
kubectl get pods -n "$DASHBOARD_NS"

# ============================================================
# 完成
# ============================================================
echo ""
echo "=========================================="
echo -e "${GREEN}  Dashboard 安装完成${NC}"
echo "=========================================="
echo ""
echo "  访问: https://${MASTER_IP}:${NODE_PORT}"
echo ""
echo "  Token:"
echo "  ----------------------------------------"
[ -n "$TOKEN" ] && echo "  $TOKEN" || echo "  (重新生成: sudo bash $0)"
echo "  ----------------------------------------"
echo ""
echo "  浏览器打开 → 忽略证书警告 → 粘贴 Token 登录"
echo ""
