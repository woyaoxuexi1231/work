#!/usr/bin/env bash

export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

# Jenkins LTS (挂载版) | Port: 8080(Web), 50000(Agent) | admin/123456
# 此脚本挂载数据目录到宿主机，容器删除后插件/配置/任务不丢失
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

C="jenkins"; I="jenkins/jenkins:${JENKINS_VERSION:-lts}"; P="${JENKINS_PORT:-8080}"; AP="${JENKINS_AGENT_PORT:-50000}"
DOCKER_DATA_ROOT="${DOCKER_DATA_ROOT:-/c/Users/15434/Desktop/docker-data}"
DATA="${DOCKER_DATA_ROOT}/jenkins-data"

check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

mkdir -p "${DATA}" || {
  log_error "无法创建 ${DATA}，请确认:"
  log_error "  1. 路径存在: C:\\Users\\15434\\Desktop\\docker-data\\"
  log_error "  2. Docker Desktop → Settings → Resources → File Sharing 已添加 C 盘"
  exit 1
}

pull_image "${I}"
docker run -d --name "${C}" --restart=unless-stopped \
  -p ${P}:8080 -p ${AP}:50000 \
  -e TZ=Asia/Shanghai \
  -e "JENKINS_UC=https://mirrors.tuna.tsinghua.edu.cn/jenkins/updates/update-center.json" \
  -v "${DATA}:/var/jenkins_home" \
  "${I}"
wait_for_container "${C}" 90

log_info "等待 Jenkins 初始化..."
for i in $(seq 1 45); do
  if docker exec "${C}" test -f /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null; then
    INIT_PASS=$(docker exec "${C}" cat /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null)
    log_info "Jenkins 已就绪"
    log_info "初始密码: ${INIT_PASS}"
    break
  fi
  sleep 2
done

done_banner "Jenkins (挂载版)"
log_info "URL: http://localhost:${P}  Agent: ${AP}"
log_info "数据目录: ${DATA}"
log_info "初始密码: cat ${DATA}/secrets/initialAdminPassword"
