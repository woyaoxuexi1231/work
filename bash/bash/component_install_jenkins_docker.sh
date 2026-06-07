#!/usr/bin/env bash
# Jenkins LTS + JDK8 + Docker CLI | Port: 8080, 50000
# export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="jenkins"; I="jenkins/jenkins:lts"; P="${JENKINS_PORT:-8080}"
DATA="${DOCKER_DATA_ROOT:-/c/Users/code/Desktop/docker-data}/jenkins-data"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"
mkdir -p "${DATA}" || { log_error "无法创建 ${DATA}"; exit 1; }

# ==== 拉取 + 启动 ====
pull_image "${I}"
log_info "启动 Jenkins 容器..."

# 如果 JDK 安装包存在，挂载进容器（避免 docker cp 路径问题）
JDK_TGZ="${SCRIPT_DIR}/jdk-8-linux-x64.tar.gz"
JDK_MOUNT=()
[[ -f "${JDK_TGZ}" ]] && JDK_MOUNT=(-v "${JDK_TGZ}:/tmp/jdk8.tar.gz:ro")

docker run -d --name "${C}" --restart=unless-stopped \
  -p ${P}:8080 -e TZ=Asia/Shanghai \
  -e DOCKER_HOST=tcp://host.docker.internal:2375 \
  -v "${DATA}:/var/jenkins_home" \
  "${JDK_MOUNT[@]}" \
  "${I}"
log_info "容器已创建，等待启动..."
for i in $(seq 1 30); do
  docker ps --format '{{.Names}}' | grep -qw "${C}" && { log_info "容器已启动"; break; }
  sleep 2
done

# ==== 等待 Jenkins 初始化 ====
log_info "等待 Jenkins 初始化（首次需 1-3 分钟）..."
for i in $(seq 1 90); do
  if docker exec "${C}" test -f /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null; then
    log_info "✓ Jenkins 初始化完成 (第 ${i} 次检查)"
    break
  fi
  [[ $((i % 15)) -eq 0 ]] && log_info "  等待中... (${i}/90)"
  sleep 3
done

# ==== 安装 Docker CLI ====
log_info "安装 Docker CLI..."
docker exec -u root "${C}" bash -c 'apt-get update -qq && apt-get install -y -qq docker.io && apt-get clean' 2>/dev/null && log_info "✓ Docker CLI 完成" || log_warn "Docker CLI 安装失败，不影响 Jenkins 使用"

# ==== 安装 JDK8 (离线) ====
JDK_TGZ="${SCRIPT_DIR}/jdk-8-linux-x64.tar.gz"
JDK_HOME="/usr/lib/jvm/java-8-openjdk-amd64"
if [[ -f "${JDK_TGZ}" ]]; then
  log_info "安装 JDK8 (离线)..."
  docker exec -u root "${C}" bash -c '
    mkdir -p /usr/lib/jvm
    tar xzf /tmp/jdk8.tar.gz -C /usr/lib/jvm
    rm -f /tmp/jdk8.tar.gz
    JDK8_DIR=$(ls -d /usr/lib/jvm/jdk* 2>/dev/null | head -1)
    ln -sfn "$JDK8_DIR" '"${JDK_HOME}"'
    echo "JDK8: $JDK8_DIR"
  '
  log_info "✓ JDK8 安装完成"
else
  log_warn "未找到 ${JDK_TGZ}, 跳过 JDK8"
  log_warn "下载: curl -L -o jdk-8-linux-x64.tar.gz 'https://api.adoptium.net/v3/binary/latest/8/ga/linux/x64/jdk/hotspot/normal/eclipse'"
fi

# ==== 切换更新中心 ====
log_info "切换更新中心 → 清华镜像..."
for i in $(seq 1 15); do
  if docker exec "${C}" test -f /var/jenkins_home/hudson.model.UpdateCenter.xml 2>/dev/null; then
    docker exec "${C}" sed -i 's|https://updates.jenkins.io/[^<]*|https://mirrors.tuna.tsinghua.edu.cn/jenkins/updates/update-center.json|g' /var/jenkins_home/hudson.model.UpdateCenter.xml
    log_info "✓ 更新中心已切换"
    break
  fi
  sleep 2
done

# ==== 完成 ====
INIT_PASS=$(docker exec "${C}" cat /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null || echo "获取失败")
done_banner "Jenkins | http://localhost:${P}"
log_info "初始密码: ${INIT_PASS}"
log_info "JDK8: Manage Jenkins → Tools → JDK → Name=JDK8, JAVA_HOME=${JDK_HOME}"
