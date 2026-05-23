#!/bin/bash
# ============================================================
#  Kubernetes 集群 - Worker 节点加入脚本
#  在 k8s-node1 (192.168.2.59) / k8s-node2 (192.168.2.60) 上执行
#  前提: Master 节点已完成初始化, 本机已运行 setup-common.sh
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
echo "  K8s Worker 节点加入集群"
echo "=========================================="

# 检查 root
if [ "$EUID" -ne 0 ]; then
    log_error "请使用 root 用户运行此脚本"
    exit 1
fi

# 确认不是 Master 节点
CURRENT_HOST=$(hostname)
if [ "$CURRENT_HOST" = "k8s-master" ]; then
    log_error "当前主机是 Master 节点，这个脚本只能在 Worker 节点执行"
    log_error "请使用 setup-master.sh 初始化 Master 节点"
    exit 1
fi

if [[ ! "$CURRENT_HOST" =~ ^(k8s-node1|k8s-node2)$ ]]; then
    log_warn "当前主机名 '$CURRENT_HOST' 不在预期节点列表中 (k8s-node1/k8s-node2)"
    log_warn "请确认本机为 Worker 节点"
    read -p "是否继续? (yes/no): " CONTINUE
    if [ "$CONTINUE" != "yes" ]; then
        exit 0
    fi
fi

# ==========================================
# Step 1: 获取 join 命令
# ==========================================
log_step "[1/2] 获取集群加入信息..."

MASTER_IP="192.168.2.102"
API_SERVER_ENDPOINT="${MASTER_IP}:6443"

# 检查 Master 是否可达
log_info "检查 Master 节点连通性..."
if ! ping -c 1 -W 2 "$MASTER_IP" &> /dev/null; then
    log_error "无法连接到 Master 节点 $MASTER_IP，请检查网络"
    exit 1
fi
log_info "Master 节点可达"

# 判断是否已加入集群
if systemctl is-active --quiet kubelet && kubectl get node "$CURRENT_HOST" &> /dev/null 2>&1; then
    log_warn "当前节点似乎已加入集群"
    kubectl get node "$CURRENT_HOST" 2>/dev/null || true
    read -p "是否重置并重新加入? (yes/no): " CONFIRM
    if [ "$CONFIRM" = "yes" ]; then
        kubeadm reset -f
        rm -rf /etc/kubernetes/manifests /var/lib/kubelet
        iptables -F && iptables -t nat -F && iptables -t mangle -F && iptables -X
        log_info "已重置"
    else
        log_info "跳过"
        exit 0
    fi
fi

# 尝试读取 join 命令文件
JOIN_CMD_FILE="/root/k8s-join-command.sh"

# 尝试从 Master 复制 join 命令（如果有 SSH 免密登录）
if [ ! -f "$JOIN_CMD_FILE" ]; then
    log_info "尝试从 Master 节点获取 join 命令..."

    if command -v sshpass &> /dev/null; then
        log_info "使用 sshpass 从 Master 复制..."
        read -s -p "请输入 Master 节点 root 密码: " MASTER_PASS
        echo ""
        sshpass -p "$MASTER_PASS" scp -o StrictHostKeyChecking=no root@${MASTER_IP}:/root/k8s-join-command.sh "$JOIN_CMD_FILE"
    elif ssh -o StrictHostKeyChecking=no -o ConnectTimeout=3 root@${MASTER_IP} 'exit' 2>/dev/null; then
        log_info "SSH 免密登录可用，从 Master 复制 join 命令..."
        scp -o StrictHostKeyChecking=no root@${MASTER_IP}:/root/k8s-join-command.sh "$JOIN_CMD_FILE"
    fi
fi

# 如果文件存在，直接执行
if [ -f "$JOIN_CMD_FILE" ]; then
    log_info "找到 join 命令文件，执行加入..."
    chmod +x "$JOIN_CMD_FILE"
    bash "$JOIN_CMD_FILE"
else
    # 手动输入 join 命令
    log_warn "未找到 join 命令文件"
    log_warn ""
    log_warn "请按以下步骤操作:"
    log_warn "  1. 在 Master 节点执行: cat /root/k8s-join-command.sh"
    log_warn "  2. 将输出的命令复制下来"
    log_warn "  3. 在本机粘贴执行"
    log_warn ""
    log_warn "或者手动从 Master 节点获取 join 命令:"
    log_warn "  (Master 上执行) kubeadm token create --print-join-command"
    log_warn ""
    log_warn "示例格式:"
    log_warn "  kubeadm join 192.168.2.102:6443 --token xxxxxx \\"
    log_warn "      --discovery-token-ca-cert-hash sha256:xxxxxx"
    log_warn ""

    read -p "请输入 join 命令（直接粘贴，留空跳过）: " JOIN_CMD

    if [ -n "$JOIN_CMD" ]; then
        log_info "执行 join 命令..."
        eval "$JOIN_CMD"
    else
        log_error "未提供 join 命令，退出"
        exit 1
    fi
fi

# ==========================================
# Step 2: 验证加入结果
# ==========================================
log_step "[2/2] 验证节点状态..."

# 等待 kubelet 注册
sleep 5

if systemctl is-active --quiet kubelet; then
    log_info "kubelet 运行正常"
else
    log_error "kubelet 未运行，请检查 journalctl -u kubelet"
    exit 1
fi

echo ""
echo "=========================================="
echo -e "${GREEN}  Worker 节点加入完成: $CURRENT_HOST${NC}"
echo "=========================================="
echo ""
log_info "请在 Master 节点执行以下命令检查集群状态:"
echo ""
echo "  kubectl get nodes -o wide"
echo ""
echo "预期输出:"
echo "  NAME         STATUS   ROLES           AGE   VERSION"
echo "  k8s-master   Ready    control-plane   ...   v1.28.x"
echo "  k8s-node1    Ready    <none>          ...   v1.28.x"
echo "  k8s-node2    Ready    <none>          ...   v1.28.x"
echo ""
