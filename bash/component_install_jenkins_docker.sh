#!/usr/bin/env bash
# Jenkins LTS + JDK8 + Docker CLI | Port: 8080, 50000
export MSYS_NO_PATHCONV=1; export MSYS2_ARG_CONV_EXCL="*"
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

# ==== 配置 ====
C="jenkins"; I="jenkins/jenkins:lts"; P="${JENKINS_PORT:-8080}"
DATA="${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}/jenkins-data"

# ==== 前置检查 ====
check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"
mkdir -p "${DATA}" || { log_error "无法创建 ${DATA}"; exit 1; }

# ==== 拉取 + 启动 ====
pull_image "${I}"
docker run -d --name "${C}" --restart=unless-stopped \
  -p ${P}:8080 -e TZ=Asia/Shanghai \
  -e DOCKER_HOST=tcp://host.docker.internal:2375 \
  -v "${DATA}:/var/jenkins_home" \
  "${I}"
wait_for_container "${C}" 180

# 等待 Jenkins 初始化完成
for i in $(seq 1 60); do
  docker exec "${C}" test -f /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null && break
  sleep 3
done

# ==== 安装 Docker CLI ====
docker exec -u root "${C}" bash -c 'apt-get update -qq && apt-get install -y -qq docker.io && apt-get clean' || true

# ==== 安装 JDK8 (离线) ====
# 把 jdk-8-linux-x64.tar.gz 放在脚本同目录下
JDK_TGZ="${SCRIPT_DIR}/jdk-8-linux-x64.tar.gz"
JDK_HOME="/usr/lib/jvm/java-8-openjdk-amd64"
if [[ -f "${JDK_TGZ}" ]]; then
  docker cp "${JDK_TGZ}" "${C}:/tmp/jdk8.tar.gz"
  docker exec -u root "${C}" bash -c '
    mkdir -p /usr/lib/jvm
    tar xzf /tmp/jdk8.tar.gz -C /usr/lib/jvm
    rm -f /tmp/jdk8.tar.gz
    JDK8_DIR=$(ls -d /usr/lib/jvm/jdk* 2>/dev/null | head -1)
    ln -sfn "$JDK8_DIR" '"${JDK_HOME}"'
    echo "JDK8: $JDK8_DIR"
  '
else
  log_warn "未找到 ${JDK_TGZ}, 跳过 JDK8 安装"
  log_warn "下载地址: https://adoptium.net/download/ (Linux x64 JDK8 tar.gz)"
fi

# ==== 切换更新中心 ====
for i in $(seq 1 30); do
  docker exec "${C}" test -f /var/jenkins_home/hudson.model.UpdateCenter.xml 2>/dev/null && {
    docker exec "${C}" sed -i 's|https://updates.jenkins.io/[^<]*|https://mirrors.tuna.tsinghua.edu.cn/jenkins/updates/update-center.json|g' /var/jenkins_home/hudson.model.UpdateCenter.xml
    break
  }
  sleep 2
done

# ==== 完成 ====
done_banner "Jenkins | http://localhost:${P}"
log_info "初始密码: cat ${DATA}/secrets/initialAdminPassword"
log_info "JDK8: Manage Jenkins → Tools → JDK → Name=JDK8, JAVA_HOME=${JDK_HOME}"
