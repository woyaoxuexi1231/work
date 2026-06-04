#!/usr/bin/env bash
# Jenkins LTS + JDK8 + Docker CLI | Port: 8080, 50000
export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="jenkins"; I="jenkins/jenkins:lts"; P="${JENKINS_PORT:-8080}"; AP="${JENKINS_AGENT_PORT:-50000}"
DATA="${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}/jenkins-data"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

# ==== 数据目录 ====
mkdir -p "${DATA}" || { log_error "无法创建 ${DATA}, 检查 Docker Desktop 文件共享"; exit 1; }

# ==== 拉取镜像 ====
pull_image "${I}"

# ==== 启动容器 ====
docker run -d --name "${C}" --restart=unless-stopped \
  -p ${P}:8080 -p ${AP}:50000 \
  -e TZ=Asia/Shanghai -e DOCKER_HOST=tcp://host.docker.internal:2375 \
  -v "${DATA}:/var/jenkins_home" \
  "${I}"
wait_for_container "${C}" 180

# ==== 等待 Jenkins 初始化完成 ====
log_info "等待 Jenkins 初始化..."
for i in $(seq 1 60); do
  if docker exec "${C}" test -f /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null; then
    log_info "Jenkins 初始化完成"
    break
  fi
  sleep 3
done

# ==== 安装 Docker CLI ====
log_info "安装 Docker CLI..."
docker exec -u root "${C}" bash -c '
  for i in $(seq 1 5); do
    apt-get update -qq 2>/dev/null && break || sleep 5
  done
  apt-get install -y -qq docker.io && apt-get clean
'
docker exec "${C}" docker --version 2>/dev/null || log_warn "Docker CLI 安装失败, 可进容器手动安装: apt-get install -y docker.io"

# ==== 安装 JDK8 ====
log_info "安装 JDK8 (Adoptium)..."
docker exec -u root "${C}" bash -c '
  mkdir -p /usr/lib/jvm; cd /tmp
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/8/ga/linux/x64/jdk/hotspot/normal/eclipse" -o jdk8.tar.gz || \
    wget -q -O jdk8.tar.gz "https://api.adoptium.net/v3/binary/latest/8/ga/linux/x64/jdk/hotspot/normal/eclipse"
  tar xzf jdk8.tar.gz -C /usr/lib/jvm; rm -f jdk8.tar.gz
  JDK8_DIR=$(ls -d /usr/lib/jvm/jdk8* 2>/dev/null || ls -d /usr/lib/jvm/temurin-8* 2>/dev/null | head -1)
  ln -sfn "$JDK8_DIR" /usr/lib/jvm/java-8-openjdk-amd64
  echo "JDK8: $JDK8_DIR"
' || log_warn "JDK8 安装失败"

# ==== 切换更新中心 ====
for i in $(seq 1 30); do
  if docker exec "${C}" test -f /var/jenkins_home/hudson.model.UpdateCenter.xml 2>/dev/null; then
    docker exec "${C}" sed -i 's|https://updates.jenkins.io/[^<]*|https://mirrors.tuna.tsinghua.edu.cn/jenkins/updates/update-center.json|g' /var/jenkins_home/hudson.model.UpdateCenter.xml
    log_info "更新中心 → 清华镜像"
    break
  fi
  sleep 2
done

# ==== 验证 Docker 连接 ====
if docker exec "${C}" docker ps >/dev/null 2>&1; then
  log_info "✓ Jenkins → Docker Desktop 通信正常"
else
  log_warn "Docker 连接失败, 确认 Docker Desktop → Settings → Expose daemon on tcp://localhost:2375"
fi

# ==== 初始密码 ====
INIT_PASS=$(docker exec "${C}" cat /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null || echo "获取失败, 手动查看: cat ${DATA}/secrets/initialAdminPassword")
log_info "初始密码: ${INIT_PASS}"

# ==== 完成 ====
done_banner "Jenkins | http://localhost:${P}"
log_info "JDK8: /usr/lib/jvm/java-8-openjdk-amd64"
log_info "配置: Manage Jenkins → Tools → JDK → Name=JDK8, JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64"
