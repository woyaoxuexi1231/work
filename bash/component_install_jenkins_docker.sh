#!/usr/bin/env bash
# Jenkins LTS | Port: 8080(Web), 50000(Agent) | admin/123456
set -euo pipefail; SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "${SCRIPT_DIR}/lib/common.sh"

C="jenkins"; I="jenkins/jenkins:${JENKINS_VERSION:-lts}"; P="${JENKINS_PORT:-8080}"; AP="${JENKINS_AGENT_PORT:-50000}"
U="${JENKINS_USER:-admin}"; PASS="${JENKINS_PASSWORD:-123456}"

check_docker; check_container_exists "${C}" && exit 0; cleanup_container "${C}"

pull_image "${I}"
docker run -d --name "${C}" --restart=unless-stopped \
  -p ${P}:8080 -p ${AP}:50000 \
  -e TZ=Asia/Shanghai \
  -e "JENKINS_OPTS=--httpPort=8080" \
  -e "JENKINS_UC=https://mirrors.tuna.tsinghua.edu.cn/jenkins/updates/update-center.json" \
  "${I}"
wait_for_container "${C}" 90

log_info "等待 Jenkins 初始化（首次需 30-60s）..."
for i in $(seq 1 45); do
  if curl -sf -o /dev/null "http://localhost:${P}/login" 2>/dev/null; then
    INIT_PASS=$(docker exec "${C}" cat /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null || echo "请手动查看")
    log_info "Jenkins 已就绪"
    log_info "初始密码: ${INIT_PASS}"
    break
  fi
  sleep 2
done

done_banner "Jenkins"
log_info "URL: http://localhost:${P}"
log_info "Agent Port: ${AP}"
log_info "初始密码获取: docker exec ${C} cat /var/jenkins_home/secrets/initialAdminPassword"
