#!/usr/bin/env bash
# 基础服务批量安装 | 用法: bash $0 [--all] [--services minio,redis,mysql]
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

# 可用服务
declare -A SERVICES=(
  ["minio"]="${SCRIPT_DIR}/component_install_minio_docker.sh"
  ["redis"]="${SCRIPT_DIR}/component_install_redis_docker.sh"
  ["mysql"]="${SCRIPT_DIR}/component_install_mysql_docker.sh"
  ["mongo"]="${SCRIPT_DIR}/component_install_mongo_docker.sh"
  ["elasticsearch"]="${SCRIPT_DIR}/component_install_elasticsearch_docker.sh"
  ["zookeeper"]="${SCRIPT_DIR}/component_install_zookeeper_docker.sh"
)
ALL_SERVICES=("minio" "redis" "mysql" "mongo" "elasticsearch" "zookeeper")

require_root
check_docker

# parse args
TO_INSTALL=("${ALL_SERVICES[@]}")
while [[ $# -gt 0 ]]; do
  case $1 in
    --all)   shift ;;
    --services) IFS=',' read -ra TO_INSTALL <<< "$2"; shift 2 ;;
    --help)  echo "Usage: $0 [--all] [--services s1,s2,...]"; echo "Available: ${ALL_SERVICES[*]}"; exit 0 ;;
    *)       log_error "未知: $1, 用 --help 查看"; exit 1 ;;
  esac
done

log_info "安装服务: ${TO_INSTALL[*]}"

total=${#TO_INSTALL[@]}; i=0
for svc in "${TO_INSTALL[@]}"; do
  ((i++))
  log_info "[${i}/${total}] ${svc}"
  [[ -z "${SERVICES[$svc]:-}" ]] && { log_warn "跳过: ${svc}"; continue; }
  bash "${SERVICES[$svc]}" || { log_error "${svc} 失败"; exit 1; }
  [[ $i -lt $total ]] && sleep 3
done

log_info "批量安装完成: ${TO_INSTALL[*]}"
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
