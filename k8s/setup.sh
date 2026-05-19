#!/bin/bash
# Kubernetes 集群快速部署脚本 - Ubuntu 20.04/22.04
# 使用前请仔细阅读 README.md

set -e

echo "=========================================="
echo "  K8s 集群快速部署脚本"
echo "=========================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 检查是否为 root 用户
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}请使用 root 用户运行此脚本${NC}"
    exit 1
fi

echo -e "${GREEN}[1/8] 更新系统...${NC}"
apt update && apt upgrade -y

echo -e "${GREEN}[2/8] 关闭 Swap...${NC}"
swapoff -a
sed -i '/ swap / s/^\(.*\)$/#\1/' /etc/fstab

echo -e "${GREEN}[3/8] 加载内核模块...${NC}"
modprobe overlay
modprobe br_netfilter

echo -e "${GREEN}[4/8] 配置内核参数...${NC}"
cat > /etc/sysctl.d/k8s.conf <<EOF
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF
sysctl --system

echo -e "${GREEN}[5/8] 安装 containerd...${NC}"
apt install -y apt-transport-https ca-certificates curl gnupg lsb-release
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" > /etc/apt/sources.list.d/docker.list
apt update
apt install -y containerd.io
mkdir -p /etc/containerd
containerd config default > /etc/containerd/config.toml
sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
systemctl restart containerd
systemctl enable containerd

echo -e "${GREEN}[6/8] 安装 Kubernetes 组件...${NC}"
curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg | gpg --dearmor -o /etc/apt/keyrings/kubernetes-archive-keyring.gpg
echo "deb [signed-by=/etc/apt/keyrings/kubernetes-archive-keyring.gpg] https://apt.kubernetes.io/ kubernetes-xenial main" > /etc/apt/sources.list.d/kubernetes.list
apt update
apt install -y kubelet kubeadm kubectl
apt-mark hold kubelet kubeadm kubectl

echo -e "${GREEN}[7/8] 设置主机名...${NC}"
read -p "请输入主机名 (master/node1/node2): " HOSTNAME
hostnamectl set-hostname $HOSTNAME

echo -e "${GREEN}[8/8] 完成！${NC}"
echo ""
echo "=========================================="
echo -e "${YELLOW}下一步操作：${NC}"
echo "=========================================="
echo ""
echo "Master 节点执行:"
echo "  kubeadm init --pod-network-cidr=10.244.0.0/16 --image-repository registry.cn-hangzhou.aliyuncs.com/google_containers"
echo ""
echo "初始化后配置 kubectl:"
echo "  mkdir -p \$HOME/.kube"
echo "  cp -i /etc/kubernetes/admin.conf \$HOME/.kube/config"
echo ""
echo "安装网络插件:"
echo "  kubectl apply -f https://raw.githubusercontent.com/flannel-io/flannel/master/Documentation/kube-flannel.yml"
echo ""
echo "Worker 节点加入:"
echo "  kubeadm join <master-ip>:6443 --token <token> --discovery-token-ca-cert-hash sha256:<hash>"
echo ""
echo "=========================================="
