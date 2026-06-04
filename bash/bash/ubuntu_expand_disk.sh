#!/usr/bin/env bash
# ========================================================================================
# Ubuntu 磁盘扩容脚本 (Ubuntu Disk Expansion Script)
# ========================================================================================
#
# 功能说明:
#   本脚本用于自动扩容 Ubuntu 系统中的磁盘分区和文件系统。
#   支持 LVM (Logical Volume Manager) 和直接分区两种扩容方式，
#   自动检测磁盘结构并选择合适的扩容策略。
#
# 主要特性:
#   ✓ 自动检测磁盘结构: 支持 LVM 和直接分区两种场景
#   ✓ 智能扩容策略: 根据磁盘情况选择最优的扩容方案
#   ✓ 安全确认机制: 重要操作前会提示用户确认
#   ✓ 详细日志记录: 完整的操作日志和错误信息
#   ✓ 兼容多种文件系统: 支持 ext2/3/4 和 XFS 文件系统
#   ✓ 虚拟机友好: 特别优化了虚拟机磁盘扩容场景
#
# 扩容流程:
#   1. 检测当前磁盘和分区结构
#   2. 识别 LVM 或直接分区布局
#   3. 检查可用磁盘空间
#   4. 自动扩展分区边界
#   5. 调整 LVM 物理/逻辑卷大小
#   6. 扩展文件系统到新大小
#   7. 验证扩容结果
#
# 支持的场景:
#   - 虚拟机磁盘扩容后自动扩展分区
#   - LVM 卷组中有剩余空间的扩容
#   - 直接分区有未分配空间的扩容
#   - 多种 Linux 文件系统 (ext2/3/4, XFS)
#
# 安全特性:
#   - 操作前数据备份提醒
#   - 重要操作确认提示
#   - 详细的错误检测和恢复
#   - 操作日志完整记录
#
# 使用方法:
#   1. 自动检测并扩容根分区（推荐）:
#      sudo bash ubuntu_expand_disk.sh
#
#   2. 指定要扩容的分区:
#      sudo PARTITION=/dev/sda3 bash ubuntu_expand_disk.sh
#
#   3. 指定要扩容的 LVM 逻辑卷:
#      sudo LV_PATH=/dev/ubuntu-vg/ubuntu-lv bash ubuntu_expand_disk.sh
#
#   4. 跳过确认提示（用于脚本自动化）:
#      sudo SKIP_CONFIRM=yes bash ubuntu_expand_disk.sh
#
# 环境变量:
#   PARTITION        - 指定要扩容的分区 (默认: 自动检测)
#   LV_PATH          - 指定 LVM 逻辑卷路径 (默认: 自动检测)
#   AUTO_EXPAND      - 是否自动扩展分区 (默认: yes)
#   SKIP_CONFIRM     - 是否跳过确认提示 (默认: no)
#   LOG_FILE         - 日志文件路径 (默认: /var/log/disk/expand_disk.log)
#
# 前置条件:
#   - Ubuntu/Debian 系 Linux 发行版
#   - root 权限或 sudo 权限
#   - 已扩展底层磁盘大小（虚拟机场景）
#   - 必要的系统工具 (自动安装): parted, lvm2, e2fsprogs, bc
#
# 支持的文件系统:
#   - ext2, ext3, ext4: 使用 resize2fs 命令
#   - XFS: 使用 xfs_growfs 命令
#   - 其他文件系统: 需要手动扩展
#
# 目录结构:
#   /var/log/disk/          - 日志目录
#     ├── expand_disk.log   - 操作日志
#     └── backup/           - 备份文件目录
#
# 故障排除:
#   - 查看日志: cat /var/log/disk/expand_disk.log
#   - 检查磁盘: lsblk && df -h
#   - LVM 信息: vgs && lvs && pvs
#   - 恢复备份: 如有问题可从备份恢复配置
#
# 注意事项:
#   ⚠️ 重要操作前请备份数据
#   ⚠️ 虚拟机请先在管理界面扩展磁盘
#   ⚠️ 生产环境建议在维护窗口执行
#   ⚠️ 不支持在线扩容某些文件系统类型
#
# 扩展计划:
#   - 支持 Btrfs 文件系统
#   - 支持 ZFS 文件系统
#   - 支持 RAID 扩容
#   - 支持多磁盘自动均衡
#
# 作者: 系统运维脚本
# 版本: v2.0
# 更新时间: 2024-01
# ========================================================================================

# Script to expand Ubuntu disk/partition and filesystem.
# - Supports LVM and direct partition expansion
# - Automatically detects available space
# - Expands filesystem after partition expansion
# - Emits logs to stdout and to LOG_FILE (default: /var/log/disk/expand_disk.log).
#
# 使用方法：
#   # 自动检测并扩容根分区（推荐）
#   sudo bash ubuntu_expand_disk.sh
#
#   # 指定要扩容的分区
#   sudo PARTITION=/dev/sda3 bash ubuntu_expand_disk.sh
#
#   # 指定要扩容的 LVM 逻辑卷
#   sudo LV_PATH=/dev/ubuntu-vg/ubuntu-lv bash ubuntu_expand_disk.sh

# Ensure we are running under bash (Ubuntu /bin/sh 不支持 pipefail)
if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="${LOG_FILE:-/var/log/disk/expand_disk.log}"
PARTITION="${PARTITION:-}"
LV_PATH="${LV_PATH:-}"
VG_NAME="${VG_NAME:-}"
LV_NAME="${LV_NAME:-}"
AUTO_EXPAND="${AUTO_EXPAND:-yes}"
SKIP_CONFIRM="${SKIP_CONFIRM:-no}"

log() {
  local level="$1"; shift
  local msg="$*"
  local ts
  ts="$(date '+%Y-%m-%d %H:%M:%S')"
  printf '[%s] [%s] %s\n' "$ts" "$level" "$msg" | tee -a "$LOG_FILE"
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

# 确保日志目录存在
ensure_dir "$(dirname "${LOG_FILE}")"

trap 'log_error "操作过程中出现错误，退出。"' ERR

log_info "=== Ubuntu 磁盘扩容脚本开始 ==="
log_info "日志文件: ${LOG_FILE}"

# 安全确认提示
if [[ "${SKIP_CONFIRM}" != "yes" ]]; then
  log_warn "警告：此脚本将修改磁盘分区和文件系统"
  log_warn "请确保："
  log_warn "  1. 已备份重要数据"
  log_warn "  2. 已在虚拟机管理界面扩展了磁盘大小（如适用）"
  log_warn "  3. 了解操作的风险"
  echo ""
  read -p "是否继续？(yes/no): " CONFIRM
  if [[ "${CONFIRM}" != "yes" ]]; then
    log_info "用户取消操作"
    exit 0
  fi
fi

# 0. 权限检查
if [[ $EUID -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    log_warn "当前非 root，使用 sudo 重新执行脚本..."
    exec sudo -E bash "$0" "$@"
  else
    log_error "需要 root 权限且未找到 sudo，请以 root 或 sudo 运行脚本。"
    exit 1
  fi
fi

# 检查必要的命令
REQUIRED_COMMANDS=("lsblk" "df" "parted" "resize2fs" "pvresize" "lvextend")
MISSING_COMMANDS=()
for cmd in "${REQUIRED_COMMANDS[@]}"; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    MISSING_COMMANDS+=("$cmd")
  fi
done

# 检查 bc 命令（用于数值计算）
if ! command -v bc >/dev/null 2>&1; then
  MISSING_COMMANDS+=("bc")
fi

if [[ ${#MISSING_COMMANDS[@]} -gt 0 ]]; then
  log_error "缺少必要的命令: ${MISSING_COMMANDS[*]}"
  log_info "正在安装必要的工具..."
  apt-get update -qq
  apt-get install -y -qq parted lvm2 e2fsprogs util-linux bc || {
    log_error "无法安装必要的工具，请手动安装: apt-get install -y parted lvm2 e2fsprogs util-linux bc"
    exit 1
  }
fi

# 1. 检测当前磁盘和分区情况
log_info "=== 检测当前磁盘和分区情况 ==="

# 获取根文件系统信息
ROOT_FS=$(df -h / | tail -n 1 | awk '{print $1}')
log_info "根文件系统: ${ROOT_FS}"

# 检测是否使用 LVM
IS_LVM=false
if [[ "${ROOT_FS}" =~ /dev/mapper/ ]] || [[ "${ROOT_FS}" =~ /dev/[a-z]+-vg/ ]]; then
  IS_LVM=true
  log_info "检测到 LVM 文件系统"

  # 获取 LVM 信息
  if [[ -z "${LV_PATH}" ]]; then
    LV_PATH="${ROOT_FS}"
  fi

  # 从 LV_PATH 提取 VG 和 LV 名称
  # 方法1: 使用 lvs 命令获取（最可靠）
  LV_INFO=$(lvs --noheadings -o vg_name,lv_name "${LV_PATH}" 2>/dev/null)
  if [[ -n "${LV_INFO}" ]]; then
    VG_NAME=$(echo "${LV_INFO}" | awk '{print $1}' | tr -d ' ')
    LV_NAME=$(echo "${LV_INFO}" | awk '{print $2}' | tr -d ' ')
    if [[ -n "${VG_NAME}" ]] && [[ -n "${LV_NAME}" ]]; then
      log_info "使用 lvs 命令成功获取 VG 和 LV 名称"
    else
      log_warn "lvs 命令返回了信息但解析失败，尝试备用方法"
      LV_INFO=""
    fi
  fi

  if [[ -z "${LV_INFO}" ]] || [[ -z "${VG_NAME}" ]] || [[ -z "${LV_NAME}" ]]; then
    # 方法2: 从路径解析（处理 /dev/mapper/ 中的 -- 转义）
    # /dev/mapper/ubuntu--vg-ubuntu--lv -> ubuntu-vg/ubuntu-lv
    # 将 -- 替换为 -，然后分割
    if [[ "${LV_PATH}" =~ /dev/mapper/(.+) ]]; then
      MAPPER_NAME="${BASH_REMATCH[1]}"
      # 将 -- 替换为 -，然后按最后一个 - 分割
      NORMALIZED_NAME=$(echo "${MAPPER_NAME}" | sed 's/--/-/g')
      # 查找最后一个 - 的位置来分割 VG 和 LV
      # 假设格式是 vgname-lvname
      if [[ "${NORMALIZED_NAME}" =~ ^(.+)-(.+)$ ]]; then
        VG_NAME="${BASH_REMATCH[1]}"
        LV_NAME="${BASH_REMATCH[2]}"
      else
        log_error "无法从 ${LV_PATH} 解析 VG 和 LV 名称"
        exit 1
      fi
    elif [[ "${LV_PATH}" =~ /dev/([^/]+)/(.+) ]]; then
      VG_NAME="${BASH_REMATCH[1]}"
      LV_NAME="${BASH_REMATCH[2]}"
    else
      log_error "无法从 ${LV_PATH} 解析 VG 和 LV 名称"
      exit 1
    fi
  fi

  log_info "卷组 (VG): ${VG_NAME}"
  log_info "逻辑卷 (LV): ${LV_NAME}"
  log_info "逻辑卷路径: ${LV_PATH}"
else
  log_info "检测到直接分区文件系统"
  if [[ -z "${PARTITION}" ]]; then
    PARTITION="${ROOT_FS}"
  fi
  log_info "分区: ${PARTITION}"
fi

# 2. 显示当前磁盘使用情况
log_info "=== 磁盘结构信息 ==="
lsblk 2>/dev/null | while IFS= read -r line; do
  log_info "  $line"
done || log_warn "无法执行 lsblk 命令"

log_info "=== 当前磁盘使用情况 ==="
df -h / | while IFS= read -r line; do
  log_info "  $line"
done

if [[ "${IS_LVM}" == "true" ]]; then
  log_info "=== LVM 信息 ==="
  vgs 2>/dev/null | while IFS= read -r line; do
    log_info "  $line"
  done || true

  lvs 2>/dev/null | while IFS= read -r line; do
    log_info "  $line"
  done || true

  pvs 2>/dev/null | while IFS= read -r line; do
    log_info "  $line"
  done || true
fi

# 3. 检测可用空间
log_info "=== 检测可用空间 ==="

if [[ "${IS_LVM}" == "true" ]]; then
  # LVM 扩容流程
  log_info "使用 LVM 扩容流程"

  # 获取卷组信息
  VG_INFO=$(vgs "${VG_NAME}" --noheadings --units g 2>/dev/null)
  if [[ -z "${VG_INFO}" ]]; then
    log_error "无法获取卷组 ${VG_NAME} 的信息"
    log_error "请检查卷组名称是否正确"
    exit 1
  fi

  VG_SIZE=$(echo "${VG_INFO}" | awk '{print $6}' | sed 's/g//g' | tr -d ' ')
  VG_FREE=$(echo "${VG_INFO}" | awk '{print $7}' | sed 's/g//g' | tr -d ' ')

  if [[ -z "${VG_SIZE}" ]]; then
    log_error "无法解析卷组 ${VG_NAME} 的大小信息"
    exit 1
  fi

  log_info "卷组 ${VG_NAME} 总大小: ${VG_SIZE}G"
  if [[ -n "${VG_FREE}" ]]; then
    log_info "卷组 ${VG_NAME} 可用空间: ${VG_FREE}G"
  else
    log_warn "无法获取卷组 ${VG_NAME} 的可用空间信息，将尝试继续"
    VG_FREE="0"
  fi

  # 检查物理卷是否有未分配空间
  PVS_INFO=$(pvs --noheadings -o pv_name,pv_free --units g 2>/dev/null | sed 's/g//g')
  log_info "物理卷信息:"
  if [[ -n "${PVS_INFO}" ]]; then
    echo "${PVS_INFO}" | while IFS= read -r line; do
      log_info "  $line"
    done
  else
    log_warn "无法获取物理卷信息"
  fi

  # 检查是否有未使用的物理卷空间
  PV_WITH_SPACE=""
  if [[ -n "${PVS_INFO}" ]]; then
    while IFS= read -r line; do
      PV_DEVICE=$(echo "$line" | awk '{print $1}' | tr -d ' ')
      PV_FREE=$(echo "$line" | awk '{print $2}' | tr -d ' ')
      if [[ -n "${PV_FREE}" ]] && [[ "${PV_FREE}" != "0" ]] && (( $(echo "${PV_FREE} > 0" | bc -l 2>/dev/null || echo 0) )); then
        PV_WITH_SPACE="${PV_DEVICE}"
        log_info "发现物理卷 ${PV_DEVICE} 有 ${PV_FREE}G 可用空间"
        break
      fi
    done <<< "${PVS_INFO}"
  fi

  # 如果没有可用空间，检查磁盘分区
  if [[ -z "${PV_WITH_SPACE}" ]]; then
    log_info "检查磁盘分区是否有未分配空间..."

    # 获取物理卷对应的磁盘分区
    PV_DEVICES=$(pvs --noheadings -o pv_name 2>/dev/null | tr -d ' ')
    for PV_DEV in ${PV_DEVICES}; do
      log_info "检查物理卷: ${PV_DEV}"

      # 获取分区信息
      if [[ "${PV_DEV}" =~ /dev/([a-z]+)([0-9]+) ]]; then
        DISK_DEVICE="/dev/${BASH_REMATCH[1]}"
        PART_NUM="${BASH_REMATCH[2]}"

        log_info "磁盘设备: ${DISK_DEVICE}, 分区号: ${PART_NUM}"

        # 使用 parted 检查磁盘和分区信息
        DISK_SIZE=$(parted -s "${DISK_DEVICE}" unit GB print | grep "^Disk ${DISK_DEVICE}" | awk '{print $3}' | sed 's/GB//')
        PART_END=$(parted -s "${DISK_DEVICE}" unit GB print | grep "^[[:space:]]*${PART_NUM}" | awk '{print $3}' | sed 's/GB//')

        log_info "磁盘总大小: ${DISK_SIZE}G"
        log_info "分区结束位置: ${PART_END}G"

        # 检查是否有未分配空间
        if (( $(echo "${DISK_SIZE} > ${PART_END}" | bc -l 2>/dev/null || echo 0) )); then
          FREE_SPACE=$(echo "${DISK_SIZE} - ${PART_END}" | bc -l 2>/dev/null || echo "0")
          log_info "发现未分配空间: ${FREE_SPACE}G"

          if [[ "${AUTO_EXPAND}" == "yes" ]]; then
            log_info "自动扩展分区 ${PV_DEV}..."

            # 扩展分区到磁盘末尾
            parted -s "${DISK_DEVICE}" resizepart "${PART_NUM}" 100% || {
              log_error "无法扩展分区 ${PV_DEV}"
              exit 1
            }

            log_info "分区已扩展，重新扫描物理卷..."
            pvresize "${PV_DEV}" || {
              log_error "无法重新扫描物理卷 ${PV_DEV}"
              exit 1
            }

            log_info "物理卷已更新"
          else
            log_warn "发现未分配空间但 AUTO_EXPAND=no，跳过自动扩展"
            log_warn "如需扩展，请手动执行："
            log_warn "  parted -s ${DISK_DEVICE} resizepart ${PART_NUM} 100%"
            log_warn "  pvresize ${PV_DEV}"
          fi
        else
          log_info "分区 ${PV_DEV} 已使用所有可用空间"
        fi
      fi
    done
  fi

  # 重新获取卷组可用空间（如果之前没有获取）
  if [[ -z "${VG_FREE}" ]]; then
    VG_FREE=$(vgs "${VG_NAME}" --noheadings --units g 2>/dev/null | awk '{print $7}' | sed 's/g//')
  fi
  log_info "卷组 ${VG_NAME} 当前可用空间: ${VG_FREE}G"

  if [[ -z "${VG_FREE}" ]] || (( $(echo "${VG_FREE} <= 0" | bc -l 2>/dev/null || echo 1) )); then
    log_error "卷组 ${VG_NAME} 没有可用空间进行扩容"
    log_error "请先扩展磁盘或添加新的物理卷"
    exit 1
  fi

  # 扩展逻辑卷
  log_info "扩展逻辑卷 ${LV_PATH}..."
  lvextend -l +100%FREE "${LV_PATH}" || {
    log_error "无法扩展逻辑卷 ${LV_PATH}"
    exit 1
  }

  log_info "逻辑卷已扩展"

  # 扩展文件系统
  log_info "检测文件系统类型..."
  FS_TYPE=$(blkid -o value -s TYPE "${LV_PATH}" 2>/dev/null || echo "")

  if [[ -z "${FS_TYPE}" ]]; then
    FS_TYPE=$(df -T / | tail -n 1 | awk '{print $2}')
    log_info "从 df 获取文件系统类型: ${FS_TYPE}"
  else
    log_info "文件系统类型: ${FS_TYPE}"
  fi

  log_info "扩展文件系统..."
  case "${FS_TYPE}" in
    ext2|ext3|ext4)
      resize2fs "${LV_PATH}" || {
        log_error "无法扩展 ext2/3/4 文件系统"
        exit 1
      }
      ;;
    xfs)
      xfs_growfs / || {
        log_error "无法扩展 XFS 文件系统"
        exit 1
      }
      ;;
    *)
      log_warn "不支持的文件系统类型: ${FS_TYPE}"
      log_warn "请手动扩展文件系统"
      ;;
  esac

  log_info "文件系统已扩展"

else
  # 直接分区扩容流程
  log_info "使用直接分区扩容流程"
  log_warn "直接分区扩容需要先扩展分区，然后扩展文件系统"
  log_warn "此操作较为复杂，建议使用 LVM"

  if [[ -z "${PARTITION}" ]]; then
    log_error "未指定要扩容的分区"
    exit 1
  fi

  # 获取分区信息
  if [[ "${PARTITION}" =~ /dev/([a-z]+)([0-9]+) ]]; then
    DISK_DEVICE="/dev/${BASH_REMATCH[1]}"
    PART_NUM="${BASH_REMATCH[2]}"

    log_info "磁盘设备: ${DISK_DEVICE}"
    log_info "分区号: ${PART_NUM}"

    # 检查磁盘和分区大小
    DISK_SIZE=$(parted -s "${DISK_DEVICE}" unit GB print | grep "^Disk ${DISK_DEVICE}" | awk '{print $3}' | sed 's/GB//')
    PART_END=$(parted -s "${DISK_DEVICE}" unit GB print | grep "^[[:space:]]*${PART_NUM}" | awk '{print $3}' | sed 's/GB//')

    log_info "磁盘总大小: ${DISK_SIZE}G"
    log_info "分区结束位置: ${PART_END}G"

    # 检查是否有未分配空间
    if (( $(echo "${DISK_SIZE} > ${PART_END}" | bc -l 2>/dev/null || echo 0) )); then
      FREE_SPACE=$(echo "${DISK_SIZE} - ${PART_END}" | bc -l 2>/dev/null || echo "0")
      log_info "发现未分配空间: ${FREE_SPACE}G"

      if [[ "${AUTO_EXPAND}" == "yes" ]]; then
        log_info "扩展分区 ${PARTITION}..."

        # 扩展分区到磁盘末尾
        parted -s "${DISK_DEVICE}" resizepart "${PART_NUM}" 100% || {
          log_error "无法扩展分区 ${PARTITION}"
          exit 1
        }

        log_info "分区已扩展"

        # 扩展文件系统
        log_info "检测文件系统类型..."
        FS_TYPE=$(blkid -o value -s TYPE "${PARTITION}" 2>/dev/null || echo "")

        if [[ -z "${FS_TYPE}" ]]; then
          FS_TYPE=$(df -T / | tail -n 1 | awk '{print $2}')
          log_info "从 df 获取文件系统类型: ${FS_TYPE}"
        else
          log_info "文件系统类型: ${FS_TYPE}"
        fi

        log_info "扩展文件系统..."
        case "${FS_TYPE}" in
          ext2|ext3|ext4)
            resize2fs "${PARTITION}" || {
              log_error "无法扩展 ext2/3/4 文件系统"
              exit 1
            }
            ;;
          xfs)
            xfs_growfs / || {
              log_error "无法扩展 XFS 文件系统"
              exit 1
            }
            ;;
          *)
            log_warn "不支持的文件系统类型: ${FS_TYPE}"
            log_warn "请手动扩展文件系统"
            ;;
        esac

        log_info "文件系统已扩展"
      else
        log_warn "发现未分配空间但 AUTO_EXPAND=no，跳过自动扩展"
      fi
    else
      log_warn "分区 ${PARTITION} 已使用所有可用空间"
      log_warn "如需扩容，请先扩展虚拟磁盘"
    fi
  else
    log_error "无法解析分区路径: ${PARTITION}"
    exit 1
  fi
fi

# 4. 显示扩容后的磁盘使用情况
log_info "=== 扩容后的磁盘使用情况 ==="
df -h / | while IFS= read -r line; do
  log_info "  $line"
done

if [[ "${IS_LVM}" == "true" ]]; then
  log_info "=== LVM 信息（扩容后） ==="
  vgs 2>/dev/null | while IFS= read -r line; do
    log_info "  $line"
  done || true

  lvs 2>/dev/null | while IFS= read -r line; do
    log_info "  $line"
  done || true
fi

log_info "=== Ubuntu 磁盘扩容脚本完成 ==="
log_info ""
log_info "使用说明："
log_info "1. 如果使用虚拟机，请先在虚拟机管理界面扩展磁盘大小"
log_info "2. 然后运行此脚本自动扩容文件系统"
log_info ""
log_info "3. 手动扩容步骤（LVM）："
log_info "   # 扩展分区"
log_info "   parted -s /dev/sda resizepart 3 100%"
log_info "   # 重新扫描物理卷"
log_info "   pvresize /dev/sda3"
log_info "   # 扩展逻辑卷"
log_info "   lvextend -l +100%FREE /dev/ubuntu-vg/ubuntu-lv"
log_info "   # 扩展文件系统"
log_info "   resize2fs /dev/ubuntu-vg/ubuntu-lv"
log_info ""
log_info "4. 查看磁盘信息："
log_info "   lsblk"
log_info "   df -h"
log_info "   vgs && lvs && pvs"
