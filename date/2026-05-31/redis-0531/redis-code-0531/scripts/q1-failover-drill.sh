#!/bin/bash
# ============================================================================
# Q1: Sentinel 故障转移——交互式演练脚本
#
# 用法: bash scripts/q1-failover-drill.sh
#
# 这个脚本引导你完成完整的故障转移演练：
#   1. 查看初始拓扑
#   2. tail Sentinel 日志（开新终端）
#   3. Kill master
#   4. 观察日志中的 SDOWN → ODOWN → 选举 → 新主上线
#   5. 验证新拓扑
#   6. 复活旧主 → 观察降级为 Slave
# ============================================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

SPRING_API="http://localhost:8080/api/sentinel"
MASTER_HOST="192.168.3.100"
MASTER_PORT="6379"
REDIS_PASS="123456"
SENTINEL1="${MASTER_HOST}:26379"
SENTINEL2="${MASTER_HOST}:26380"
SENTINEL3="${MASTER_HOST}:26381"

# ---- helper ----
pause() { echo ""; read -p "按 Enter 继续..."; }

# ============================================================================
echo -e "${CYAN}══════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  Q1: Sentinel 故障转移——真实演练${NC}"
echo -e "${CYAN}══════════════════════════════════════════════════════════════${NC}"
echo ""

# ---- Step 1: 确认 Spring Boot 已启动 ----
echo -e "${YELLOW}[Step 1] 确认 Spring Boot 应用已启动${NC}"
if curl -s "${SPRING_API}/who-is-master" > /dev/null 2>&1; then
    echo -e "${GREEN}  ✓ Spring Boot 可达${NC}"
    curl -s "${SPRING_API}/who-is-master" | python3 -m json.tool 2>/dev/null || \
    curl -s "${SPRING_API}/who-is-master"
else
    echo -e "${RED}  ✗ 无法连接 ${SPRING_API}${NC}"
    echo "  请先启动: mvn spring-boot:run -Dspring-boot.run.profiles=sentinel"
    exit 1
fi
pause

# ---- Step 2: 初始拓扑 ----
echo -e "${YELLOW}[Step 2] 查看当前 Sentinel 拓扑${NC}"
echo "  调用: GET ${SPRING_API}/topology"
curl -s "${SPRING_API}/topology" | python3 -m json.tool 2>/dev/null | head -40
pause

# ---- Step 3: 查看 Sentinel 配置 ----
echo -e "${YELLOW}[Step 3] 查看 Sentinel 关键参数${NC}"
curl -s "${SPRING_API}/drill-config" | python3 -m json.tool 2>/dev/null
pause

# ---- Step 4: 开新终端 tail 日志（提示） ----
echo ""
echo -e "${RED}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${RED}║  [Step 4] 现在打开 3 个新终端，分别 tail Sentinel 日志：    ║${NC}"
echo -e "${RED}║                                                              ║${NC}"
echo -e "${RED}║  终端1: redis-cli -h ${MASTER_HOST} -p 26379 -a ${REDIS_PASS} MONITOR${NC}"
echo -e "${RED}║  终端2: redis-cli -h ${MASTER_HOST} -p 26380 -a ${REDIS_PASS} MONITOR${NC}"
echo -e "${RED}║  终端3: redis-cli -h ${MASTER_HOST} -p 26381 -a ${REDIS_PASS} MONITOR${NC}"
echo -e "${RED}║                                                              ║${NC}"
echo -e "${RED}║  或者用redis命令看Sentinel状态：${NC}"
echo -e "${RED}║  watch -n 1 'redis-cli -h ${MASTER_HOST} -p 26379 -a ${REDIS_PASS} SENTINEL MASTER mymaster | grep -E \"flags|num-slaves|ip\"'${NC}"
echo -e "${RED}╚══════════════════════════════════════════════════════════════╝${NC}"
pause

# ---- Step 5: Kill master ----
echo ""
echo -e "${RED}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${RED}║  [Step 5] 即将 KILL MASTER！                                ║${NC}"
echo -e "${RED}║                                                              ║${NC}"
echo -e "${RED}║  当前 master: ${MASTER_HOST}:${MASTER_PORT}${NC}"
echo -e "${RED}║  命令: redis-cli -h ${MASTER_HOST} -p ${MASTER_PORT} -a ${REDIS_PASS} SHUTDOWN${NC}"
echo -e "${RED}║                                                              ║${NC}"
echo -e "${RED}║  请盯好那 3 个 Sentinel 日志终端！                          ║${NC}"
echo -e "${RED}╚══════════════════════════════════════════════════════════════╝${NC}"
read -p "输入 YES 确认执行 kill: " confirm
if [ "$confirm" != "YES" ]; then
    echo "已取消"
    exit 0
fi

echo ""
echo -e "${RED}>>> 执行 KILL MASTER ...${NC}"
redis-cli -h ${MASTER_HOST} -p ${MASTER_PORT} -a ${REDIS_PASS} SHUTDOWN 2>&1
echo ""
echo -e "${YELLOW}wait 5 秒，让 Sentinel 感知到故障...${NC}"
sleep 5
pause

# ---- Step 6: 查看故障转移结果 ----
echo -e "${YELLOW}[Step 6] 故障转移后——查看新拓扑${NC}"
echo "  观察 Sentinel 日志中是否出现:"
echo "    +sdown master mymaster ${MASTER_HOST} ${MASTER_PORT}"
echo "    +odown master mymaster ${MASTER_HOST} ${MASTER_PORT}"
echo "    +failover-state-select-slave ..."
echo "    +switch-master mymaster ${MASTER_HOST} ${MASTER_PORT} <新主IP> <新主端口>"
echo ""
echo "  当前谁是 master？"
curl -s "${SPRING_API}/who-is-master" | python3 -m json.tool 2>/dev/null

echo ""
echo "  完整拓扑："
curl -s "${SPRING_API}/topology" | python3 -m json.tool 2>/dev/null | head -30
pause

# ---- Step 7: 复活旧主 ----
echo ""
echo -e "${YELLOW}[Step 7] 复活旧主——观察它降级为 Slave${NC}"
echo -e "  重启命令（请根据实际部署方式选择）:"
echo -e "  Docker: docker start <redis-6379-container>"
echo -e "  直接:   redis-server /path/to/redis-6379.conf &"
echo ""
echo -e "  重启后观察 Sentinel 日志中是否出现:"
echo -e "    +slave slave ${MASTER_HOST}:${MASTER_PORT} ..."
echo -e "    旧主以 Slave 身份重新加入，从新主同步数据"
read -p "旧主已重启？按 Enter 查看最终拓扑..."

echo ""
echo -e "${GREEN}最终拓扑：${NC}"
curl -s "${SPRING_API}/topology" | python3 -m json.tool 2>/dev/null | head -40

echo ""
echo -e "${GREEN}══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  Q1 演练完成！${NC}"
echo -e "${GREEN}  核心收获：Sentinel 日志中的 +sdown → +odown → +switch-master${NC}"
echo -e "${GREEN}  以及旧主复活后的 +slave 事件${NC}"
echo -e "${GREEN}══════════════════════════════════════════════════════════════${NC}"
