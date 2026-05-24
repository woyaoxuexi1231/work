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

DASHBOARD_VERSION="v2.7.0"
METRICS_SERVER_VERSION="v0.7.2"
DASHBOARD_NS="kubernetes-dashboard"
NODE_PORT="30443"

MASTER_IP="${K8S_MASTER_IP:-$(hostname -I 2>/dev/null | awk '{print $1}')}"
TOKEN_FILE="/root/k8s-dashboard-token.txt"

echo ""
echo "=========================================="
echo "  Kubernetes Dashboard 安装"
echo "=========================================="
echo ""

[ "$EUID" -ne 0 ] && { log_error "请使用 sudo 运行"; exit 1; }
kubectl cluster-info &>/dev/null || { log_error "无法连接集群"; exit 1; }

# ============================================================
# Step 1: 安装 Metrics Server
# ============================================================
log_step "[1/3] 安装 Metrics Server..."

if kubectl get deployment metrics-server -n kube-system &>/dev/null; then
    log_info "已安装，跳过"
else
    kubectl apply -f "https://github.com/kubernetes-sigs/metrics-server/releases/download/${METRICS_SERVER_VERSION}/components.yaml" 2>/dev/null || {
        log_warn "在线安装失败，换用本地配置..."
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
        kubectl apply -f /tmp/metrics-server.yaml || { log_error "Metrics Server 安装失败"; exit 1; }
        rm -f /tmp/metrics-server.yaml
    }

    # 等待就绪
    log_info "等待 Metrics Server Pod 就绪..."
    for i in $(seq 1 30); do
        kubectl get pods -n kube-system -l k8s-app=metrics-server --field-selector=status.phase=Running 2>/dev/null | grep -q Running && break
        sleep 4
        echo -n "."
    done
    echo ""
fi
log_info "Metrics Server 安装完成"

# ============================================================
# Step 2: 安装 Dashboard + NodePort
# ============================================================
log_step "[2/3] 安装 Dashboard (NodePort :${NODE_PORT})..."

if kubectl get deployment kubernetes-dashboard -n "$DASHBOARD_NS" &>/dev/null; then
    log_info "Dashboard 已安装，跳过"
else
    # 多个镜像源，依次尝试（解决 raw.githubusercontent.com 被墙问题）
    DASHBOARD_URLS=(
        "https://raw.githubusercontent.com/kubernetes/dashboard/${DASHBOARD_VERSION}/aio/deploy/recommended.yaml"
        "https://cdn.jsdelivr.net/gh/kubernetes/dashboard@${DASHBOARD_VERSION}/aio/deploy/recommended.yaml"
        "https://ghproxy.com/https://raw.githubusercontent.com/kubernetes/dashboard/${DASHBOARD_VERSION}/aio/deploy/recommended.yaml"
    )
    installed=false
    for url in "${DASHBOARD_URLS[@]}"; do
        log_info "尝试: $url"
        if kubectl apply -f "$url" --validate=false 2>/dev/null; then
            installed=true
            break
        fi
        log_warn "该源不可用，尝试下一个..."
    done
    $installed || { log_error "所有镜像源均不可用，请手动下载: ${DASHBOARD_URLS[0]}"; exit 1; }

    log_info "等待 Dashboard Pod 就绪..."
    for i in $(seq 1 30); do
        kubectl get pods -n "$DASHBOARD_NS" -l k8s-app=kubernetes-dashboard --field-selector=status.phase=Running 2>/dev/null | grep -q Running && break
        sleep 4
        echo -n "."
    done
    echo ""
fi

# 配置 NodePort
kubectl patch svc kubernetes-dashboard -n "$DASHBOARD_NS" \
    -p "{\"spec\":{\"type\":\"NodePort\",\"ports\":[{\"port\":443,\"targetPort\":8443,\"nodePort\":${NODE_PORT}}]}}" 2>/dev/null || true
log_info "Dashboard + NodePort 配置完成"

# ============================================================
# Step 3: 创建管理员账户 + Token
# ============================================================
log_step "[3/3] 创建管理员账户..."

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
if [ -z "$TOKEN" ]; then
    log_warn "Token 生成失败，请手动执行: kubectl -n $DASHBOARD_NS create token admin-user --duration=87600h"
else
    echo "$TOKEN" > "$TOKEN_FILE"
    chmod 600 "$TOKEN_FILE"
fi

# ============================================================
# 完成
# ============================================================
echo ""
echo "=========================================="
echo -e "${GREEN}  ✅ Dashboard 安装完成${NC}"
echo "=========================================="
echo ""
echo "  地址: https://${MASTER_IP}:${NODE_PORT}"
echo "  Token 文件: $TOKEN_FILE"
echo ""
echo "  Token:"
echo "  ----------------------------------------"
[ -n "$TOKEN" ] && echo "  $TOKEN" || echo "  (生成失败，手动执行: kubectl -n $DASHBOARD_NS create token admin-user --duration=87600h)"
echo "  ----------------------------------------"
echo ""
echo "  浏览器打开 https://${MASTER_IP}:${NODE_PORT}"
echo "  → 忽略证书警告 → 粘贴 Token 登录"
echo ""
echo "  重新生成 Token: sudo bash $0"
echo ""
