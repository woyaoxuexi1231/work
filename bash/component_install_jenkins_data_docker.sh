#!/usr/bin/env bash

export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

# Jenkins LTS (最新) + JDK8 数据挂载版 | Port: 8080, 50000
# 数据落盘: C:\Users\15434\Desktop\docker-data\jenkins-data
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

C="jenkins"; I="jenkins/jenkins:lts"; P="${JENKINS_PORT:-8080}"; AP="${JENKINS_AGENT_PORT:-50000}"
DOCKER_DATA_ROOT="${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}"
DATA="${DOCKER_DATA_ROOT}/jenkins-data"

check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

mkdir -p "${DATA}" || {
  log_error "无法创建 ${DATA}"
  log_error "  1. 路径: C:\\Users\\15434\\Desktop\\docker-data\\"
  log_error "  2. Docker Desktop → Settings → Resources → File Sharing 已添加 C 盘"
  exit 1
}

pull_image "${I}"
docker run -d --name "${C}" --restart=unless-stopped \
  -p ${P}:8080 -p ${AP}:50000 \
  -e TZ=Asia/Shanghai \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "${DATA}:/var/jenkins_home" \
  "${I}"

wait_for_container "${C}" 120

# ---- 安装 JDK8 ----
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

# ---- 切换更新中心为清华镜像 ----
for i in $(seq 1 30); do
  if docker exec "${C}" test -f /var/jenkins_home/hudson.model.UpdateCenter.xml 2>/dev/null; then
    docker exec "${C}" sed -i 's|https://updates.jenkins.io/update-center.json|https://mirrors.tuna.tsinghua.edu.cn/jenkins/updates/update-center.json|g' /var/jenkins_home/hudson.model.UpdateCenter.xml
    docker exec "${C}" sed -i 's|https://updates.jenkins.io/dynamic-.*/update-center.json|https://mirrors.tuna.tsinghua.edu.cn/jenkins/updates/update-center.json|g' /var/jenkins_home/hudson.model.UpdateCenter.xml
    log_info "更新中心已切换为清华源"
    break
  fi
  sleep 2
done

# ---- 获取初始密码 ----
for i in $(seq 1 45); do
  if docker exec "${C}" test -f /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null; then
    INIT_PASS=$(docker exec "${C}" cat /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null)
    log_info "初始密码: ${INIT_PASS}"
    break
  fi
  sleep 2
done

done_banner "Jenkins LTS + JDK8 (挂载版)"
log_info "URL: http://localhost:${P}  Agent: ${AP}"
log_info "数据目录: ${DATA}"
log_info "Jenkins: $(docker exec "${C}" java -version 2>&1 | head -1)"
log_info "JDK8: $(docker exec "${C}" /usr/lib/jvm/java-8-openjdk-amd64/bin/java -version 2>&1 | head -1)"
log_info ""
log_info "初始密码: cat ${DATA}/secrets/initialAdminPassword"
log_info ""
log_info "配置 JDK8 工具: Manage Jenkins → Tools → JDK → Add JDK"
log_info "  Name: JDK8"
log_info "  JAVA_HOME: /usr/lib/jvm/java-8-openjdk-amd64"
