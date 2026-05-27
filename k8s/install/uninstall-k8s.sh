#!/bin/bash
# ============================================================
#  Kubernetes 集群卸载脚本
#  在要清理的节点上执行 (master / node1 / node2)
#  使用: sudo bash uninstall-k8s.sh        # 清理 K8s，保留 containerd
#        sudo bash uninstall-k8s.sh --all  # 连 containerd 也清
# ============================================================

set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

MODE="${1:-keep-containerd}"

echo ""
echo "=========================================="
echo "  Kubernetes 集群卸载"
echo "  节点: $(hostname)"
[ "$MODE" = "--all" ] && echo "  模式: 完全清理（含 containerd）"
echo "=========================================="
echo ""

[ "$EUID" -ne 0 ] && { log_error "请使用 sudo 运行"; exit 1; }

read -p "确认卸载 Kubernetes? (y/n): " CONFIRM
[[ "$CONFIRM" =~ ^[Yy] ]] || { log_info "已取消"; exit 0; }

# 1. kubeadm reset（清理集群配置）
log_info "执行 kubeadm reset..."
kubeadm reset -f 2>/dev/null || log_warn "kubeadm reset 失败（可能已清过），继续..."

# 2. 停止 kubelet
log_info "停止 kubelet..."
systemctl stop kubelet 2>/dev/null || true
systemctl disable kubelet 2>/dev/null || true

# 3. 删除 K8s 数据目录
log_info "清理 K8s 数据..."
rm -rf /etc/kubernetes/
rm -rf /var/lib/kubelet/
rm -rf /var/lib/etcd/
rm -rf /root/.kube/
rm -rf /etc/cni/net.d/
rm -f /root/k8s-join-command.sh /root/k8s-dashboard-token.txt /root/k8s-dashboard-admin.yaml

# 4. 清理 iptables
log_info "清理 iptables..."
iptables -F && iptables -t nat -F && iptables -t mangle -F && iptables -X 2>/dev/null || true

# 5. 清理网络接口
log_info "清理网络接口..."
ip link delete cni0 2>/dev/null || true
ip link delete flannel.1 2>/dev/null || true
ip link delete kube-ipvs0 2>/dev/null || true

# 6. 清理 /etc/hosts 中的集群条目
log_info "清理 /etc/hosts..."
sed -i '/k8s-master\|k8s-node1\|k8s-node2\|Kubernetes Cluster/d' /etc/hosts 2>/dev/null || true

# 7. 清理 K8s apt 仓库
log_info "清理 K8s apt 源..."
rm -f /etc/apt/sources.list.d/kubernetes.list
rm -f /etc/apt/keyrings/kubernetes-apt-keyring.gpg

# 8. 卸载 K8s 软件包
log_info "卸载 kubeadm / kubelet / kubectl..."
apt-mark unhold kubeadm kubelet kubectl 2>/dev/null || true
apt remove -y kubeadm kubelet kubectl 2>/dev/null || true
apt autoremove -y 2>/dev/null || true

# 9. 清理 K8s 相关的 systemd 服务
rm -f /etc/systemd/system/k8s-iptables-forward.service
systemctl daemon-reload 2>/dev/null || true

# 10. containerd 处理
if [ "$MODE" = "--all" ]; then
    log_info "卸载 containerd..."
    systemctl stop containerd 2>/dev/null || true
    apt-mark unhold containerd.io 2>/dev/null || true
    apt remove -y containerd.io 2>/dev/null || true
    rm -rf /etc/containerd/
else
    log_info "保留 containerd（Docker 依赖它）"
    # 清理 containerd 中的 K8s 容器和镜像
    crictl rm -a 2>/dev/null || true
    crictl rmi --prune 2>/dev/null || true
    # 恢复 containerd 原始配置
    if [ -f /etc/containerd/config.toml ]; then
        cp /etc/containerd/config.toml /etc/containerd/config.toml.k8s.bak 2>/dev/null || true
    fi
fi

# 11. 清理 SysV init（k8s-master 上有 LSB docker 脚本残留）
update-rc.d docker remove 2>/dev/null || true

echo ""
echo "=========================================="
echo -e "${GREEN}  Kubernetes 卸载完成${NC}"
echo "=========================================="
echo ""
echo "  已清理:"
echo "    - kubeadm / kubelet / kubectl 软件包"
echo "    - /etc/kubernetes / /var/lib/kubelet / /var/lib/etcd"
echo "    - /etc/hosts 中的集群条目"
echo "    - iptables 规则"
[ "$MODE" != "--all" ] && echo "    - containerd 已保留（Docker 仍可用）"
echo ""
echo "  这台机器现在可以重新加入新集群了。"
echo ""
