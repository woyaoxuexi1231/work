#!/bin/bash
# ============================================================
#  Kubernetes 集群 - 公共环境准备脚本
#  在所有节点上执行 (k8s-master / k8s-node1 / k8s-node2)
#  适用系统: Ubuntu 20.04 / 22.04
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

echo "=========================================="
echo "  K8s 公共环境准备脚本"
echo "  适用: 所有节点"
echo "=========================================="

# 检查 root 权限 (Ubuntu 使用 sudo 运行)
if [ "$EUID" -ne 0 ]; then
    log_error "请使用 sudo 运行此脚本: sudo bash setup-common.sh"
    exit 1
fi

# 检测真实的操作用户 (sudo 场景下 $SUDO_USER 非空)
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
log_step "[1/7] 配置主机名和 /etc/hosts..."

echo "当前主机名: $(hostname)"
echo ""
echo "集群节点规划:"
echo "  k8s-master  192.168.2.102"
echo "  k8s-node1   192.168.2.59"
echo "  k8s-node2   192.168.2.60"
echo ""

read -p "请输入本机的主机名 (k8s-master / k8s-node1 / k8s-node2): " HOSTNAME

if [[ ! "$HOSTNAME" =~ ^(k8s-master|k8s-node1|k8s-node2)$ ]]; then
    log_error "主机名不合法，必须是 k8s-master / k8s-node1 / k8s-node2"
    exit 1
fi

# 设置主机名
hostnamectl set-hostname "$HOSTNAME"
log_info "主机名已设置为: $HOSTNAME"

# 配置 hosts 文件
if ! grep -q "192.168.2.102 k8s-master" /etc/hosts; then
    cat >> /etc/hosts <<EOF

# Kubernetes Cluster
192.168.2.102  k8s-master
192.168.2.59   k8s-node1
192.168.2.60   k8s-node2
EOF
    log_info "/etc/hosts 已配置"
else
    log_info "/etc/hosts 已存在集群配置，跳过"
fi

# ==========================================
# Step 2: 关闭 Swap
# ==========================================
log_step "[2/7] 关闭 Swap..."

swapoff -a
sed -i '/ swap / s/^\(.*\)$/#\1/' /etc/fstab
log_info "Swap 已关闭"

# ==========================================
# Step 3: 加载内核模块 & 配置内核参数
# ==========================================
log_step "[3/7] 配置内核模块和参数..."

# 加载内核模块（开机自动加载）
cat > /etc/modules-load.d/k8s.conf <<EOF
overlay
br_netfilter
EOF

modprobe overlay
modprobe br_netfilter

# 配置内核参数
cat > /etc/sysctl.d/k8s.conf <<EOF
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF

# 立即生效（重启后由 /etc/sysctl.d/k8s.conf 自动加载）
sysctl -w net.bridge.bridge-nf-call-iptables=1  > /dev/null
sysctl -w net.bridge.bridge-nf-call-ip6tables=1 > /dev/null
sysctl -w net.ipv4.ip_forward=1                 > /dev/null
log_info "内核参数已配置"

# 验证
if lsmod | grep -q br_netfilter && lsmod | grep -q overlay; then
    log_info "内核模块加载成功"
else
    log_error "内核模块加载失败"
    exit 1
fi

# ==========================================
# Step 4: 安装 iptables 并放开转发
# ==========================================
log_step "[4/7] 安装并配置 iptables..."

apt update
apt install -y iptables arptables ebtables
iptables -P FORWARD ACCEPT
log_info "iptables 已配置"

# ==========================================
# Step 5: 安装 containerd 容器运行时
# ==========================================
log_step "[5/7] 安装 containerd..."

if systemctl is-active --quiet containerd; then
    log_info "containerd 已安装，跳过安装步骤"
else
    apt install -y apt-transport-https ca-certificates curl gnupg lsb-release

    # 添加 Docker 官方 GPG 密钥
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
        | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

    # 添加 Docker APT 仓库
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
        > /etc/apt/sources.list.d/docker.list

    apt update
    apt install -y containerd.io

    # 生成默认配置
    mkdir -p /etc/containerd
    containerd config default > /etc/containerd/config.toml

    log_info "containerd 安装完成"
fi

# ★ 不管 containerd 是新装还是已存在，都要确保配置正确
log_info "确保 containerd 配置正确..."

# 启用 SystemdCgroup
if grep -q 'SystemdCgroup = false' /etc/containerd/config.toml 2>/dev/null; then
    sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
    log_info "SystemdCgroup 已启用"
fi

# sandbox 镜像改为阿里云镜像
if grep 'sandbox_image' /etc/containerd/config.toml 2>/dev/null | grep -q 'registry.k8s.io'; then
    sed -i 's|sandbox_image\s*=\s*"[^"]*"|sandbox_image = "registry.cn-hangzhou.aliyuncs.com/google_containers/pause:3.9"|' /etc/containerd/config.toml
    log_info "sandbox 镜像已切换为阿里云"
fi

systemctl restart containerd
systemctl enable containerd
log_info "containerd 配置已完成"

# 验证
if systemctl is-active --quiet containerd; then
    log_info "containerd 运行正常"
else
    log_error "containerd 未运行"
    exit 1
fi

# ==========================================
# Step 6: 安装 Kubernetes 组件
# ==========================================
log_step "[6/7] 安装 kubeadm / kubelet / kubectl..."

if command -v kubeadm &> /dev/null; then
    log_info "Kubernetes 组件已安装: $(kubeadm version -o short)"
    log_info "跳过安装步骤"
else
    apt update

    # 安装依赖
    apt install -y apt-transport-https ca-certificates curl gpg

    # 添加 Kubernetes GPG 密钥
    curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.28/deb/Release.key \
        | gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg

    # 添加 Kubernetes 仓库（v1.28）
    echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.28/deb/ /" \
        > /etc/apt/sources.list.d/kubernetes.list

    apt update
    apt install -y kubelet kubeadm kubectl
    apt-mark hold kubelet kubeadm kubectl

    log_info "Kubernetes 组件安装完成"
fi

# 验证版本
log_info "kubeadm: $(kubeadm version -o short)"
log_info "kubelet: $(kubelet --version 2>&1 | head -1)"
log_info "kubectl: $(kubectl version --client -o short 2>/dev/null)"

# ==========================================
# Step 7: 配置 kubelet 开机启动
# ==========================================
log_step "[7/7] 配置 kubelet 自启动..."

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
