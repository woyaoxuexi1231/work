#!/bin/bash
# ============================================================
#  Docker 完整卸载脚本
#  使用: sudo bash uninstall_docker.sh
#        sudo bash uninstall_docker.sh --all  (连 containerd 也清)
# ============================================================

set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }

MODE="${1:-keep-containerd}"

echo ""
echo "=========================================="
echo "  Docker 完整卸载"
[ "$MODE" = "--all" ] && echo "  模式: 完全清理（含 containerd）"
echo "=========================================="
echo ""

[ "$EUID" -ne 0 ] && { echo "请使用 sudo 运行"; exit 1; }

read -p "确认卸载 Docker? (y/n): " CONFIRM
[[ "$CONFIRM" =~ ^[Yy] ]] || { log_info "已取消"; exit 0; }

# 1. 停止并删除所有容器
log_info "停止所有容器..."
docker stop $(docker ps -aq) 2>/dev/null || true
log_info "删除所有容器..."
docker rm -f $(docker ps -aq) 2>/dev/null || true

# 2. 删除所有镜像
log_info "删除所有镜像..."
docker rmi -f $(docker images -q) 2>/dev/null || true

# 3. 删除所有卷和网络
log_info "清理卷和网络..."
docker volume rm $(docker volume ls -q) 2>/dev/null || true
docker network prune -f 2>/dev/null || true

# 4. 停止 Docker 服务
log_info "停止 Docker 服务..."
systemctl stop docker 2>/dev/null || true
systemctl stop docker.socket 2>/dev/null || true
systemctl disable docker 2>/dev/null || true
systemctl disable docker.socket 2>/dev/null || true

# 5. 卸载 Docker 软件包
log_info "卸载 Docker 软件包..."
for pkg in docker-ce docker-ce-cli docker.io docker-buildx-plugin docker-compose-plugin; do
    apt remove -y "$pkg" 2>/dev/null && log_info "  已移除 $pkg" || true
done
apt autoremove -y 2>/dev/null || true

# 6. containerd 处理
if [ "$MODE" = "--all" ]; then
    log_info "卸载 containerd..."
    systemctl stop containerd 2>/dev/null || true
    apt remove -y containerd.io 2>/dev/null || true
    rm -rf /etc/containerd/ /var/lib/containerd/
else
    log_info "保留 containerd（K8s 依赖它）"
fi

# 7. 清理 Docker 数据目录
log_info "清理数据目录..."
rm -rf /var/lib/docker/
rm -rf /var/lib/docker-engine/
rm -rf /var/lib/containers/

# 8. 清理 Docker 配置
log_info "清理配置文件..."
rm -rf /etc/docker/
rm -rf /root/.docker/
rm -f /etc/systemd/system/docker.service.d/override.conf
rm -f /etc/systemd/system/docker.service.d/http-proxy.conf

# 9. 清理 Docker 仓库源
log_info "清理 apt 源..."
rm -f /etc/apt/sources.list.d/docker.list
rm -f /etc/apt/sources.list.d/docker*.list
rm -f /usr/share/keyrings/docker-archive-keyring.gpg
rm -f /usr/share/keyrings/docker-ce-archive-keyring.gpg
rm -f /etc/apt/keyrings/docker.gpg
rm -f /etc/apt/keyrings/docker.asc
rm -f /var/lib/apt/lists/*docker*

# 10. 清理 systemd 残留
systemctl daemon-reload 2>/dev/null || true
systemctl reset-failed 2>/dev/null || true

echo ""
echo "=========================================="
echo -e "${GREEN}  Docker 卸载完成${NC}"
echo "=========================================="
echo ""
[ "$MODE" != "--all" ] && echo "  containerd 已保留（K8s 需要它）"
echo ""
