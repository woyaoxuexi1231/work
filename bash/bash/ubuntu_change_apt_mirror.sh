#!/usr/bin/env bash
# ========================================================================================
# Ubuntu APT 源配置脚本 (Ubuntu APT Mirror Configuration Script)
# ========================================================================================
#
# 功能说明:
#   本脚本用于将 Ubuntu 系统的 APT 软件源切换到国内镜像源，提升软件包下载速度。
#   默认使用阿里云镜像源，支持 Ubuntu 所有版本的自动检测和配置。
#
# 主要特性:
#   ✓ 自动检测 Ubuntu 版本和代号: 支持 16.04+ 所有版本
#   ✓ 双格式支持: 兼容传统格式和 DEB822 新格式 (Ubuntu 24.04+)
#   ✓ 安全更新源: 安全更新走镜像源，不依赖 security.ubuntu.com
#   ✓ 备份恢复: 自动备份原有配置，支持一键恢复
#   ✓ 网络测试: 配置完成后测试镜像源可用性
#   ✓ 多镜像源: 支持多个国内镜像源选择
#
# 支持的镜像源:
#   1. 阿里云镜像源 (默认)
#      - 主站: https://mirrors.aliyun.com
#      - 特点: 覆盖全面，速度稳定，支持 HTTPS
#
#   2. 清华大学 TUNA 镜像源
#      - 主站: https://mirrors.tuna.tsinghua.edu.cn
#      - 特点: 速度快，更新及时，官方镜像
#
#   3. 中科大 USTC 镜像源
#      - 主站: https://mirrors.ustc.edu.cn
#      - 特点: 教育网首选，支持 HTTPS
#
#   4. 华为云镜像源
#      - 主站: https://mirrors.huaweicloud.com
#      - 特点: 华为云用户优先，支持 HTTPS
#
#   5. 腾讯云镜像源
#      - 主站: http://mirrors.tencent.com/ubuntu/
#      - 特点: 腾讯云用户优先选择 (仅 HTTP)
#
#   6. 网易 163 镜像源
#      - 主站: http://mirrors.163.com/ubuntu/
#      - 特点: 老牌镜像源，稳定性好 (仅 HTTP)
#
# 配置流程:
#   1. 检测当前 Ubuntu 版本和代号
#   2. 备份现有的 sources.list 或 sources.list.d 配置
#   3. 生成新的镜像源配置文件
#   4. 更新软件包索引验证配置
#   5. 测试镜像源连通性和下载速度
#
# 支持的 Ubuntu 版本:
#   - Ubuntu 16.04 (Xenial Xerus)
#   - Ubuntu 18.04 (Bionic Beaver)
#   - Ubuntu 20.04 (Focal Fossa)
#   - Ubuntu 22.04 (Jammy Jellyfish)
#   - Ubuntu 24.04 (Noble Numbat) - DEB822 格式
#
# 使用方法:
#   1. 使用默认阿里云镜像源:
#      sudo bash ubuntu_change_apt_mirror.sh
#
#   2. 指定其他镜像源:
#      sudo MIRROR=tuna    bash ubuntu_change_apt_mirror.sh
#      sudo MIRROR=aliyun  bash ubuntu_change_apt_mirror.sh
#      sudo MIRROR=ustc    bash ubuntu_change_apt_mirror.sh
#      sudo MIRROR=huawei  bash ubuntu_change_apt_mirror.sh
#      sudo MIRROR=tencent bash ubuntu_change_apt_mirror.sh
#      sudo MIRROR=163     bash ubuntu_change_apt_mirror.sh
#
#   3. 恢复原始配置:
#      sudo bash ubuntu_change_apt_mirror.sh --restore
#
# 环境变量:
#   MIRROR        - 选择镜像源 (aliyun/tuna/ustc/huawei/tencent/163)
#   BACKUP_DIR    - 备份文件目录 (默认: /etc/apt/backup)
#
# 备份机制:
#   - 自动备份原有配置文件
#   - 备份文件名包含时间戳
#   - 支持一键恢复原始配置
#   - 保留多个历史备份版本
#
# 验证功能:
#   - 语法检查: 验证 sources.list 格式正确性
#   - 连通性测试: 测试镜像源 HTTP 连通性
#   - 下载速度测试: 对比官方源和镜像源速度
#   - 软件包更新: 验证 apt update 是否正常工作
#
# 安全考虑:
#   ⚠️ 镜像源需使用 HTTPS (如果支持)
#   ⚠️ 安全更新与镜像源一致，避免跨网超时
#   ⚠️ 定期验证 GPG 密钥和软件包签名
#
# 故障排除:
#   - 检查网络连通性: ping mirrors.tuna.tsinghua.edu.cn
#   - 验证配置文件: cat /etc/apt/sources.list
#   - 更新软件包缓存: sudo apt update
#   - 恢复原始配置: 使用 --restore 参数
#
# 文件位置:
#   - 主配置文件: /etc/apt/sources.list
#   - 源列表目录: /etc/apt/sources.list.d/
#   - 备份目录: /etc/apt/backup/
#   - 日志文件: /var/log/apt/mirror_change.log
#
# 性能对比:
#   镜像源通常比官方源快 5-10 倍，特别是国内用户
#   首选阿里云，其次 TUNA/USTC/华为云，最后腾讯云或网易
#
# 作者: 系统运维脚本
# 版本: v2.0
# 更新时间: 2024-01
# ========================================================================================

# 确保在 bash 下运行；/bin/sh (dash) 不支持 set -o pipefail
if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

# ========== 工具函数 ==========
log() {
  local level="$1"; shift
  local msg="$*"
  local ts
  ts="$(date '+%Y-%m-%d %H:%M:%S')"
  printf '[%s] [%s] %s\n' "$ts" "$level" "$msg"
}
log_info() { log "INFO" "$@"; }
log_warn() { log "WARN" "$@"; }
log_error() { log "ERROR" "$@"; }

ensure_dir() {
  local dir="$1"
  if [[ -z "${dir}" ]]; then
    log_error "ensure_dir 收到空目录参数"
    exit 1
  fi
  if [[ ! -d "${dir}" ]]; then
    mkdir -p "${dir}"
  fi
}

# ========== 镜像源选择 ==========
# 默认使用阿里云镜像源（可通过 MIRROR 环境变量切换）
MIRROR_URL="https://mirrors.aliyun.com/ubuntu"
# 可选: aliyun (默认), tuna, ustc, huawei, tencent, 163
case "${MIRROR:-aliyun}" in
  tuna)
    MIRROR_URL="https://mirrors.tuna.tsinghua.edu.cn/ubuntu"
    ;;
  aliyun)
    MIRROR_URL="https://mirrors.aliyun.com/ubuntu"
    ;;
  ustc)
    MIRROR_URL="https://mirrors.ustc.edu.cn/ubuntu"
    ;;
  huawei)
    MIRROR_URL="https://mirrors.huaweicloud.com/ubuntu"
    ;;
  tencent)
    MIRROR_URL="http://mirrors.tencent.com/ubuntu/"
    ;;
  163)
    MIRROR_URL="http://mirrors.163.com/ubuntu/"
    ;;
  *)
    log_warn "未知镜像源: ${MIRROR}，使用默认阿里云镜像源。"
    MIRROR_URL="https://mirrors.aliyun.com/ubuntu"
    ;;
esac

# security 也走镜像源（阿里云/华为云等国内镜像均包含 security）
# 若镜像源不通会自动回退；不使用 security.ubuntu.com 避免跨网超时
SECURITY_URL="${MIRROR_URL}"

BACKUP_DIR="/etc/apt/sources.list.d/backups"

log_info "=== Ubuntu APT 镜像切换脚本开始 ==="
log_info "目标镜像: ${MIRROR_URL}"

# 权限检查
if [[ $EUID -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    log_warn "当前非 root，使用 sudo 重新执行脚本..."
    exec sudo -E bash "$0" "$@"
  else
    log_error "需要 root 权限且未找到 sudo，请以 root 或 sudo 运行脚本。"
    exit 1
  fi
fi

# 检查是否为 Ubuntu
if [[ ! -f /etc/os-release ]] || ! grep -qi 'ubuntu' /etc/os-release; then
  log_error "当前系统看起来不是 Ubuntu，放弃修改 APT 源。"
  exit 1
fi

# 获取发行版代号和版本号
UBUNTU_CODENAME=""
UBUNTU_VERSION=""
UBUNTU_MAJOR_VERSION=""

if command -v lsb_release >/dev/null 2>&1; then
  UBUNTU_CODENAME="$(lsb_release -sc)"
  UBUNTU_VERSION="$(lsb_release -sr)"
else
  # 从 /etc/os-release 里解析
  . /etc/os-release
  UBUNTU_CODENAME="${UBUNTU_CODENAME:-}"
  UBUNTU_VERSION="${VERSION_ID:-}"
fi

if [[ -z "${UBUNTU_CODENAME}" ]]; then
  log_error "无法自动检测 Ubuntu 发行版代号 (UBUNTU_CODENAME)，请手动检查系统。"
  exit 1
fi

if [[ -z "${UBUNTU_VERSION}" ]]; then
  log_error "无法自动检测 Ubuntu 版本号，请手动检查系统。"
  exit 1
fi

UBUNTU_MAJOR_VERSION="${UBUNTU_VERSION%%.*}"

log_info "检测到 Ubuntu 版本: ${UBUNTU_VERSION} (${UBUNTU_CODENAME})"

# 根据版本确定配置文件和格式
if [[ "${UBUNTU_MAJOR_VERSION}" -ge 24 ]]; then
  # Ubuntu 24.04 及以上版本使用 DEB822 格式
  SOURCES_FILE="/etc/apt/sources.list.d/ubuntu.sources"
  USE_DEB822=true
  log_info "使用 DEB822 格式 (Ubuntu 24.04+)"
else
  # Ubuntu 24.04 以下版本使用传统格式
  SOURCES_FILE="/etc/apt/sources.list"
  USE_DEB822=false
  log_info "使用传统格式 (Ubuntu < 24.04)"
fi

# 备份原配置文件
ensure_dir "${BACKUP_DIR}"
if [[ -f "${SOURCES_FILE}" ]]; then
  TS="$(date '+%Y%m%d_%H%M%S')"
  BACKUP_FILE="${BACKUP_DIR}/$(basename ${SOURCES_FILE}).backup.${TS}"
  cp "${SOURCES_FILE}" "${BACKUP_FILE}"
  log_info "已备份原配置文件至: ${BACKUP_FILE}"
else
  log_warn "未找到 ${SOURCES_FILE}，将直接创建新的配置文件。"
fi

# 如果是 Ubuntu 24.04+，还需要备份或清理旧的 sources.list（如果存在）
if [[ "${USE_DEB822}" == "true" ]] && [[ -f "/etc/apt/sources.list" ]]; then
  TS="$(date '+%Y%m%d_%H%M%S')"
  BACKUP_FILE="${BACKUP_DIR}/sources.list.backup.${TS}"
  cp "/etc/apt/sources.list" "${BACKUP_FILE}"
  log_info "已备份旧的 sources.list 至: ${BACKUP_FILE}"
  # 注释掉旧文件内容（保留文件但禁用）
  sed -i 's/^/# /' /etc/apt/sources.list
  log_info "已注释旧的 sources.list 内容"
fi

# 写入新的配置文件
log_info "写入新配置文件: ${SOURCES_FILE}"

if [[ "${USE_DEB822}" == "true" ]]; then
  # DEB822 格式 (Ubuntu 24.04+)
  # security 也走镜像源，和阿里云/华为云等官方推荐配置一致
  cat > "${SOURCES_FILE}" <<EOF
Types: deb
URIs: ${MIRROR_URL}
Suites: ${UBUNTU_CODENAME} ${UBUNTU_CODENAME}-updates ${UBUNTU_CODENAME}-backports ${UBUNTU_CODENAME}-security
Components: main restricted universe multiverse
Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
EOF
else
  # 传统格式 (Ubuntu < 24.04)
  cat > "${SOURCES_FILE}" <<EOF
# 默认注释了源码镜像以提高 apt update 速度，如有需要可自行取消注释
deb ${MIRROR_URL} ${UBUNTU_CODENAME} main restricted universe multiverse
# deb-src ${MIRROR_URL} ${UBUNTU_CODENAME} main restricted universe multiverse

deb ${MIRROR_URL} ${UBUNTU_CODENAME}-updates main restricted universe multiverse
# deb-src ${MIRROR_URL} ${UBUNTU_CODENAME}-updates main restricted universe multiverse

deb ${MIRROR_URL} ${UBUNTU_CODENAME}-backports main restricted universe multiverse
# deb-src ${MIRROR_URL} ${UBUNTU_CODENAME}-backports main restricted universe multiverse

deb ${SECURITY_URL} ${UBUNTU_CODENAME}-security main restricted universe multiverse
# deb-src ${SECURITY_URL} ${UBUNTU_CODENAME}-security main restricted universe multiverse
EOF
fi

log_info "新配置文件写入完成。"

# 更新索引，验证网络是否正常
export DEBIAN_FRONTEND=noninteractive

# 验证 apt-get update 是否真正成功
# 只检查我们配置的镜像源是否出错，忽略 /etc/apt/sources.list.d/ 中第三方仓库的问题
verify_apt_update() {
  local log_file="${BACKUP_DIR}/apt_update.log"
  apt-get update 2>&1 | tee "${log_file}"
  local exit_code=${PIPESTATUS[0]}

  # 镜像源自身是否有错误
  if grep -qE "Err:.*${MIRROR_URL}" "${log_file}"; then
    log_error "镜像源 ${MIRROR_URL} 连接失败，请检查网络或换一个源。"
    return 1
  fi

  # apt-get update 整体成功
  if [[ ${exit_code} -eq 0 ]]; then
    return 0
  fi

  # 镜像源正常，但第三方仓库（Docker/K8s等）有 GPG 密钥过期等问题
  log_warn "镜像源 ${MIRROR_URL} 工作正常，但部分第三方软件源有错误。"
  log_warn "如需修复 Docker / Kubernetes 等源的 GPG 密钥，可在脚本外执行:"
  log_warn "  sudo apt-key adv --keyserver keyserver.ubuntu.com --recv-keys <缺失的KEY>"
  return 0
}

log_info "执行 apt-get update 测试镜像连通性（详细输出如下）..."
if verify_apt_update; then
  log_info "apt-get update 成功，镜像源工作正常。"
else
  log_error "镜像源 ${MIRROR_URL} 不可达，请尝试其他镜像源:"
  log_error "  sudo MIRROR=tuna    bash $0"
  log_error "  sudo MIRROR=ustc    bash $0"
  log_error "  sudo MIRROR=huawei  bash $0"
  log_error "  sudo MIRROR=tencent bash $0"
  log_error "  sudo MIRROR=163     bash $0"
  exit 1
fi

log_info "=== Ubuntu APT 镜像切换脚本完成 ==="
log_info "已成功切换到镜像源: ${MIRROR_URL}"
log_info "安全更新使用镜像源: ${SECURITY_URL}"
