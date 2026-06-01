#!/bin/bash
# ============================================================================
# Q5: 热 Key / 大 Key 排查脚本
# 用法: bash scripts/q5-scan.sh [host] [port]
# ============================================================================
HOST="${1:-192.168.3.100}"
PORT="${2:-7000}"
PASS="123456"

echo "=== Q5: 热 Key / 大 Key 排查 ==="
echo "目标: ${HOST}:${PORT}"
echo ""

echo "--- [1] SLOWLOG GET 5（慢命令）---"
redis-cli -h "${HOST}" -p "${PORT}" -a "${PASS}" --no-auth-warning SLOWLOG GET 5 2>/dev/null
echo ""
echo "  关注: 第3个数字是耗时(微秒)，>10000=10ms 即慢查询"
echo "  面试点: KEYS * 遍历全库阻塞主线程；HGETALL 大 key 返回数MB数据"
echo ""

echo "--- [2] --bigkeys（大 Key 扫描）---"
redis-cli -h "${HOST}" -p "${PORT}" -a "${PASS}" --no-auth-warning --bigkeys 2>/dev/null | head -20
echo ""
echo "  面试点: 大 Key (>=10MB) 读取阻塞主线程，MIGRATE 时卡死"
echo "  对策: 按用户尾号/日期拆分"
echo ""

echo "--- [3] MEMORY STATS（内存碎片）---"
redis-cli -h "${HOST}" -p "${PORT}" -a "${PASS}" --no-auth-warning MEMORY STATS 2>/dev/null | grep -E "fragmentation|peak|total"
echo ""
echo "  面试点: mem_fragmentation_ratio > 1.5 → 碎片严重"
echo ""

echo "--- [4] --hotkeys（热 Key，需 allkeys-lfu 策略）---"
redis-cli -h "${HOST}" -p "${PORT}" -a "${PASS}" --no-auth-warning --hotkeys 2>/dev/null | head -10
echo ""
echo "  面试点: 热 Key 不能直接 DEL → 缓存击穿 → DB 瞬间打爆"
echo "  应急: 本地 Caffeine 缓存 + 从库分担读 + key 拆分"
echo ""
echo "=== 扫描完成 ==="
