#!/bin/bash
# ============================================================
#  Kubernetes 集群 - Master 节点初始化脚本
#  仅在 k8s-master (192.168.3.100) 上执行
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
echo "  节点: k8s-master (192.168.3.100)"
echo "=========================================="

# 检查 root 权限 (Ubuntu 使用 sudo 运行)
if [ "$EUID" -ne 0 ]; then
    log_error "请使用 sudo 运行此脚本: sudo bash setup-master.sh"
    exit 1
fi

# 检测真实的操作用户 (sudo 场景下 $SUDO_USER 非空)
REAL_USER="${SUDO_USER:-$(logname 2>/dev/null || echo '')}"
if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
    REAL_HOME=$(eval echo ~$REAL_USER)
    log_info "真实用户: $REAL_USER, HOME: $REAL_HOME"
else
    REAL_USER="root"
    REAL_HOME="/root"
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

# --- 情况1: 集群已经正常运行 ---
if kubectl cluster-info &> /dev/null; then
    log_warn "检测到已有 Kubernetes 集群正在运行"
    kubectl get nodes -o wide 2>/dev/null || true
    log_warn ""
    read -p "是否需要重置并重新初始化? 这会删除所有数据 (y/n): " CONFIRM
    if [[ "$CONFIRM" =~ ^[Yy] ]]; then
        log_info "正在重置集群..."
        kubeadm reset -f 2>/dev/null || true
        systemctl stop kubelet 2>/dev/null || true
        rm -rf /etc/kubernetes/manifests /var/lib/etcd /var/lib/kubelet
        rm -rf /root/.kube "$REAL_HOME/.kube" 2>/dev/null || true
        iptables -F && iptables -t nat -F && iptables -t mangle -F && iptables -X
        log_info "集群已重置"
    else
        log_info "集群保持不动，退出"
        exit 0
    fi

# --- 情况2: 没有运行的集群，但上次 init 失败残留了文件 ---
elif [ -d "/etc/kubernetes/manifests" ] && ls /etc/kubernetes/manifests/*.yaml &>/dev/null; then
    log_warn "检测到上次 kubeadm init 的残留文件（集群未成功运行）"
    log_warn "这会导致重新初始化失败，需要先清理"
    read -p "是否清理残留并重新初始化? (y/n): " CONFIRM
    if [[ "$CONFIRM" =~ ^[Yy] ]]; then
        log_info "清理残留..."
        kubeadm reset -f 2>/dev/null || true
        systemctl stop kubelet 2>/dev/null || true
        rm -rf /etc/kubernetes/manifests /var/lib/etcd /var/lib/kubelet
        rm -rf /root/.kube "$REAL_HOME/.kube" 2>/dev/null || true
        iptables -F && iptables -t nat -F && iptables -t mangle -F && iptables -X
        log_info "残留已清理"
    else
        log_info "未清理，退出"
        exit 0
    fi

# --- 情况3: 全新环境，直接初始化 ---
else
    log_info "环境干净，开始初始化"
fi

# 确保 containerd sandbox 镜像使用阿里云镜像（避免 kubelet 因拉不到 pause 而卡住）
log_info "检查 containerd sandbox 镜像配置..."
SANDBOX_IMAGE=$(grep 'sandbox_image' /etc/containerd/config.toml 2>/dev/null | head -1)
if echo "$SANDBOX_IMAGE" | grep -q 'registry.k8s.io'; then
    log_warn "sandbox_image 仍指向 registry.k8s.io，改为阿里云镜像..."
    sed -i 's|sandbox_image\s*=\s*"[^"]*"|sandbox_image = "registry.cn-hangzhou.aliyuncs.com/google_containers/pause:3.9"|' /etc/containerd/config.toml
    systemctl restart containerd
    sleep 2
    log_info "containerd sandbox 镜像已修复"
elif grep -q 'sandbox_image' /etc/containerd/config.toml 2>/dev/null; then
    log_info "sandbox_image 配置: $(grep 'sandbox_image' /etc/containerd/config.toml | head -1 | xargs)"
    # 即使配置看起来正常，也要确保 containerd 完全加载了新配置
    if ! systemctl is-active --quiet containerd; then
        systemctl restart containerd
        sleep 2
    fi
else
    log_warn "未找到 sandbox_image 配置项，可能配置异常"
fi

# 检查 cgroup —— kubelet 依赖 cgroup，若缺失会导致 timeout
log_info "检查 cgroup 配置..."
if ! mount | grep -q cgroup2 && ! mount | grep -q 'cgroup on'; then
    log_error "未检测到 cgroup，kubelet 将无法启动"
    log_info "尝试添加 cgroup 内核参数..."
    if ! grep -q 'cgroup_enable' /etc/default/grub 2>/dev/null; then
        sed -i 's|^GRUB_CMDLINE_LINUX="|GRUB_CMDLINE_LINUX="cgroup_enable=memory swapaccount=1 |' /etc/default/grub
        update-grub
        log_warn "已更新 GRUB，请重启机器后重新运行本脚本: sudo reboot"
        exit 1
    fi
else
    log_info "cgroup 已启用"
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
    --apiserver-advertise-address=192.168.3.100 \
    --node-name=k8s-master

log_info "集群初始化完成！"

# ==========================================
# Step 2: 配置 kubectl
# ==========================================
log_step "[2/4] 配置 kubectl..."

# 为当前 root 环境配置 kubectl
mkdir -p /root/.kube
cp /etc/kubernetes/admin.conf /root/.kube/config

# 为真实用户也配置 kubectl（sudo 场景下用户不是 root）
if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
    mkdir -p "$REAL_HOME/.kube"
    cp /etc/kubernetes/admin.conf "$REAL_HOME/.kube/config"
    chown -R "$REAL_USER:$REAL_USER" "$REAL_HOME/.kube"

    # 配置 bash 补全
    if ! grep -q "kubectl completion bash" "$REAL_HOME/.bashrc" 2>/dev/null; then
        echo "source <(kubectl completion bash)" >> "$REAL_HOME/.bashrc"
    fi

    log_info "kubectl 已配置到用户 '$REAL_USER' 的 HOME 目录"
else
    log_info "kubectl 已配置到 /root/.kube/"
fi

# ==========================================
# Step 3: 保存 join 命令
# ==========================================
log_step "[3/4] 生成并保存 Worker 节点加入命令..."

JOIN_CMD_FILE="/root/k8s-join-command.sh"
kubeadm token create --print-join-command > "$JOIN_CMD_FILE"
chmod +x "$JOIN_CMD_FILE"

# 同步到 /tmp/，方便 Worker 节点通过普通用户 SCP 获取（Ubuntu 不允许 root SSH）
cp "$JOIN_CMD_FILE" /tmp/k8s-join-command.sh
chmod 644 /tmp/k8s-join-command.sh

log_info "Join 命令已保存到: $JOIN_CMD_FILE 和 /tmp/k8s-join-command.sh"
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

# Calico 超时检查
if [ "$ATTEMPTS" -ge "$MAX_ATTEMPTS" ]; then
    log_warn "Calico Pod 等待超时，网络插件可能未就绪"
    log_warn "请手动检查: kubectl get pods -n kube-system"
fi

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
kubectl get nodes -o wide 2>/dev/null || log_warn "无法获取节点状态"
echo ""
echo "系统 Pod 状态:"
kubectl get pods -n kube-system 2>/dev/null || log_warn "无法获取 Pod 状态"
echo ""
echo "=========================================="
echo -e "${YELLOW}  下一步: 在 Worker 节点执行加入操作${NC}"
echo "=========================================="
echo ""
log_info "Join 命令文件: /root/k8s-join-command.sh   (也可通过 /tmp/k8s-join-command.sh 用普通用户 SCP 获取)"
echo ""
echo "在 Worker 节点执行:"
echo "  sudo bash setup-node.sh"
echo ""
