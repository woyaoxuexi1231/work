#!/bin/bash
# ============================================================
#  Kubernetes 集群 - Master 节点初始化脚本 (v1.28)
#  仅在 k8s-master 上执行
#  前提: 已运行 setup-common.sh
# ============================================================

set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${BLUE}[STEP]${NC} $1"; }

detect_default_ip() {
    ip route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i <= NF; i++) if ($i == "src") {print $(i+1); exit}}'
}

resolve_master_ip() {
    if [ -n "${K8S_MASTER_IP:-}" ]; then
        echo "${K8S_MASTER_IP}"
        return 0
    fi

    getent hosts k8s-master 2>/dev/null | awk '{print $1; exit}'
}

MASTER_IP="$(resolve_master_ip)"
if [ -z "$MASTER_IP" ]; then
    MASTER_IP="$(detect_default_ip)"
fi
POD_CIDR="${K8S_POD_CIDR:-10.244.0.0/16}"

if [ -z "$MASTER_IP" ]; then
    log_error "无法自动探测 Master IP，请通过环境变量指定: export K8S_MASTER_IP=<master-ip>"
    exit 1
fi

echo "=========================================="
echo "  K8s Master 节点初始化"
echo "  节点: k8s-master (${MASTER_IP})"
echo "=========================================="

if [ "$EUID" -ne 0 ]; then
    log_error "请使用 sudo 运行此脚本: sudo bash setup-master.sh"
    exit 1
fi

REAL_USER="${SUDO_USER:-$(logname 2>/dev/null || echo '')}"
if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
    REAL_HOME=$(eval echo ~$REAL_USER)
else
    REAL_USER="root"; REAL_HOME="/root"
fi

CURRENT_HOST=$(hostname)
if [ "$CURRENT_HOST" != "k8s-master" ]; then
    log_error "当前主机名是 '$CURRENT_HOST'，请在 k8s-master 节点上执行此脚本"
    exit 1
fi

# ==========================================
# Step 1: kubeadm init
# ==========================================
log_step "[1/4] 初始化 Kubernetes 集群..."

# --- 集群状态检测与重置 ---
NEED_RESET=false
if kubectl cluster-info &> /dev/null; then
    log_warn "检测到已有 Kubernetes 集群正在运行"
    read -p "是否需要重置并重新初始化? (y/n): " CONFIRM
    [[ "$CONFIRM" =~ ^[Yy] ]] && NEED_RESET=true
elif [ -d "/etc/kubernetes/manifests" ] && ls /etc/kubernetes/manifests/*.yaml &>/dev/null 2>&1; then
    log_warn "检测到上次 kubeadm init 的残留文件"
    read -p "是否清理残留并重新初始化? (y/n): " CONFIRM
    [[ "$CONFIRM" =~ ^[Yy] ]] && NEED_RESET=true
fi

if [ "$NEED_RESET" = true ]; then
    log_info "正在重置集群..."
    kubeadm reset -f 2>/dev/null || true
    systemctl stop kubelet 2>/dev/null || true
    rm -rf /etc/kubernetes/manifests /var/lib/etcd /var/lib/kubelet
    rm -rf /root/.kube "$REAL_HOME/.kube" 2>/dev/null || true
    iptables -F && iptables -t nat -F && iptables -t mangle -F && iptables -X
    log_info "集群已重置"
elif [ "$NEED_RESET" = false ] && kubectl cluster-info &> /dev/null; then
    log_info "集群保持不动，退出"
    exit 0
fi

# ★ 强制校验 containerd CRI 接口（不再信任进程存活）
log_info "验证 containerd CRI 接口..."
if ! crictl info &>/dev/null; then
    log_error "containerd CRI 接口不可用！请先重新运行 setup-common.sh"
    journalctl -u containerd --no-pager -n 20
    exit 1
fi
log_info "CRI 接口正常"

# ★ 无条件确保 sandbox 镜像配置正确
log_info "强制校验 sandbox 镜像配置..."
sed -i 's|sandbox_image = "[^"]*"|sandbox_image = "registry.cn-hangzhou.aliyuncs.com/google_containers/pause:3.9"|' /etc/containerd/config.toml
systemctl restart containerd
sleep 3

# cgroup 检查
if ! mount | grep -qE 'cgroup2|cgroup on'; then
    log_error "未检测到 cgroup，请检查内核参数"
    exit 1
fi
log_info "cgroup 已启用"

# 预拉取镜像
log_info "预拉取 Kubernetes 镜像..."
kubeadm config images pull --image-repository registry.cn-hangzhou.aliyuncs.com/google_containers

# 初始化集群
log_info "正在初始化集群..."
kubeadm init \
    --pod-network-cidr="${POD_CIDR}" \
    --service-cidr=10.96.0.0/12 \
    --image-repository registry.cn-hangzhou.aliyuncs.com/google_containers \
    --apiserver-advertise-address="${MASTER_IP}" \
    --node-name=k8s-master \
    --upload-certs

log_info "集群初始化完成！"

# ==========================================
# Step 2: 配置 kubectl
# ==========================================
log_step "[2/4] 配置 kubectl..."

mkdir -p /root/.kube
cp /etc/kubernetes/admin.conf /root/.kube/config

if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
    mkdir -p "$REAL_HOME/.kube"
    cp /etc/kubernetes/admin.conf "$REAL_HOME/.kube/config"
    chown -R "$REAL_USER:$REAL_USER" "$REAL_HOME/.kube"
    if ! grep -q "kubectl completion bash" "$REAL_HOME/.bashrc" 2>/dev/null; then
        echo "source <(kubectl completion bash)" >> "$REAL_HOME/.bashrc"
    fi
    log_info "kubectl 已配置到用户 '$REAL_USER'"
else
    log_info "kubectl 已配置到 /root/.kube/"
fi

# ==========================================
# Step 3: 保存 join 命令（含 control-plane 加入命令）
# ==========================================
log_step "[3/4] 生成并保存 Join 命令..."

JOIN_CMD_FILE="/root/k8s-join-command.sh"
kubeadm token create --print-join-command > "$JOIN_CMD_FILE"
chmod +x "$JOIN_CMD_FILE"

# 同步到 /tmp/ 供 Worker SCP 获取
cp "$JOIN_CMD_FILE" /tmp/k8s-join-command.sh
chmod 644 /tmp/k8s-join-command.sh

log_info "Join 命令已保存:"
cat "$JOIN_CMD_FILE"

# ==========================================
# Step 4: 安装 Calico 网络插件（使用预拉取的本地镜像）
# ==========================================
log_step "[4/4] 安装 Calico 网络插件..."

sleep 5

# ★ 下载 Calico YAML 清单
CALICO_URL="https://ghproxy.net/https://raw.githubusercontent.com/projectcalico/calico/v3.26.4/manifests/calico.yaml"
log_info "从国内代理拉取 Calico 清单..."
if ! curl -fsSL -o /tmp/calico.yaml "$CALICO_URL"; then
    CALICO_BACKUP="https://mirror.ghproxy.com/https://raw.githubusercontent.com/projectcalico/calico/v3.26.4/manifests/calico.yaml"
    log_warn "主代理失败，尝试备用源..."
    curl -fsSL -o /tmp/calico.yaml "$CALICO_BACKUP" || {
        log_error "Calico YAML 下载失败，请手动下载并执行 kubectl apply -f calico.yaml"
        exit 1
    }
fi

# ★ 修正 Calico YAML 中的镜像地址为 docker.io 标准格式（与本地预拉取的标签一致）
log_info "修正 Calico YAML 镜像地址..."
sed -i 's|^\(\s*image:\s*\)docker\.io/calico/|\1docker.io/calico/|' /tmp/calico.yaml
sed -i 's|^\(\s*image:\s*\)quay\.io/calico/|\1docker.io/calico/|' /tmp/calico.yaml
sed -i 's|^\(\s*image:\s*\)calico/|\1docker.io/calico/|' /tmp/calico.yaml

# ★ 确保 Pod CIDR 与 kubeadm init 一致
log_info "设置 Pod CIDR 为 ${POD_CIDR}..."
# 取消注释并设置正确的 CIDR
sed -i '/# *- name: CALICO_IPV4POOL_CIDR/{n;s/^# */  /}; /# *value: "10.244/{n;s/^# */  /}' /tmp/calico.yaml 2>/dev/null || true
sed -i 's|value: "192.168.0.0/16"|value: "'${POD_CIDR}'"|' /tmp/calico.yaml

# 验证修改结果
log_info "Calico YAML 镜像引用:"
grep 'image:.*calico' /tmp/calico.yaml | head -5

kubectl apply -f /tmp/calico.yaml
log_info "Calico 网络插件安装中（使用本地缓存镜像，无需联网拉取）..."

# 等待 Calico Pod 就绪
log_info "等待 Calico Pod 启动 (最多 120 秒)..."
for i in $(seq 1 24); do
    READY=$(kubectl get pods -n kube-system -l k8s-app=calico-node --field-selector=status.phase=Running 2>/dev/null | grep -c Running || true)
    [ "$READY" -ge 1 ] && { log_info "Calico Pod 已就绪"; break; }
    sleep 5
    echo -n "."
done
echo ""

# 移除 Master 污点
kubectl taint nodes k8s-master node-role.kubernetes.io/control-plane:NoSchedule- 2>/dev/null || true

# ==========================================
# 验证
# ==========================================
echo ""
echo "=========================================="
echo -e "${GREEN}  Master 节点初始化完成！${NC}"
echo "=========================================="
kubectl get nodes -o wide 2>/dev/null || true
echo ""
kubectl get pods -n kube-system 2>/dev/null || true
echo ""
log_info "Worker 加入命令: /tmp/k8s-join-command.sh"
