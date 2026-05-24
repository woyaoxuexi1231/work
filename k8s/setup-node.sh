#!/bin/bash
# ============================================================
#  Kubernetes 集群 - Worker 节点加入脚本 (v1.28)
#  在 k8s-node1 / k8s-node2 上执行
#  前提: Master 已初始化, 本机已运行 setup-common.sh
# ============================================================

set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${BLUE}[STEP]${NC} $1"; }

echo "=========================================="
echo "  K8s Worker 节点加入集群"
echo "=========================================="

if [ "$EUID" -ne 0 ]; then
    log_error "请使用 sudo 运行: sudo bash setup-node.sh"
    exit 1
fi

REAL_USER="${SUDO_USER:-$(logname 2>/dev/null || echo '')}"
if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
    REAL_HOME=$(eval echo ~$REAL_USER)
else
    REAL_USER="root"; REAL_HOME="/root"
fi

CURRENT_HOST=$(hostname)
if [ "$CURRENT_HOST" = "k8s-master" ]; then
    log_error "此脚本只能在 Worker 节点执行"
    exit 1
fi

MASTER_IP="192.168.3.100"

# ==========================================
# Step 1: 环境清理与获取最新 join 命令
# ==========================================
log_step "[1/2] 环境检查与获取加入信息..."

if ! ping -c 1 -W 2 "$MASTER_IP" &>/dev/null; then
    log_error "无法连接 Master $MASTER_IP"
    exit 1
fi

# ★ 双重检测：同时覆盖"已加入集群"和"join失败留有脏数据"两种场景
NEED_RESET=false
if kubectl get node "$CURRENT_HOST" &>/dev/null 2>&1; then
    log_warn "检测到节点已加入集群"
    NEED_RESET=true
elif [ -f "/etc/kubernetes/kubelet.conf" ] || [ -d "/etc/kubernetes/pki" ]; then
    log_warn "检测到上次 join 失败的残留文件（节点未成功加入）"
    NEED_RESET=true
fi

if [ "$NEED_RESET" = true ]; then
    read -p "是否清理残留并重新加入? (y/n): " CONFIRM
    if [[ "$CONFIRM" =~ ^[Yy] ]]; then
        log_info "正在彻底清理残留..."
        kubeadm reset -f 2>/dev/null || true
        systemctl stop kubelet 2>/dev/null || true
        fuser -k 10250/tcp 2>/dev/null || true
        rm -rf /etc/kubernetes /var/lib/kubelet /var/lib/etcd
        rm -rf /root/.kube "$REAL_HOME/.kube" 2>/dev/null || true
        iptables -F && iptables -t nat -F && iptables -t mangle -F && iptables -X
        log_info "环境与端口残留已清理"
    else
        log_info "跳过清理，退出"
        exit 0
    fi
fi

JOIN_CMD_FILE="$REAL_HOME/k8s-join-command.sh"

# ★ 核心修复：Master 重装后，主动废弃本地缓存的旧 join 文件
if [ -f "$JOIN_CMD_FILE" ]; then
    log_warn "检测到本地存在旧的 join 命令文件"
    log_warn "若 Master 节点已重置或重装，该文件中的 Token 已失效"
    read -p "是否删除旧文件并从 Master 重新获取最新命令? (y/n): " REFRESH
    if [[ "$REFRESH" =~ ^[Yy] ]]; then
        rm -f "$JOIN_CMD_FILE"
        log_info "旧 join 文件已删除"
    fi
fi

# 从 Master 拉取最新的 join 命令
if [ ! -f "$JOIN_CMD_FILE" ]; then
    SSH_USER="${SUDO_USER:-hulei}"
    read -p "Master SSH 用户名 [$SSH_USER]: " INPUT_USER
    SSH_USER="${INPUT_USER:-$SSH_USER}"

    if ! command -v sshpass &>/dev/null; then
        log_info "安装 sshpass..."
        apt update && apt install -y sshpass
    fi

    read -s -p "请输入 ${SSH_USER}@${MASTER_IP} 密码: " MASTER_PASS; echo ""

    if sshpass -p "$MASTER_PASS" scp -o StrictHostKeyChecking=no \
        "${SSH_USER}@${MASTER_IP}:/tmp/k8s-join-command.sh" "$JOIN_CMD_FILE" 2>/dev/null; then
        log_info "最新 Join 命令获取成功"
    else
        log_warn "SCP 获取失败，请检查网络、用户名或密码"
    fi
fi

# 执行 join，内置 Token 过期自愈机制
EXEC_JOIN() {
    local cmd="$1"
    log_info "执行: $cmd"
    if eval "$cmd" 2>&1 | tee /tmp/join-output.log; then
        return 0
    fi

    if grep -qiE 'token.*expired|unauthorized|authentication' /tmp/join-output.log; then
        log_warn "Join Token 已过期或无效"
        log_warn "请在 Master 节点执行: kubeadm token create --print-join-command"
        read -p "请粘贴新的 join 命令: " NEW_CMD
        if [ -n "$NEW_CMD" ]; then
            eval "$NEW_CMD"
        else
            log_error "未提供有效命令，退出"
            exit 1
        fi
    else
        log_error "Join 失败，请查看日志: cat /tmp/join-output.log"
        exit 1
    fi
}

if [ -f "$JOIN_CMD_FILE" ]; then
    EXEC_JOIN "$(cat "$JOIN_CMD_FILE")"
else
    read -p "请手动粘贴 join 命令: " MANUAL_CMD
    [ -z "$MANUAL_CMD" ] && { log_error "未提供 join 命令"; exit 1; }
    EXEC_JOIN "$MANUAL_CMD"
fi

# ==========================================
# Step 2: 验证节点状态
# ==========================================
log_step "[2/2] 验证节点状态..."
sleep 5

if systemctl is-active --quiet kubelet; then
    log_info "kubelet 服务运行正常"
else
    log_error "kubelet 未正常运行，请执行: journalctl -u kubelet -n 30 --no-pager"
    exit 1
fi

echo ""
echo "=========================================="
echo -e "${GREEN}  Worker 节点加入完成: $CURRENT_HOST${NC}"
echo "=========================================="
log_info "请前往 Master 节点执行以下命令验证集群状态:"
echo "  kubectl get nodes -o wide"