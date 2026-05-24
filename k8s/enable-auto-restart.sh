#!/bin/bash
# ============================================================
#  K8s 重启自愈脚本 - 确保所有组件重启后自动恢复
#  在所有节点执行 (k8s-master / k8s-node1 / k8s-node2)
#  使用: sudo bash enable-auto-restart.sh
# ============================================================

set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${BLUE}[STEP]${NC} $1"; }
log_ok()    { echo -e "${GREEN}  ✓${NC} $1"; }
log_fix()   { echo -e "${YELLOW}  ↻${NC} $1"; }

echo ""
echo "=========================================="
echo "  K8s 重启自愈配置"
echo "  节点: $(hostname)"
echo "=========================================="
echo ""

[ "$EUID" -ne 0 ] && { log_error "请使用 sudo 运行"; exit 1; }

# ============================================================
# 1. iptables 规则持久化（最关键！重启后会丢失）
# ============================================================
log_step "[1/4] iptables 规则持久化..."

# 先确保当前规则正确
iptables -P FORWARD ACCEPT

# 安装 iptables-persistent（非交互模式）
export DEBIAN_FRONTEND=noninteractive
if ! dpkg -s iptables-persistent &>/dev/null; then
    log_fix "安装 iptables-persistent..."
    apt-get update -qq
    apt-get install -y -qq iptables-persistent netfilter-persistent 2>/dev/null || true
fi

# 保存当前规则（重启后自动恢复）
if command -v iptables-save &>/dev/null; then
    iptables-save > /etc/iptables/rules.v4
    log_ok "iptables 规则已保存到 /etc/iptables/rules.v4"
else
    log_warn "iptables-save 不可用，跳过"
fi

# 兜底：创建 systemd 服务，开机时强制设置 FORWARD ACCEPT
if [ ! -f /etc/systemd/system/k8s-iptables-forward.service ]; then
    cat > /etc/systemd/system/k8s-iptables-forward.service <<'EOF'
[Unit]
Description=K8s: Ensure iptables FORWARD ACCEPT
After=network.target

[Service]
Type=oneshot
ExecStart=/usr/sbin/iptables -P FORWARD ACCEPT
RemainAfterExit=yes

[Install]
WantedBy=multi-user.target
EOF
    systemctl daemon-reload
    systemctl enable k8s-iptables-forward.service 2>/dev/null || true
    log_ok "已创建 k8s-iptables-forward 兜底服务"
fi

# ============================================================
# 2. 确保关键服务开机自启
# ============================================================
log_step "[2/4] 检查关键服务自启状态..."

SERVICES=(
    "containerd"
    "kubelet"
    "k8s-iptables-forward"
)

ALL_OK=true
for svc in "${SERVICES[@]}"; do
    if systemctl is-enabled "$svc" &>/dev/null; then
        log_ok "$svc 已启用自启"
    else
        log_fix "$svc 未启用，正在设置..."
        systemctl enable "$svc" 2>/dev/null && log_ok "$svc 已启用" || log_error "$svc 启用失败"
        ALL_OK=false
    fi
done

# ============================================================
# 3. 验证内核模块自启
# ============================================================
log_step "[3/4] 验证内核模块自启..."

if [ -f /etc/modules-load.d/k8s.conf ]; then
    log_ok "内核模块配置存在: $(cat /etc/modules-load.d/k8s.conf | tr '\n' ' ')"
else
    log_fix "创建内核模块配置..."
    cat > /etc/modules-load.d/k8s.conf <<EOF
overlay
br_netfilter
EOF
    modprobe overlay
    modprobe br_netfilter
    log_ok "内核模块配置已创建"
fi

# 验证 sysctl
if [ -f /etc/sysctl.d/k8s.conf ]; then
    log_ok "sysctl 配置存在"
else
    log_warn "sysctl 配置缺失，请重新运行 setup-common.sh"
fi

# ============================================================
# 4. 验证 containerd 配置
# ============================================================
log_step "[4/4] 验证 containerd 配置..."

# 检查 docker.io 镜像加速
if grep -q 'endpoint.*mirror' /etc/containerd/config.toml 2>/dev/null; then
    log_ok "containerd 镜像加速已配置"
else
    log_warn "containerd 缺少 docker.io 镜像加速，请运行 fix-containerd-mirror.sh"
fi

# 检查 SystemdCgroup
if grep -q 'SystemdCgroup = true' /etc/containerd/config.toml 2>/dev/null; then
    log_ok "containerd SystemdCgroup = true"
else
    log_error "containerd SystemdCgroup != true，请检查配置"
fi

# ============================================================
# 模拟重启后恢复检查
# ============================================================
echo ""
echo "=========================================="
echo "  重启后自动恢复过程"
echo "=========================================="
echo ""
echo "  服务器重启后，以下步骤自动执行:"
echo ""
echo "  1. systemd 启动 containerd"
echo "  2. systemd 启动 kubelet"
echo "  3. kubelet 启动静态 Pod (etcd, apiserver, controller-manager, scheduler)"
echo "  4. kubelet 启动 DaemonSet Pod (calico-node, kube-proxy)"
echo "  5. kubelet 恢复 Deployment Pod (coredns, dashboard, 用户 Pod)"
echo ""
echo "  预计恢复时间: 1-3 分钟"
echo "  检查命令:    kubectl get nodes -o wide"
echo "               kubectl get pods -A"
echo ""

# ============================================================
# 生成重启后验证命令
# ============================================================
echo "=========================================="
echo -e "${GREEN}  自愈配置完成${NC}"
echo "=========================================="
echo ""
echo "  重启后验证集群状态:"
echo ""
echo "    # 节点状态"
echo "    kubectl get nodes -o wide"
echo ""
echo "    # Pod 状态"
echo "    kubectl get pods -A"
echo ""
echo "    # 关键服务"
echo "    sudo systemctl status containerd kubelet --no-pager"
echo ""
echo "    # iptables 规则"
echo "    sudo iptables -L FORWARD | head -3"
echo ""
echo "  提示: 重启后 K8s 需要 1-3 分钟恢复，请耐心等待"
echo ""
