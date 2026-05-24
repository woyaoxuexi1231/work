#!/bin/bash
# ============================================================
#  永久禁用 Swap 脚本 (Kubernetes 必备)
#  适用: Ubuntu 22.04 / 所有 K8s 节点
#  执行: sudo bash disable-swap-permanently.sh
# ============================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

if [ "$EUID" -ne 0 ]; then
    log_error "请使用 sudo 运行: sudo bash $0"
    exit 1
fi

echo "=========================================="
echo "  永久禁用 Swap 脚本"
echo "=========================================="

# 1. 立即关闭所有 swap
log_info "关闭当前所有 swap..."
swapoff -a

# 2. 确认 swap 已关闭
if grep -qE '^\S+\s+\S+\s+swap\s+' /proc/swaps; then
    log_error "swap 关闭失败，请检查 /proc/swaps"
    exit 1
fi
log_info "swap 已立即关闭"

# 3. 处理 /etc/fstab (注释掉所有 swap 行)
if grep -qE 'swap' /etc/fstab; then
    log_info "注释 /etc/fstab 中的 swap 行..."
    sed -i '/\sswap\s/s/^/#/' /etc/fstab
    log_info "/etc/fstab 已修改"
fi

# 4. 移除常见的 swap 文件
SWAP_FILES=("/swap.img" "/swapfile")
for sf in "${SWAP_FILES[@]}"; do
    if [ -f "$sf" ]; then
        log_info "删除 swap 文件: $sf"
        rm -f "$sf"
    fi
done

# 5. 禁用 systemd 管理的 swap 单元
log_info "禁用 systemd swap 单元..."
SYSTEMD_SWAP=$(systemctl list-units --type=swap --no-legend 2>/dev/null | awk '{print $1}')
if [ -n "$SYSTEMD_SWAP" ]; then
    for unit in $SYSTEMD_SWAP; do
        log_info "停止并禁用: $unit"
        systemctl stop "$unit" 2>/dev/null || true
        systemctl disable "$unit" 2>/dev/null || true
    done
else
    log_info "没有发现 systemd swap 单元"
fi

# 6. 防止 systemd 自动挂载 swap (删除 /etc/systemd/system 下可能的挂载单元)
find /etc/systemd/system -name "*swap*.mount" -delete 2>/dev/null || true
find /etc/systemd/system -name "*swap*.service" -delete 2>/dev/null || true
systemctl daemon-reload

# 7. 设置内核参数，让 vm.swappiness = 0 (可选，降低 swap 使用倾向)
log_info "设置 vm.swappiness=0..."
echo "vm.swappiness=0" > /etc/sysctl.d/99-kubernetes-noswap.conf
sysctl --system > /dev/null

# 8. 重启 kubelet (如果已安装)
if systemctl is-active kubelet &>/dev/null; then
    log_info "重启 kubelet 服务..."
    systemctl restart kubelet
    log_info "kubelet 重启完成"
fi

echo ""
echo "=========================================="
echo -e "${GREEN}  Swap 已被永久禁用，重启后也不会恢复${NC}"
echo "=========================================="
echo "  ✅ swapoff -a 已执行"
echo "  ✅ /etc/fstab 已注释 swap 条目"
echo "  ✅ 删除 /swap.img 或 /swapfile"
echo "  ✅ systemd swap 单元已停止并禁用"
echo "=========================================="