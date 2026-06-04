#!/usr/bin/env bash

export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

# Jenkins LTS (最新) + JDK8 + Docker CLI | Port: 8080, 50000
# 通过 Docker Desktop TCP API (2375) 控制宿主机 Docker
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

C="jenkins"; I="jenkins/jenkins:lts"; P="${JENKINS_PORT:-8080}"; AP="${JENKINS_AGENT_PORT:-50000}"

check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

pull_image "${I}"
docker run -d --name "${C}" --restart=unless-stopped \
  -p ${P}:8080 -p ${AP}:50000 \
  -e TZ=Asia/Shanghai \
  -e DOCKER_HOST=tcp://host.docker.internal:2375 \
  "${I}"

wait_for_container "${C}" 120

# ---- 安装 Docker CLI + Git + JDK8 ----
log_info "安装 Docker CLI..."
docker exec -u root "${C}" bash -c 'apt-get update -qq && apt-get install -y -qq docker.io && apt-get clean'
docker exec "${C}" docker --version

log_info "安装 JDK8 (Adoptium Temurin)..."
docker exec -u root "${C}" bash -c '
  JDK8_URL="https://api.adoptium.net/v3/binary/latest/8/ga/linux/x64/jdk/hotspot/normal/eclipse"
  mkdir -p /usr/lib/jvm
  cd /tmp
  curl -fsSL -o jdk8.tar.gz "$JDK8_URL" || wget -q -O jdk8.tar.gz "$JDK8_URL"
  tar xzf jdk8.tar.gz -C /usr/lib/jvm
  rm -f jdk8.tar.gz
  JDK8_DIR=$(ls -d /usr/lib/jvm/jdk8* 2>/dev/null || ls -d /usr/lib/jvm/temurin-8* 2>/dev/null | head -1)
  ln -sfn "$JDK8_DIR" /usr/lib/jvm/java-8-openjdk-amd64
  echo "JDK8 installed: $JDK8_DIR"
'

# ---- 切换更新中心 ----
for i in $(seq 1 30); do
  if docker exec "${C}" test -f /var/jenkins_home/hudson.model.UpdateCenter.xml 2>/dev/null; then
    docker exec "${C}" sed -i 's|https://updates.jenkins.io/update-center.json|https://mirrors.tuna.tsinghua.edu.cn/jenkins/updates/update-center.json|g' /var/jenkins_home/hudson.model.UpdateCenter.xml
    docker exec "${C}" sed -i 's|https://updates.jenkins.io/dynamic-.*/update-center.json|https://mirrors.tuna.tsinghua.edu.cn/jenkins/updates/update-center.json|g' /var/jenkins_home/hudson.model.UpdateCenter.xml
    log_info "更新中心已切换为清华源"
    break
  fi
  sleep 2
done

# ---- 验证 Docker 连接 ----
log_info "验证 Docker API 连接..."
if docker exec "${C}" docker ps >/dev/null 2>&1; then
  log_info "✓ Jenkins → Docker Desktop 通信正常"
else
  log_warn "Docker 连接失败，请确认 Docker Desktop 已开启 TCP API (tcp://localhost:2375)"
fi

# ---- 初始密码 ----
for i in $(seq 1 45); do
  if docker exec "${C}" test -f /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null; then
    INIT_PASS=$(docker exec "${C}" cat /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null)
    log_info "初始密码: ${INIT_PASS}"
    break
  fi
  sleep 2
done

done_banner "Jenkins LTS + JDK8"
log_info "URL: http://localhost:${P}"
log_info ""
log_info "配置 JDK8: Manage Jenkins → Tools → JDK → Add JDK"
log_info "  Name: JDK8"
log_info "  JAVA_HOME: /usr/lib/jvm/java-8-openjdk-amd64"
