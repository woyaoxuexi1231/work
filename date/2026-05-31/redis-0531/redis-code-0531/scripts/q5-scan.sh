#!/bin/bash
# ============================================================================
# Q5: 热 Key / 大 Key 排查脚本
#
# 用法: bash scripts/q5-scan.sh [node_host] [node_port]
# 示例: bash scripts/q5-scan.sh 192.168.3.100 7000
# ============================================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

HOST="${1:-192.168.3.100}"
PORT="${2:-7000}"
PASS="123456"
SPRING_API="http://localhost:8080/api/cluster/troubleshoot"

echo -e "${CYAN}══════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  Q5: 热 Key / 大 Key 排查${NC}"
echo -e "${CYAN}  目标节点: ${HOST}:${PORT}${NC}"
echo -e "${CYAN}══════════════════════════════════════════════════════════════${NC}"
echo ""

# ---- 1. 慢查询日志 ----
echo -e "${YELLOW}[1] SLOWLOG —— 最近 10 条慢命令${NC}"
echo "  命令: redis-cli -h ${HOST} -p ${PORT} -a ${PASS} SLOWLOG GET 10"
echo "  （阈值 slowlog-log-slower-than 通常设为 10000 = 10ms）"
echo ""
redis-cli -h ${HOST} -p ${PORT} -a ${PASS} --no-auth-warning SLOWLOG GET 10 2>/dev/null | head -40
echo ""
echo "  面试要点: KEYS * → 遍历键空间，阻塞主线程，线上禁用"
echo "           HGETALL 大 hash → 可能返回数 MB 数据，打满带宽"
echo "           SORT 大集合 → O(N+M*log(M)) 复杂度"
pause() { echo ""; read -p "按 Enter 继续..."; }
pause

# ---- 2. 大 Key 扫描 ----
echo ""
echo -e "${YELLOW}[2] --bigkeys —— 扫描大 Key（⚠ 会遍历所有 key，生产慎用）${NC}"
echo "  命令: redis-cli -h ${HOST} -p ${PORT} -a ${PASS} --bigkeys"
echo ""
redis-cli -h ${HOST} -p ${PORT} -a ${PASS} --no-auth-warning --bigkeys 2>/dev/null | head -30
echo ""
echo "  面试要点: 大 Key (≥10MB) 会导致:"
echo "  1. 读取阻塞主线程（HGETALL / GET 大 value）"
echo "  2. 迁移时 MIGRATE 同步操作 → 主线程阻塞数秒"
echo "  3. 主从同步时大 Key 传输占用带宽"
echo "  对策: 按用户尾号/日期拆分（如 cart:{userId % 100}）"
pause

# ---- 3. 内存统计 ----
echo ""
echo -e "${YELLOW}[3] MEMORY STATS —— 内存碎片率${NC}"
echo "  命令: redis-cli -h ${HOST} -p ${PORT} -a ${PASS} MEMORY STATS"
echo ""
redis-cli -h ${HOST} -p ${PORT} -a ${PASS} --no-auth-warning MEMORY STATS 2>/dev/null | grep -E "fragmentation|peak|total"
echo ""
echo "  面试要点: mem_fragmentation_ratio > 1.5 → 内存碎片严重"
echo "           重启或 MEMORY PURGE 可缓解"
pause

# ---- 4. 热 Key（如果 Redis 版本支持） ----
echo ""
echo -e "${YELLOW}[4] --hotkeys —— 热 Key 扫描（Redis 4.0+，需 maxmemory-policy=LFU）${NC}"
echo "  命令: redis-cli -h ${HOST} -p ${PORT} -a ${PASS} --hotkeys"
echo ""
redis-cli -h ${HOST} -p ${PORT} -a ${PASS} --no-auth-warning --hotkeys 2>/dev/null | head -20 || \
echo "  （如果失败，说明需要设置 maxmemory-policy allkeys-lfu）"

echo ""
echo "  面试要点: 热 Key 应急方案"
echo "  读热 Key: 加 Slave 分担读 + 本地 Caffeine 缓存"
echo "  写热 Key: 消息队列削峰 + key 拆分（100 份）"
echo "  禁忌: 不能直接 DEL 热 Key → 缓存击穿 → DB 瞬间打爆"
pause

# ---- 5. Spring Boot 多级缓存接口 ----
echo ""
echo -e "${YELLOW}[5] Spring Boot 多级缓存命中率${NC}"
echo "  接口: GET ${SPRING_API}/cache/stats"
curl -s "${SPRING_API}/cache/stats" 2>/dev/null | python3 -m json.tool 2>/dev/null || \
echo "  （Spring Boot 未启动或接口不可达）"

echo ""
echo -e "${GREEN}══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  Q5 扫描完成${NC}"
echo -e "${GREEN}══════════════════════════════════════════════════════════════${NC}"
