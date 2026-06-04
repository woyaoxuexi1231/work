#!/usr/bin/env bash
# ========================================================================================
# Ubuntu 静态IP配置脚本 (Ubuntu Static IP Configuration Script)
# ========================================================================================
#
# 功能说明:
#   本脚本用于在 Ubuntu 系统上配置静态IP地址，特别针对VMware虚拟机环境优化。
#   支持自动检测网络接口、配置DNS、设置静态路由等完整的网络配置。
#
# 主要特性:
#   ✓ 自动检测网络接口: 智能识别活动网络接口
#   ✓ VMware NAT优化: 专门针对VMware NAT模式优化配置
#   ✓ DNS配置完整: 配置主备DNS服务器确保解析稳定
#   ✓ 路由配置正确: 设置合适的默认网关和路由规则
#   ✓ 网络验证完整: 配置完成后自动验证连通性
#   ✓ 备份和恢复: 自动备份原有配置，支持恢复
#
# 默认配置参数:
#   - IP地址: 192.168.3.90
#   - 子网掩码: 255.255.255.0 (/24)
#   - 网关: 192.168.3.2 (VMware NAT默认网关)
#   - DNS1: 8.8.8.8 (Google Public DNS)
#   - DNS2: 8.8.4.4 (Google Public DNS备用)
#
# 适用环境:
#   - VMware Workstation/Player 虚拟机
#   - VirtualBox 等虚拟化平台 (需调整网关)
#   - Ubuntu 18.04+ 桌面/服务器版本
#   - 需要固定IP的开发/测试环境
#
# 配置流程:
#   1. 检测当前网络配置和接口状态
#   2. 备份现有网络配置文件
#   3. 生成新的静态IP配置文件
#   4. 应用网络配置并重启服务
#   5. 验证网络连通性和DNS解析
#
# 使用方法:
#   1. 基本配置（使用默认参数）:
#      sudo bash ubuntu_config_static_ip.sh
#
#   2. 自定义IP地址:
#      sudo IP_ADDR=192.168.1.100 bash ubuntu_config_static_ip.sh
#
#   3. 自定义网关:
#      sudo GATEWAY=192.168.1.1 bash ubuntu_config_static_ip.sh
#
#   4. 完全自定义:
#      sudo IP_ADDR=10.0.0.100 GATEWAY=10.0.0.1 DNS1=114.114.114.114 bash ubuntu_config_static_ip.sh
#
# 环境变量:
#   IP_ADDR        - 要配置的静态IP地址 (默认: 192.168.3.90)
#   GATEWAY        - 默认网关地址 (默认: 192.168.3.2)
#   NETMASK        - 子网掩码 (默认: 255.255.255.0)
#   DNS            - DNS服务器列表，逗号分隔 (默认: 223.5.5.5,8.8.8.8)
#   DNS_SEARCH     - DNS搜索域列表 (默认: localdomain)
#   INTERFACE      - 网络接口名 (默认: 自动检测)
#
# VMware 桥接模式说明:
#   VMware 桥接模式的网络配置特点：
#   - 虚拟机与宿主机在同一网络中
#   - 虚拟机可以直接访问外部网络
#   - 其他设备可以直接访问虚拟机
#   - 网关通常是 xxx.xxx.xxx.1（与宿主机相同）
#
# VMware NAT模式说明:
#   VMware NAT模式下：
#   - 网关通常是子网 xxx.xxx.xxx.2
#
# 网络接口检测:
#   脚本会自动检测活动网络接口，支持：
#   - 以太网接口 (enp0s3, ens33等)
#   - 无线网卡接口 (wlan0, wlp2s0等)
#   - 排除回环接口 (lo)
#
# DNS配置说明:
#   - 主DNS: 8.8.8.8 (Google Public DNS, 国内访问较快)
#   - 备DNS: 8.8.4.4 (Google备用DNS)
#   - 如需使用国内DNS，可设置: DNS1=114.114.114.114 DNS2=223.5.5.5
#
# 故障排除:
#   - 查看网络状态: ip addr show
#   - 测试连通性: ping 192.168.3.2
#   - 检查DNS: nslookup www.baidu.com
#   - 查看日志: journalctl -u NetworkManager
#   - 恢复配置: 从备份文件恢复
#
# 安全注意事项:
#   ⚠️ 配置前请记录当前网络配置
#   ⚠️ 确保IP地址在网络中唯一
#   ⚠️ 验证网关地址可达
#   ⚠️ 测试网络连接正常后再重启
#
# 文件位置:
#   - 配置文件: /etc/netplan/01-netcfg.yaml (Ubuntu 18.04+)
#   - 备份文件: /etc/netplan/01-netcfg.yaml.backup.*
#   - 日志文件: /var/log/network/config_static_ip.log
#
# 作者: 系统运维脚本
# 版本: v2.0
# 更新时间: 2024-01
# ========================================================================================
#
# 使用方法：
#   # 使用默认配置（推荐）
#   sudo bash ubuntu_config_static_ip.sh
#
#   # 自定义配置
#   STATIC_IP="192.168.3.90/24" GATEWAY="192.168.3.2" DNS="8.8.8.8" sudo bash ubuntu_config_static_ip.sh
#
# 特性：
#   - 自动检测网卡接口
#   - 针对VMware NAT模式优化（网关为子网.2）
#   - 配置前验证参数
#   - 配置后测试网络连通性

# Ensure Bash
if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -euo pipefail

# 网络配置（支持 VMware 桥接模式和 NAT 模式）
# 子网: 192.168.3.0/24
# 静态IP: 192.168.3.90
# 网关: 192.168.3.1 (桥接模式常用网关，NAT模式通常为子网.2)
STATIC_IP="${STATIC_IP:-192.168.3.90/24}"
GATEWAY="${GATEWAY:-192.168.3.1}"
# 默认DNS配置（国内常用DNS + Google备用）
DNS="${DNS:-223.5.5.5,8.8.8.8}"
# 默认搜索域
DNS_SEARCH="${DNS_SEARCH:-localdomain}"
IFACE="${IFACE:-}"

log() {
  local level="$1"; shift
  local msg="$*"
  local ts
  ts="$(date '+%Y-%m-%d %H:%M:%S')"
  printf '[%s] [%s] %s\n' "$ts" "$level" "$msg"
}

log_info() { log INFO "$@"; }
log_warn() { log WARN "$@"; }
log_error() { log ERROR "$@"; }

# Re-exec with sudo if needed
if [[ $EUID -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    log_warn "需要 root 权限，使用 sudo 重新执行..."
    exec sudo -E bash "$0" "$@"
  else
    log_error "请以 root 或 sudo 运行此脚本。"
    exit 1
  fi
fi

if ! command -v netplan >/dev/null 2>&1; then
  log_error "未检测到 netplan，此脚本适用于 Ubuntu 18.04+ (netplan)。"
  exit 1
fi

# Detect primary interface if not set
if [[ -z "$IFACE" ]]; then
  IFACE="$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++){if($i=="dev"){print $(i+1); exit}}}')"
fi

if [[ -z "$IFACE" ]]; then
  log_error "无法自动检测网卡 IFACE，请手动设置 IFACE 环境变量后重试。"
  exit 1
fi

if [[ -z "$STATIC_IP" || -z "$GATEWAY" ]]; then
  log_error "请设置 STATIC_IP（含掩码，如 192.168.124.200/24）与 GATEWAY（如 192.168.124.1）。"
  exit 1
fi

# 验证 IP 地址和网关是否在同一子网
log_info "验证 IP 地址和网关配置..."
IP_ADDR="${STATIC_IP%%/*}"
NETMASK_BITS="${STATIC_IP##*/}"

# 计算子网掩码
if [[ "${NETMASK_BITS}" == "24" ]]; then
  IP_BASE="${IP_ADDR%.*}"
  GATEWAY_BASE="${GATEWAY%.*}"
  if [[ "${IP_BASE}" != "${GATEWAY_BASE}" ]]; then
    log_error "IP 地址 ${IP_ADDR} 和网关 ${GATEWAY} 不在同一子网！"
    log_error "IP 网段: ${IP_BASE}.0/24，网关网段: ${GATEWAY_BASE}.0/24"
    log_error "请确保 IP 地址和网关在同一子网内。"
    exit 1
  fi
  log_info "✓ IP 地址和网关在同一子网: ${IP_BASE}.0/24"
elif [[ "${NETMASK_BITS}" == "16" ]]; then
  IP_BASE="${IP_ADDR%.*}"
  IP_BASE="${IP_BASE%.*}"
  GATEWAY_BASE="${GATEWAY%.*}"
  GATEWAY_BASE="${GATEWAY_BASE%.*}"
  if [[ "${IP_BASE}" != "${GATEWAY_BASE}" ]]; then
    log_error "IP 地址 ${IP_ADDR} 和网关 ${GATEWAY} 不在同一子网！"
    exit 1
  fi
  log_info "✓ IP 地址和网关在同一子网"
else
  log_warn "子网掩码为 /${NETMASK_BITS}，跳过子网验证（仅验证 /24 和 /16）"
fi

# 检测VMware环境并验证网关配置
# VMware桥接模式下，网关通常与宿主机相同（子网.1）
IS_VMWARE=false
VMWARE_BRIDGE_GATEWAY=""
if dmesg 2>/dev/null | grep -qi "vmware\|vmx" || \
   ([[ -f /sys/class/dmi/id/product_name ]] && grep -qi "vmware" /sys/class/dmi/id/product_name 2>/dev/null) || \
   [[ -f /sys/class/dmi/id/sys_vendor ]] && grep -qi "vmware" /sys/class/dmi/id/sys_vendor 2>/dev/null; then
  IS_VMWARE=true
  log_info "✓ 检测到 VMware 虚拟机环境"
  log_info "  当前模式: 桥接模式"
else
  log_warn "未检测到VMware环境，但将继续配置"
fi

# 提取IP网段并计算预期的桥接模式网关
IP_NETWORK="${STATIC_IP%%/*}"
IP_BASE="${IP_NETWORK%.*}"
VMWARE_BRIDGE_GATEWAY="${IP_BASE}.1"

# 验证网关配置（桥接模式下通常是.1）
if [[ "${GATEWAY}" == "${VMWARE_BRIDGE_GATEWAY}" ]]; then
  log_info "✓ 网关配置正确（${GATEWAY}，符合桥接模式）"
elif [[ "${GATEWAY}" == "${IP_BASE}.2" ]]; then
  log_warn "注意：当前网关 ${GATEWAY} 看起来像 NAT 模式配置"
  log_warn "桥接模式下，网关通常是 ${VMWARE_BRIDGE_GATEWAY}"
else
  log_info "网关配置: ${GATEWAY}"
fi

# Prepare DNS list
IFS=',' read -ra DNS_ARR <<< "$DNS"
DNS_YAML=""
for d in "${DNS_ARR[@]}"; do
  d_trimmed="$(echo "$d" | xargs)"
  # 10 spaces to align under "addresses:"
  [[ -n "$d_trimmed" ]] && DNS_YAML+="          - ${d_trimmed}\n"
done

# Prepare search domains
IFS=',' read -ra DNS_SEARCH_ARR <<< "$DNS_SEARCH"
SEARCH_YAML=""
for s in "${DNS_SEARCH_ARR[@]}"; do
  s_trimmed="$(echo "$s" | xargs)"
  [[ -n "$s_trimmed" ]] && SEARCH_YAML+="          - ${s_trimmed}\n"
done

if [[ -z "$DNS_YAML" ]]; then
  log_error "DNS 列表为空，请检查 DNS 环境变量。"
  exit 1
fi

NETPLAN_DIR="/etc/netplan"
TARGET_FILE="${NETPLAN_DIR}/01-static-${IFACE}.yaml"

log_info "网卡: ${IFACE}"
log_info "静态 IP: ${STATIC_IP}"
log_info "网关: ${GATEWAY}"
log_info "DNS: ${DNS}"
log_info "DNS Search: ${DNS_SEARCH}"
log_info "目标配置文件: ${TARGET_FILE}"

# Backup existing netplan YAML
for f in "${NETPLAN_DIR}"/*.yaml; do
  if [[ -f "$f" ]]; then
    cp -a "$f" "${f}.bak.$(date +%Y%m%d%H%M%S)" && log_info "备份 $f -> ${f}.bak"
  fi
done

# 检查并清理其他netplan文件中关于该接口的冲突配置
# netplan不允许多个文件为同一接口定义gateway4，会导致"Conflicting default route"错误
log_info "检查其他netplan文件中的冲突配置..."
CONFLICT_FILES=()

for f in "${NETPLAN_DIR}"/*.yaml; do
  # 跳过目标文件和备份文件
  if [[ "$f" == "${TARGET_FILE}" ]] || [[ "$f" == *.bak* ]]; then
    continue
  fi

  if [[ -f "$f" ]]; then
    # 检查文件中是否包含该接口的配置
    if grep -q "${IFACE}" "$f" 2>/dev/null; then
      # 检查该接口配置块中是否有gateway4（更宽松的匹配）
      # 使用更宽松的匹配，因为YAML缩进可能不同
      if grep -A 50 "${IFACE}" "$f" | grep -E "[[:space:]]*gateway4[[:space:]]*:" 2>/dev/null; then
        CONFLICT_FILES+=("$f")
        log_warn "发现冲突: $f 中也定义了 ${IFACE} 的 gateway4"
      # 也检查是否有routes配置可能导致默认路由冲突
      elif grep -A 50 "${IFACE}" "$f" | grep -E "[[:space:]]*routes[[:space:]]*:" 2>/dev/null; then
        # 检查routes中是否有默认路由
        if grep -A 50 "${IFACE}" "$f" | grep -A 10 "routes:" | grep -E "[[:space:]]*-[[:space:]]*to:[[:space:]]*0\.0\.0\.0" 2>/dev/null; then
          CONFLICT_FILES+=("$f")
          log_warn "发现冲突: $f 中 ${IFACE} 的 routes 配置可能包含默认路由"
        fi
      fi
    fi
  fi
done

# 如果没有检测到冲突，但仍然检查所有包含该接口的文件，以防万一
if [[ ${#CONFLICT_FILES[@]} -eq 0 ]]; then
  log_info "未检测到明显的gateway4冲突，但为确保安全，将检查所有包含 ${IFACE} 的文件"
  for f in "${NETPLAN_DIR}"/*.yaml; do
    if [[ "$f" == "${TARGET_FILE}" ]] || [[ "$f" == *.bak* ]]; then
      continue
    fi
    if [[ -f "$f" ]] && grep -q "${IFACE}" "$f" 2>/dev/null; then
      CONFLICT_FILES+=("$f")
      log_info "发现包含 ${IFACE} 的文件: $f，将清理其中的路由配置以确保无冲突"
    fi
  done
fi

# 如果有冲突，尝试修复
if [[ ${#CONFLICT_FILES[@]} -gt 0 ]]; then
  log_info "正在清理 ${#CONFLICT_FILES[@]} 个文件中的冲突配置..."

  for f in "${CONFLICT_FILES[@]}"; do
    log_info "处理文件: $f"

    # 方法1: 尝试使用Python处理YAML（如果可用）
    if command -v python3 >/dev/null 2>&1 && python3 -c "import yaml" 2>/dev/null; then
      if python3 - "$f" "$IFACE" <<'PYEOF'
import yaml
import sys

try:
    file_path = sys.argv[1]
    iface_name = sys.argv[2]

    with open(file_path, 'r', encoding='utf-8') as file:
        config = yaml.safe_load(file)

    modified = False
    if config and 'network' in config:
        if 'ethernets' in config['network'] and iface_name in config['network']['ethernets']:
            iface_config = config['network']['ethernets'][iface_name]
            # 移除gateway4、gateway6和routes
            if 'gateway4' in iface_config:
                del iface_config['gateway4']
                modified = True
                print(f"Removed gateway4 from {iface_name}")
            if 'gateway6' in iface_config:
                del iface_config['gateway6']
                modified = True
                print(f"Removed gateway6 from {iface_name}")
            if 'routes' in iface_config:
                del iface_config['routes']
                modified = True
                print(f"Removed routes from {iface_name}")
            # 确保DHCP被禁用
            iface_config['dhcp4'] = False
            iface_config['dhcp6'] = False

    if modified:
        with open(file_path, 'w', encoding='utf-8') as file:
            yaml.dump(config, file, default_flow_style=False, allow_unicode=True, sort_keys=False)
        print("SUCCESS")
        sys.exit(0)
    else:
        print("No changes needed")
        sys.exit(0)  # 即使没有修改也返回成功，因为可能已经清理过了
except Exception as e:
    print(f"ERROR: {e}", file=sys.stderr)
    sys.exit(1)
PYEOF
      then
        log_info "✓ 已清理 $f 中 ${IFACE} 的路由配置（使用Python）"
        continue
      else
        log_warn "Python处理失败，尝试使用awk方法"
      fi
    fi

    # 方法2: 使用awk删除gateway4相关行（更可靠的方法）
    TEMP_FILE="${f}.tmp"
    CLEANED=false

    # 首先尝试使用sed简单删除gateway4、gateway6和routes行
    # 使用临时文件方式以确保兼容性
    TEMP_SED="${f}.sedtmp"
    if sed "/${IFACE}/,/^[[:space:]]*[a-zA-Z0-9_-]*:/ {
      /gateway4:/d
      /gateway6:/d
      /^[[:space:]]*routes:/,/^[[:space:]]*[a-zA-Z0-9_-]*:/ {
        /^[[:space:]]*routes:/d
        /^[[:space:]]*-/d
        /^[[:space:]]*to:/d
        /^[[:space:]]*via:/d
        /^[[:space:]]*metric:/d
        /^[[:space:]]*scope:/d
      }
    }" "$f" > "$TEMP_SED" 2>/dev/null && mv "$TEMP_SED" "$f"; then
      CLEANED=true
    fi

    # 如果sed失败，使用awk方法
    if [[ "$CLEANED" == "false" ]]; then
      if awk -v iface="${IFACE}" '
        BEGIN { 
          in_iface = 0
          in_routes = 0
          iface_indent = 0
          routes_indent = 0
        }
        {
          # 检测是否进入接口配置块
          if ($0 ~ "^[[:space:]]*" iface ":[[:space:]]*$" || $0 ~ "^[[:space:]]*" iface ":[[:space:]]*") {
            in_iface = 1
            iface_indent = match($0, /[^[:space:]]/) - 1
            if (iface_indent < 0) iface_indent = 0
            print
            next
          }
          
          # 如果在接口块内
          if (in_iface) {
            # 计算当前行的缩进
            current_indent = match($0, /[^[:space:]]/) - 1
            if (current_indent < 0) current_indent = 0
            
            # 检测routes块
            if ($0 ~ /^[[:space:]]+routes:[[:space:]]*$/) {
              in_routes = 1
              routes_indent = current_indent
              next  # 跳过routes:行
            }
            
            # 在routes块内
            if (in_routes) {
              # 如果缩进回到routes级别或更小，退出routes块
              if (current_indent <= routes_indent && $0 !~ /^[[:space:]]*$/) {
                in_routes = 0
              } else {
                next  # 跳过routes块内的所有行
              }
            }
            
            # 如果遇到同级别或更高级别的键（非空行且是键），退出接口块
            if (!in_routes && $0 ~ /^[[:space:]]*[a-zA-Z0-9_-]+:/ && current_indent <= iface_indent && $0 !~ /^[[:space:]]*$/) {
              in_iface = 0
              in_routes = 0
            }
            
            # 在接口块内，跳过gateway4、gateway6行
            if (in_iface && !in_routes && ($0 ~ /^[[:space:]]+gateway4[[:space:]]*:/ || $0 ~ /^[[:space:]]+gateway6[[:space:]]*:/)) {
              next  # 跳过这一行
            }
          }
          
          print
        }
      ' "$f" > "$TEMP_FILE" 2>/dev/null && mv "$TEMP_FILE" "$f"; then
        CLEANED=true
      fi
    fi

    if [[ "$CLEANED" == "true" ]]; then
      log_info "✓ 已清理 $f 中 ${IFACE} 的路由配置（使用sed/awk）"
    else
      log_warn "⚠ 无法自动清理 $f"
      log_warn "   请手动编辑该文件，删除 ${IFACE} 配置块中的以下行："
      log_warn "     - gateway4: ..."
      log_warn "     - gateway6: ..."
      log_warn "     - routes: ..."
      log_warn "   或者临时禁用该文件: sudo mv $f ${f}.disabled"
      log_warn "   然后重新运行此脚本"
    fi
  done

  # 验证清理结果：再次检查是否还有冲突
  log_info "验证清理结果..."
  REMAINING_CONFLICTS=0
  for f in "${NETPLAN_DIR}"/*.yaml; do
    if [[ "$f" == "${TARGET_FILE}" ]] || [[ "$f" == *.bak* ]]; then
      continue
    fi
    if [[ -f "$f" ]] && grep -q "${IFACE}" "$f" 2>/dev/null; then
      if grep -A 50 "${IFACE}" "$f" | grep -E "[[:space:]]*gateway4[[:space:]]*:" 2>/dev/null; then
        log_error "⚠ 警告: $f 中仍然存在 ${IFACE} 的 gateway4 配置！"
        ((REMAINING_CONFLICTS++)) || true
      fi
    fi
  done

  if [[ $REMAINING_CONFLICTS -gt 0 ]]; then
    log_error "检测到 ${REMAINING_CONFLICTS} 个文件仍有冲突配置"
    log_error "建议手动处理这些文件，或临时禁用它们："
    for f in "${NETPLAN_DIR}"/*.yaml; do
      if [[ "$f" == "${TARGET_FILE}" ]] || [[ "$f" == *.bak* ]]; then
        continue
      fi
      if [[ -f "$f" ]] && grep -A 50 "${IFACE}" "$f" 2>/dev/null | grep -E "[[:space:]]*gateway4[[:space:]]*:" 2>/dev/null; then
        log_error "  sudo mv $f ${f}.disabled"
      fi
    done
    log_error ""
    log_error "处理完成后，请重新运行此脚本"
    exit 1
  else
    log_info "✓ 所有冲突配置已清理完成"
  fi
fi

log_info "写入静态 IP 配置到 ${TARGET_FILE}"

# 显示即将写入的配置
log_info "即将写入的配置内容："
cat <<EOF
network:
  version: 2
  ethernets:
    ${IFACE}:
      dhcp4: no
      addresses:
        - ${STATIC_IP}
      gateway4: ${GATEWAY}
      nameservers:
        search:
$(printf "%b" "$SEARCH_YAML")
        addresses:
$(printf "%b" "$DNS_YAML")
EOF

# 写入 netplan 配置文件
# 使用 gateway4 方式（更简单，兼容性更好，特别适合 VMware NAT 模式）
cat > "${TARGET_FILE}" <<EOF
network:
  version: 2
  ethernets:
    ${IFACE}:
      dhcp4: no
      addresses:
        - ${STATIC_IP}
      gateway4: ${GATEWAY}
      nameservers:
        search:
$(printf "%b" "$SEARCH_YAML")
        addresses:
$(printf "%b" "$DNS_YAML")
EOF

# Netplan 要求配置文件权限不能过宽
chmod 600 "${TARGET_FILE}"

# 验证 netplan 配置语法
log_info "验证 netplan 配置语法..."
GENERATE_OUTPUT=$(netplan generate 2>&1)
if echo "${GENERATE_OUTPUT}" | grep -qi "error"; then
  log_error "netplan 配置语法错误："
  echo "${GENERATE_OUTPUT}" | grep -i error || echo "${GENERATE_OUTPUT}"
  log_error "请检查配置文件: ${TARGET_FILE}"
  exit 1
fi

log_info "✓ 配置语法验证通过"

# 应用配置
log_info "应用 netplan 配置..."
log_info "提示：如果配置失败导致网络断开，可以手动执行 'sudo netplan try' 进行安全测试"
log_info "     netplan try 会在120秒后自动回滚，如果配置正确可以按 Enter 确认"

if netplan apply; then
  log_info "✓ 配置应用成功"
else
  EXIT_CODE=$?
  log_error "配置应用失败，退出码: ${EXIT_CODE}"
  log_error "请检查配置参数是否正确："
  log_error "  - 网卡名称: ${IFACE}"
  log_error "  - 静态 IP: ${STATIC_IP}"
  log_error "  - 网关: ${GATEWAY}"
  log_error "  - DNS: ${DNS}"
  log_error "配置文件位置: ${TARGET_FILE}"
  log_error ""
  log_error "排查建议："
  log_error "  1. 检查配置文件语法: sudo netplan generate"
  log_error "  2. 查看详细错误: sudo journalctl -xeu NetworkManager"
  log_error "  3. 检查网卡是否存在: ip link show"
  exit 1
fi

# 等待网络接口就绪并重新初始化
log_info "等待网络接口就绪..."
sleep 5

# 确保网络接口已启用
if ! ip link show "$IFACE" 2>/dev/null | grep -q "state UP"; then
  log_warn "接口 ${IFACE} 未启用，尝试启用..."
  ip link set "$IFACE" up 2>/dev/null || true
  sleep 2
fi

# 详细的配置后检查（参考 CSDN 最佳实践）
log_info "=== 配置后网络状态检查 ==="
echo ""

# 1. 检查网络接口状态
log_info "1. 网络接口状态："
if ip link show "$IFACE" 2>/dev/null | grep -q "state UP"; then
  log_info "✓ 接口 ${IFACE} 已启用"
else
  log_warn "✗ 接口 ${IFACE} 未启用或不存在"
fi

echo ""
log_info "2. IP 地址配置："
IP_SHOW=$(ip addr show "$IFACE" 2>/dev/null)
echo "${IP_SHOW}" | grep -E "inet |inet6 " || log_warn "无法显示 ${IFACE} 的 IP 信息"
if echo "${IP_SHOW}" | grep -q "${IP_ADDR}"; then
  log_info "✓ 静态 IP ${STATIC_IP} 已正确配置"
else
  log_warn "✗ 未找到配置的静态 IP ${STATIC_IP}"
fi

echo ""
log_info "3. 路由表检查："
ROUTE_OUTPUT=$(ip route show 2>/dev/null)
echo "${ROUTE_OUTPUT}" || log_warn "无法显示路由信息"

# 检查默认路由
if echo "${ROUTE_OUTPUT}" | grep -q "default"; then
  DEFAULT_ROUTE="$(echo "${ROUTE_OUTPUT}" | grep "default" | head -1)"
  if echo "${DEFAULT_ROUTE}" | grep -q "${GATEWAY}"; then
    log_info "✓ 默认路由已正确配置: ${DEFAULT_ROUTE}"
  else
    log_warn "✗ 默认路由存在但网关不匹配"
    log_warn "  当前: ${DEFAULT_ROUTE}"
    log_warn "  期望网关: ${GATEWAY}"
  fi
else
  log_error "✗ 未找到默认路由！这可能是网络不通的主要原因。"
  log_error "  请检查 netplan 配置中的 gateway4 字段是否正确。"
fi

# 检查本地路由
if echo "${ROUTE_OUTPUT}" | grep -q "${IP_BASE}"; then
  log_info "✓ 本地子网路由已配置"
else
  log_warn "✗ 未找到本地子网路由"
fi

echo ""

log_info "4. 防火墙检查："
if command -v ufw >/dev/null 2>&1; then
  UFW_STATUS=$(ufw status 2>/dev/null | head -1)
  if echo "${UFW_STATUS}" | grep -qi "active"; then
    log_warn "✗ UFW 防火墙已启用，可能阻止网络连接"
    log_warn "  状态: ${UFW_STATUS}"
    log_warn "  如需测试，可临时禁用: sudo ufw disable"
    log_warn "  测试后记得重新启用: sudo ufw enable"
  else
    log_info "✓ UFW 防火墙未启用或已禁用"
  fi
else
  log_info "未检测到 UFW 防火墙"
fi

echo ""
log_info "5. DNS 配置检查："
# 重启 systemd-resolved 以确保 DNS 配置生效
if systemctl is-active --quiet systemd-resolved 2>/dev/null; then
  log_info "重启 systemd-resolved 服务以确保 DNS 配置生效..."
  systemctl restart systemd-resolved 2>/dev/null || true
  sleep 2
fi
echo ""
echo "--- DNS 配置 ---"
# 显示 systemd-resolved 的DNS配置（优先使用 resolvectl）
if command -v resolvectl >/dev/null 2>&1; then
  log_info "systemd-resolved 状态："
  resolvectl status 2>/dev/null | head -30 || true
elif command -v systemd-resolve >/dev/null 2>&1; then
  log_info "systemd-resolved 状态："
  systemd-resolve --status 2>/dev/null | head -30 || true
fi
echo ""
log_info "/etc/resolv.conf 内容："
cat /etc/resolv.conf 2>/dev/null || log_warn "无法读取 /etc/resolv.conf"
echo ""
log_info "netplan 配置的DNS："
cat "${TARGET_FILE}" | grep -A 10 "nameservers:" || log_warn "无法读取 netplan DNS 配置"

# 测试网络连通性
log_info "=== 开始网络连通性测试 ==="
TEST_RESULTS=()
CRITICAL_FAILURES=0

# 测试网关连通性（关键测试）
log_info "测试网关连通性: ${GATEWAY}"
if ping -c 3 -W 3 "${GATEWAY}" >/dev/null 2>&1; then
  log_info "✓ 网关 ${GATEWAY} 可达"
  TEST_RESULTS+=("gateway:OK")
else
  log_error "✗ 网关 ${GATEWAY} 不可达"
  log_error "   这是关键问题！如果网关不通，网络将无法正常工作。"
  TEST_RESULTS+=("gateway:FAIL")
  ((CRITICAL_FAILURES++)) || true
fi

# 测试 DNS 服务器连通性
log_info "测试 DNS 服务器连通性..."
DNS_TESTED=false
IFS=',' read -ra DNS_ARR <<< "$DNS"
for dns_server in "${DNS_ARR[@]}"; do
  dns_trimmed="$(echo "$dns_server" | xargs)"
  if [[ -n "$dns_trimmed" ]]; then
    # 使用ping或nc测试连通性
    if ping -c 1 -W 2 "${dns_trimmed}" >/dev/null 2>&1; then
      log_info "✓ DNS 服务器 ${dns_trimmed} 可达"
      DNS_TESTED=true
    elif command -v nc >/dev/null 2>&1 && nc -z -w 2 "${dns_trimmed}" 53 >/dev/null 2>&1; then
      log_info "✓ DNS 服务器 ${dns_trimmed}:53 可达"
      DNS_TESTED=true
    else
      log_warn "✗ DNS 服务器 ${dns_trimmed} 不可达 (可能网络受限)"
    fi
  fi
done

# 测试 DNS 解析
DNS_RESOLVE_OK=false
if command -v nslookup >/dev/null 2>&1; then
  # 先测试直接使用配置的DNS服务器
  for dns_server in "${DNS_ARR[@]}"; do
    dns_trimmed="$(echo "$dns_server" | xargs)"
    if [[ -n "$dns_trimmed" ]]; then
      if nslookup -timeout=3 baidu.com "${dns_trimmed}" >/dev/null 2>&1; then
        log_info "✓ DNS 解析正常（使用 ${dns_trimmed}）"
        DNS_RESOLVE_OK=true
        TEST_RESULTS+=("dns:OK")
        break
      fi
    fi
  done

  if [[ "${DNS_RESOLVE_OK}" == "false" ]]; then
    # 尝试使用系统默认DNS
    if nslookup -timeout=3 baidu.com >/dev/null 2>&1; then
      log_info "✓ DNS 解析正常（使用系统默认DNS）"
      TEST_RESULTS+=("dns:OK")
    else
      log_warn "✗ DNS 解析失败"
      TEST_RESULTS+=("dns:FAIL")
    fi
  fi
elif command -v host >/dev/null 2>&1; then
  if host -W 3 baidu.com >/dev/null 2>&1; then
    log_info "✓ DNS 解析正常"
    TEST_RESULTS+=("dns:OK")
  else
    log_warn "✗ DNS 解析失败"
    TEST_RESULTS+=("dns:FAIL")
  fi
fi

# 测试外网连通性（关键测试）
log_info "测试外网连通性: 8.8.8.8"
if ping -c 3 -W 3 8.8.8.8 >/dev/null 2>&1; then
  log_info "✓ 外网连通（8.8.8.8）"
  TEST_RESULTS+=("internet:OK")
else
  log_error "✗ 外网不通（8.8.8.8）"
  log_error "   如果网关通但外网不通，可能是路由或防火墙问题"
  TEST_RESULTS+=("internet:FAIL")
  ((CRITICAL_FAILURES++)) || true
fi

# 测试域名解析（关键测试）
log_info "测试域名解析: baidu.com"
if ping -c 2 -W 3 baidu.com >/dev/null 2>&1; then
  log_info "✓ 域名解析正常（baidu.com）"
  TEST_RESULTS+=("domain:OK")
else
  log_error "✗ 域名解析失败（baidu.com）"
  log_error "   如果IP通但域名不通，可能是DNS配置问题"
  TEST_RESULTS+=("domain:FAIL")
  ((CRITICAL_FAILURES++)) || true
fi

# 总结测试结果
FAILED_TESTS=0
for result in "${TEST_RESULTS[@]}"; do
  if [[ "$result" == *":FAIL" ]]; then
    ((FAILED_TESTS++)) || true
  fi
done

echo ""
log_info "=== 测试结果总结 ==="
if [[ ${CRITICAL_FAILURES} -eq 0 && ${FAILED_TESTS} -eq 0 ]]; then
  log_info "✓✓✓ 所有网络测试通过，网络配置成功！"
  log_info "当前配置："
  log_info "  - 网卡: ${IFACE}"
  log_info "  - IP地址: ${STATIC_IP}"
  log_info "  - 网关: ${GATEWAY}"
  log_info "  - DNS: ${DNS}"
  log_info "  - DNS Search: ${DNS_SEARCH}"
elif [[ ${CRITICAL_FAILURES} -gt 0 ]]; then
  log_error "✗✗✗ 检测到关键网络问题，网络可能无法正常使用！"
  log_error "失败的关键测试数量: ${CRITICAL_FAILURES}"
elif [[ ${FAILED_TESTS} -gt 0 ]]; then
  log_warn "部分网络测试失败，但配置已应用。"
  echo ""

  # VMware 桥接模式特定排查
  log_error ""
  log_error "=== VMware 桥接模式故障排查 ==="
  log_error ""
  log_error "1. 确认网关地址（最重要）："
  log_error "   - 桥接模式下，虚拟机网关与宿主机相同"
  log_error "   - 在宿主机执行: ipconfig (Windows) 或 route -n (Linux)"
  log_error "   - 查看默认网关地址"
  log_error "   - VMware 桥接模式下，网关通常是子网的 .1（如 192.168.3.1）"
  log_error ""
  log_error "2. 如果网关不是 ${VMWARE_BRIDGE_GATEWAY}，请使用正确的网关重新配置："
  log_error "   GATEWAY=\"正确的网关IP\" sudo bash $0"
  log_error ""
  log_error "3. 确认虚拟机网络适配器设置："
  log_error "   - 右键虚拟机 -> 设置 -> 网络适配器"
  log_error "   - 确保选择 '桥接模式'（不是NAT模式或仅主机模式）"
  log_error ""
  log_error "4. 确认虚拟机与宿主机在同一网段："
  log_error "   - 虚拟机IP: ${STATIC_IP}"
  log_error "   - 宿主机IP: 确保也在 192.168.3.x 网段"
  log_error ""
  log_error "5. 如果网关正确但仍不通，尝试重启网络服务："
  log_error "   sudo systemctl restart systemd-networkd"
  log_error "   或重启虚拟机"
  echo ""

  log_warn "通用故障排查建议（参考 CSDN 最佳实践）："
  log_warn "  1. 确认网络接口状态："
  log_warn "     ip link show ${IFACE}"
  log_warn "     如果接口未启用，检查物理连接或虚拟网络适配器设置"
  log_warn ""
  log_warn "  2. 检查 IP 地址和网关是否在同一子网："
  log_warn "     IP: ${STATIC_IP}，网关: ${GATEWAY}"
  log_warn "     例如：IP 192.168.3.90/24，网关应该是 192.168.3.x"
  log_warn ""
  log_warn "  3. 验证 Netplan 配置语法："
  log_warn "     sudo netplan generate"
  log_warn "     检查配置文件: ${TARGET_FILE}"
  log_warn ""
  log_warn "  4. 检查路由表："
  log_warn "     ip route show"
  log_warn "     确认有默认路由指向网关: ip route | grep default"
  log_warn ""
  log_warn "  5. 测试网关连通性："
  log_warn "     ping ${GATEWAY}"
  log_warn "     如果网关不通，检查网关地址是否正确"
  log_warn ""
  log_warn "  6. 测试外部 IP（绕过 DNS）："
  log_warn "     ping 8.8.8.8"
  log_warn "     如果外部 IP 不通但网关通，可能是路由或防火墙问题"
  log_warn ""
  log_warn "  7. 检查 DNS 配置："
  log_warn "     cat /etc/resolv.conf"
  log_warn "     resolvectl status"
  log_warn ""
  log_warn "  8. 检查防火墙："
  log_warn "     sudo ufw status"
  log_warn "     如需测试可临时禁用: sudo ufw disable"
  log_warn ""
  log_warn "  9. 使用 netplan try 安全测试配置："
  log_warn "     sudo netplan try"
  log_warn "     如果配置有问题，120秒内会自动回滚"
  echo ""

  # 检查是否有DNS失败
  if echo "${TEST_RESULTS[@]}" | grep -q "dns:FAIL"; then
    log_warn "DNS 问题排查："
    log_warn "  1. 检查 DNS 服务器是否可达：ping ${DNS_ARR[0]}"
    log_warn "  2. 手动测试 DNS 解析：nslookup baidu.com ${DNS_ARR[0]}"
    log_warn "  3. 如果使用 systemd-resolved，检查服务状态："
    log_warn "     systemctl status systemd-resolved"
    log_warn "     resolvectl status"
    log_warn "  4. 尝试重启 systemd-resolved："
    log_warn "     sudo systemctl restart systemd-resolved"
    log_warn "  5. 如果 /etc/resolv.conf 是 symlink，手动修复："
    log_warn "     sudo rm -f /etc/resolv.conf"
    log_warn "     sudo bash -c 'echo \"nameserver ${DNS_ARR[0]}\" > /etc/resolv.conf'"
    log_warn "  6. 或者使用 resolvectl 设置 DNS："
    log_warn "     sudo resolvectl dns ${IFACE} ${DNS}"
  fi

  # 检查是否有外网失败
  if echo "${TEST_RESULTS[@]}" | grep -q "internet:FAIL"; then
    log_warn "外网连接问题排查："
    log_warn "  1. 检查默认路由：ip route | grep default"
    log_warn "  2. 测试网关：ping ${GATEWAY}"
    log_warn "  3. 测试DNS服务器：ping ${DNS_ARR[0]}"
    log_warn "  4. 检查是否有其他路由规则冲突：ip route show"
    log_warn "  5. 如果是虚拟机，检查虚拟网络适配器设置（桥接/NAT）"
  fi

  echo ""
  log_info "可以尝试手动修复："
  log_info "  1. 检查并编辑配置：sudo nano ${TARGET_FILE}"
  log_info "  2. 重新应用：sudo netplan apply"
  log_info "  3. 重启 systemd-resolved：sudo systemctl restart systemd-resolved"
else
  log_info "✓ 网络连通性测试通过"
fi

echo ""
log_info "=== 配置完成 ==="
if [[ ${CRITICAL_FAILURES} -eq 0 && ${FAILED_TESTS} -eq 0 ]]; then
  log_info "✓ 静态IP配置成功，网络可用！"
  log_info ""
  log_info "当前网络配置："
  log_info "  网卡接口: ${IFACE}"
  log_info "  静态IP: ${STATIC_IP}"
  log_info "  网关: ${GATEWAY}"
  log_info "  DNS: ${DNS}"
  log_info "  DNS Search: ${DNS_SEARCH}"
  log_info ""
  log_info "配置文件位置: ${TARGET_FILE}"
else
  log_warn "配置已应用，但部分测试失败。"
  log_warn "请根据上述排查建议检查配置。"
fi

log_info ""
log_info "如需回滚配置，可执行："
log_info "  sudo rm ${TARGET_FILE}"
log_info "  sudo netplan apply"
log_info ""
log_info "或恢复备份文件："
log_info "  sudo cp ${NETPLAN_DIR}/*.bak.* ${TARGET_FILE}"
log_info "  sudo netplan apply"
