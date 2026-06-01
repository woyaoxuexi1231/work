#!/bin/bash
# ============================================================================
# Q8: Cluster 平滑扩缩容——reshard 监控脚本
#
# 用法:
#   终端1: redis-cli --cluster reshard ...    (执行迁移)
#   终端2: bash scripts/q8-reshard-monitor.sh  (监控进度)
# ============================================================================

CYAN='\033[0;36m'
YELLOW='\033[1;33m'
NC='\033[0m'

HOST="${1:-192.168.3.100}"
PORT="${2:-7000}"
PASS="123456"
SPRING_API="http://localhost:8080/api/ops/reshard"

echo -e "${CYAN}══════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  Q8: Cluster Reshard 监控${NC}"
echo -e "${CYAN}  每 2 秒刷新一次，Ctrl+C 退出${NC}"
echo -e "${CYAN}══════════════════════════════════════════════════════════════${NC}"

while true; do
    clear
    echo -e "${CYAN}=== $(date '+%H:%M:%S') ===${NC}"
    echo ""

    # 1. Slot 分布
    echo -e "${YELLOW}[Slot 分布]${NC}"
    curl -s "${SPRING_API}/slot-distribution" 2>/dev/null | \
        python3 -c "
import json,sys
try:
    d=json.load(sys.stdin)
    if 'nodes' in d:
        for n in d['nodes']:
            print(f\"  {n['nodeId'][:12]}... → {n['slotCount']} slots\")
        print(f\"  均衡度: min={d.get('minSlots','?')} max={d.get('maxSlots','?')} avg={d.get('avgSlotsPerNode','?')}\")
    elif 'error' in d:
        print(f\"  ERROR: {d['error']}\")
except: pass
" 2>/dev/null || echo "  (Spring Boot 未连接)"

    echo ""

    # 2. 正在迁移的 slot
    echo -e "${YELLOW}[正在迁移的 slot]${NC}"
    curl -s "${SPRING_API}/migrating-slots" 2>/dev/null | \
        python3 -c "
import json,sys
try:
    d=json.load(sys.stdin)
    print(f\"  MIGRATING: {len(d.get('migrating',[]))} 个\")
    print(f\"  IMPORTING: {len(d.get('importing',[]))} 个\")
    for m in d.get('migrating',[]):
        print(f\"    → {m.get('slotInfo','?')}\")
    for m in d.get('importing',[]):
        print(f\"    ← {m.get('slotInfo','?')}\")
except: pass
" 2>/dev/null || echo "  (无活跃迁移)"

    echo ""
    echo "按 Ctrl+C 停止监控"

    sleep 2
done
