#!/bin/bash
# ============================================================
#  Kubernetes 集群 - Master 节点初始化脚本
#  仅在 k8s-master (192.168.2.102) 上执行
#  前提: 已运行 setup-common.sh
# ============================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${BLUE}[STEP]${NC} $1"; }

echo "=========================================="
echo "  K8s Master 节点初始化"
echo "  节点: k8s-master (192.168.2.102)"
echo "=========================================="

# 检查 root
if [ "$EUID" -ne 0 ]; then
    log_error "请使用 root 用户运行此脚本"
    exit 1
fi

# 确认主机名
CURRENT_HOST=$(hostname)
if [ "$CURRENT_HOST" != "k8s-master" ]; then
    log_error "当前主机名是 '$CURRENT_HOST'，请在 k8s-master 节点上执行此脚本"
    log_error "请先运行 setup-common.sh 设置主机名"
    exit 1
fi

# ==========================================
# Step 1: kubeadm init
# ==========================================
log_step "[1/4] 初始化 Kubernetes 集群..."

# 检查是否已初始化
if kubectl cluster-info &> /dev/null 2>&1; then
    log_warn "检测到已有 Kubernetes 集群在运行"
    read -p "是否重置并重新初始化? (yes/no): " CONFIRM
    if [ "$CONFIRM" = "yes" ]; then
        log_info "正在重置集群..."
        kubeadm reset -f
        rm -rf /etc/kubernetes/manifests /var/lib/etcd
        rm -rf $HOME/.kube
        iptables -F && iptables -t nat -F && iptables -t mangle -F && iptables -X
        log_info "集群已重置"
    else
        log_info "跳过初始化"
        exit 0
    fi
fi

# 预拉取镜像 (使用阿里云国内镜像加速)
log_info "预拉取 Kubernetes 镜像..."
kubeadm config images pull --image-repository registry.cn-hangzhou.aliyuncs.com/google_containers

# 初始化集群
log_info "正在初始化集群..."
kubeadm init \
    --pod-network-cidr=10.244.0.0/16 \
    --service-cidr=10.96.0.0/12 \
    --image-repository registry.cn-hangzhou.aliyuncs.com/google_containers \
    --apiserver-advertise-address=192.168.2.102 \
    --node-name=k8s-master

log_info "集群初始化完成！"

# ==========================================
# Step 2: 配置 kubectl
# ==========================================
log_step "[2/4] 配置 kubectl..."

mkdir -p $HOME/.kube
cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
chown $(id -u):$(id -g) $HOME/.kube/config

# 配置 bash 补全（非 root 用户也可以手动执行）
if ! grep -q "kubectl completion bash" $HOME/.bashrc 2>/dev/null; then
    echo "source <(kubectl completion bash)" >> $HOME/.bashrc
fi

log_info "kubectl 配置完成"
log_info "验证: $(kubectl version --client -o short 2>/dev/null)"

# ==========================================
# Step 3: 保存 join 命令
# ==========================================
log_step "[3/4] 生成并保存 Worker 节点加入命令..."

# 创建 token 并生成 join 命令
JOIN_CMD_FILE="/root/k8s-join-command.sh"
kubeadm token create --print-join-command > "$JOIN_CMD_FILE"
chmod +x "$JOIN_CMD_FILE"

log_info "Join 命令已保存到: $JOIN_CMD_FILE"
log_info ""
log_info "Join 命令内容:"
echo "----------------------------------------"
cat "$JOIN_CMD_FILE"
echo "----------------------------------------"
log_warn "请将此命令复制到 Worker 节点执行，或将其内容加入到 setup-node.sh"

# ==========================================
# Step 4: 安装网络插件 (Calico)
# ==========================================
log_step "[4/4] 安装 Calico 网络插件..."

# 等待 API Server 就绪
log_info "等待 API Server 就绪..."
sleep 5

# 安装 Calico
kubectl apply -f https://raw.githubusercontent.com/projectcalico/calico/v3.26.4/manifests/calico.yaml

log_info "Calico 网络插件安装中..."

# 等待 Calico Pod 就绪
log_info "等待 Calico Pod 启动 (最多 120 秒)..."
ATTEMPTS=0
MAX_ATTEMPTS=24
while [ $ATTEMPTS -lt $MAX_ATTEMPTS ]; do
    READY_COUNT=$(kubectl get pods -n kube-system -l k8s-app=calico-node --field-selector=status.phase=Running 2>/dev/null | grep -c Running || true)
    if [ "$READY_COUNT" -ge 1 ]; then
        log_info "Calico Pod 已就绪"
        break
    fi
    sleep 5
    ATTEMPTS=$((ATTEMPTS+1))
    echo -n "."
done
echo ""

# 移除 Master 节点的污点（允许在 Master 上调度 Pod，可选）
log_info "移除 Master 节点污点，允许调度工作负载..."
kubectl taint nodes k8s-master node-role.kubernetes.io/control-plane:NoSchedule- 2>/dev/null || \
kubectl taint nodes k8s-master node-role.kubernetes.io/master:NoSchedule- 2>/dev/null || true

# ==========================================
# 验证集群状态
# ==========================================
echo ""
echo "=========================================="
echo -e "${GREEN}  Master 节点初始化完成！${NC}"
echo "=========================================="
echo ""
echo "集群节点状态:"
kubectl get nodes -o wide
echo ""
echo "系统 Pod 状态:"
kubectl get pods -n kube-system
echo ""
echo "=========================================="
echo -e "${YELLOW}  下一步: 在 Worker 节点执行加入操作${NC}"
echo "=========================================="
echo ""
log_info "Join 命令文件: /root/k8s-join-command.sh"
echo ""
echo "将 join 命令内容复制到 Worker 节点 (k8s-node1, k8s-node2) 后运行:"
echo "  bash setup-node.sh"
echo ""
echo "或者直接在 Worker 节点执行 join 命令:"
echo "  bash /root/k8s-join-command.sh  (需要先将文件从 Master 复制过去)"
echo ""
