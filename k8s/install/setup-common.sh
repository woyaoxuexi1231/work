#!/bin/bash
# ============================================================
#  Kubernetes 集群 - 公共环境准备脚本 (v1.28 + containerd 1.7.x)
#  在所有节点上执行 (k8s-master / k8s-node1 / k8s-node2)
#  适用系统: Ubuntu 20.04 / 22.04 / 24.04
# ============================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${BLUE}[STEP]${NC} $1"; }

K8S_MASTER_IP="${K8S_MASTER_IP:-192.168.3.90}"
K8S_NODE1_IP="${K8S_NODE1_IP:-192.168.3.101}"
K8S_NODE2_IP="${K8S_NODE2_IP:-192.168.3.102}"

upsert_hosts_entry() {
    local host_ip="$1"
    local host_name="$2"

    if grep -Eq "^[[:space:]]*[0-9.]+[[:space:]]+${host_name}([[:space:]]|$)" /etc/hosts; then
        sed -i -E "s|^[[:space:]]*[0-9.]+([[:space:]]+)${host_name}([[:space:]].*)?$|${host_ip}\1${host_name}|" /etc/hosts
    else
        echo "${host_ip}  ${host_name}" >> /etc/hosts
    fi
}

echo "=========================================="
echo "  K8s 公共环境准备脚本"
echo "  适用: 所有节点"
echo "=========================================="

# 检查 root 权限
if [ "$EUID" -ne 0 ]; then
    log_error "请使用 sudo 运行此脚本: sudo bash setup-common.sh"
    exit 1
fi

# 检测真实的操作用户
REAL_USER="${SUDO_USER:-$(logname 2>/dev/null || echo '')}"
if [ -n "$REAL_USER" ]; then
    REAL_HOME=$(eval echo ~$REAL_USER)
else
    REAL_USER="root"
    REAL_HOME="/root"
fi
log_info "真实用户: $REAL_USER"

# ==========================================
# Step 1: 配置主机名和 hosts
# ==========================================
log_step "[1/8] 配置主机名和 /etc/hosts..."

echo "当前主机名: $(hostname)"
echo ""
echo "集群节点规划:"
echo "  k8s-master  ${K8S_MASTER_IP}"
echo "  k8s-node1   ${K8S_NODE1_IP}"
echo "  k8s-node2   ${K8S_NODE2_IP}"
echo ""

read -p "请输入 Master IP [${K8S_MASTER_IP}]: " INPUT_MASTER_IP
K8S_MASTER_IP="${INPUT_MASTER_IP:-$K8S_MASTER_IP}"
read -p "请输入 Node1 IP [${K8S_NODE1_IP}]: " INPUT_NODE1_IP
K8S_NODE1_IP="${INPUT_NODE1_IP:-$K8S_NODE1_IP}"
read -p "请输入 Node2 IP [${K8S_NODE2_IP}]: " INPUT_NODE2_IP
K8S_NODE2_IP="${INPUT_NODE2_IP:-$K8S_NODE2_IP}"

echo ""
echo "确认后的集群节点规划:"
echo "  k8s-master  ${K8S_MASTER_IP}"
echo "  k8s-node1   ${K8S_NODE1_IP}"
echo "  k8s-node2   ${K8S_NODE2_IP}"
echo ""

read -p "请输入本机的主机名 (k8s-master / k8s-node1 / k8s-node2): " HOSTNAME

if [[ ! "$HOSTNAME" =~ ^(k8s-master|k8s-node1|k8s-node2)$ ]]; then
    log_error "主机名不合法，必须是 k8s-master / k8s-node1 / k8s-node2"
    exit 1
fi

hostnamectl set-hostname "$HOSTNAME"
log_info "主机名已设置为: $HOSTNAME"

if ! grep -q "^# Kubernetes Cluster$" /etc/hosts; then
    echo "" >> /etc/hosts
    echo "# Kubernetes Cluster" >> /etc/hosts
fi
upsert_hosts_entry "$K8S_MASTER_IP" "k8s-master"
upsert_hosts_entry "$K8S_NODE1_IP" "k8s-node1"
upsert_hosts_entry "$K8S_NODE2_IP" "k8s-node2"
log_info "/etc/hosts 已更新为当前集群规划"

# ==========================================
# Step 2: 关闭 Swap
# ==========================================
log_step "[2/8] 关闭 Swap..."

swapoff -a
sed -i '/ swap / s/^\(.*\)$/#\1/' /etc/fstab
log_info "Swap 已关闭"

# ==========================================
# Step 3: 加载内核模块 & 配置内核参数
# ==========================================
log_step "[3/8] 配置内核模块和参数..."

cat > /etc/modules-load.d/k8s.conf <<EOF
overlay
br_netfilter
EOF

modprobe overlay
modprobe br_netfilter

cat > /etc/sysctl.d/k8s.conf <<EOF
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF

sysctl --system > /dev/null 2>&1
log_info "内核参数已配置"

if lsmod | grep -q br_netfilter && lsmod | grep -q overlay; then
    log_info "内核模块加载成功"
else
    log_error "内核模块加载失败"
    exit 1
fi

# ==========================================
# Step 4: 安装 iptables 并放开转发
# ==========================================
log_step "[4/8] 安装并配置 iptables..."

apt update
apt install -y iptables arptables ebtables
iptables -P FORWARD ACCEPT
log_info "iptables 已配置"

# ==========================================
# Step 5: 安装 containerd 容器运行时 (核心修复)
# ==========================================
log_step "[5/8] 安装 containerd..."

# ★ 检查是否安装了错误的大版本，如果是则强制卸载重装
INSTALLED_VER=$(dpkg -s containerd.io 2>/dev/null | grep '^Version:' | awk '{print $2}' | cut -d. -f1 || echo "0")
if [ "$INSTALLED_VER" = "2" ]; then
    log_warn "检测到 containerd v2.x，与 K8s v1.28 不兼容，正在降级..."
    apt remove -y containerd.io
    rm -f /etc/containerd/config.toml
fi

# ★ 安装或确认 containerd 1.7.x
if dpkg -s containerd.io &>/dev/null && [ "$INSTALLED_VER" = "1" ]; then
    log_info "containerd 1.7.x 已安装，跳过安装步骤"
else
    apt install -y apt-transport-https ca-certificates curl gnupg lsb-release

    curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
        | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
        > /etc/apt/sources.list.d/docker.list

    apt update
    apt install -y containerd.io=1.7.27-*
    apt-mark hold containerd.io
    log_info "containerd 1.7.x 安装完成"
fi

# ★ 无条件重新生成配置（解决配置残缺/格式不匹配问题）
log_info "重建 containerd 配置..."
mkdir -p /etc/containerd
containerd config default > /etc/containerd/config.toml

# 无条件修改，不再依赖 grep 条件判断
sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
sed -i 's|sandbox_image = "[^"]*"|sandbox_image = "registry.cn-hangzhou.aliyuncs.com/google_containers/pause:3.9"|' /etc/containerd/config.toml
sed -i '/disabled_plugins/d' /etc/containerd/config.toml

systemctl restart containerd
systemctl enable containerd
sleep 3

# ★ 安装 crictl 用于验证 CRI 接口
if ! command -v crictl &> /dev/null; then
    log_info "安装 crictl..."
    CRICTL_VERSION="v1.28.0"
    curl -fsSL "https://github.com/kubernetes-sigs/cri-tools/releases/download/${CRICTL_VERSION}/crictl-${CRICTL_VERSION}-linux-amd64.tar.gz" \
        | tar xz -C /usr/local/bin
fi

# ★ 验证 CRI 接口（而非仅验证进程存活）
log_info "验证 containerd CRI 接口..."
if ! crictl info &>/dev/null; then
    log_error "containerd CRI 接口异常！请检查日志:"
    journalctl -u containerd --no-pager -n 30
    exit 1
fi
log_info "containerd CRI 接口正常，配置已完成"

# ==========================================
# Step 6: 预拉取 Calico 网络插件镜像
# ==========================================
log_step "[6/8] 预拉取 Calico 镜像（国内镜像源）..."

CALICO_VERSION="v3.26.4"
CALICO_IMAGES=(
    "calico/node:${CALICO_VERSION}"
    "calico/cni:${CALICO_VERSION}"
    "calico/kube-controllers:${CALICO_VERSION}"
)
# 国内镜像源，按优先级排列
MIRRORS=(
    "swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io"
    "docker.m.daocloud.io"
    "docker.1panel.live"
)

for IMG in "${CALICO_IMAGES[@]}"; do
    FULL_ORIG="docker.io/${IMG}"
    log_info "处理: ${FULL_ORIG}"

    PULLED=false
    for MIRROR in "${MIRRORS[@]}"; do
        MIRROR_IMG="${MIRROR}/${IMG}"
        echo -n "  尝试 ${MIRROR%%/*}... "

        if timeout 60 ctr -n k8s.io image pull --platform linux/amd64 "$MIRROR_IMG" &>/dev/null; then
            echo -e "${GREEN}成功${NC}"
            # 重打标签为原始 docker.io 名称，K8s 直接用本地缓存
            ctr -n k8s.io image tag "$MIRROR_IMG" "$FULL_ORIG" --force &>/dev/null
            log_info "  → ${FULL_ORIG}"
            PULLED=true
            break
        else
            echo -e "${RED}失败${NC}"
        fi
    done

    if [ "$PULLED" = false ]; then
        log_error "镜像 ${IMG} 所有镜像源均拉取失败！"
        log_error "请手动拉取: ctr -n k8s.io image pull docker.io/${IMG}"
        exit 1
    fi
done

log_info "Calico 镜像预拉取完成！"

# ==========================================
# Step 7: 安装 Kubernetes 组件
# ==========================================
log_step "[7/8] 安装 kubeadm / kubelet / kubectl..."

if command -v kubeadm &> /dev/null; then
    log_info "Kubernetes 组件已安装: $(kubeadm version -o short)"
    log_info "跳过安装步骤"
else
    apt update
    apt install -y apt-transport-https ca-certificates curl gpg

    curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.28/deb/Release.key \
        | gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg

    echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.28/deb/ /" \
        > /etc/apt/sources.list.d/kubernetes.list

    apt update
    apt install -y kubelet kubeadm kubectl
    apt-mark hold kubelet kubeadm kubectl

    log_info "Kubernetes 组件安装完成"
fi

log_info "kubeadm: $(kubeadm version -o short)"
log_info "kubelet: $(kubelet --version 2>&1 | head -1)"
log_info "kubectl: $(kubectl version --client -o short 2>/dev/null)"

# ==========================================
# Step 8: 配置 kubelet 开机启动
# ==========================================
log_step "[8/8] 配置 kubelet 自启动..."

systemctl enable kubelet

# ==========================================
# 完成
# ==========================================
echo ""
echo "=========================================="
echo -e "${GREEN}  公共环境准备完成！${NC}"
echo "=========================================="
echo ""
echo "当前节点: $HOSTNAME"
echo ""
echo -e "${YELLOW}后续操作：${NC}"
echo ""
if [ "$HOSTNAME" = "k8s-master" ]; then
    echo "  ▶ 在 Master 节点执行: sudo bash setup-master.sh"
    echo ""
else
    echo "  ▶ 等待 Master 节点完成初始化"
    echo "  ▶ 然后在 Worker 节点执行: sudo bash setup-node.sh"
    echo ""
fi
