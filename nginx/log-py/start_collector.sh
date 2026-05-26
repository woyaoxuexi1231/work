#!/bin/bash
# Nginx 日志采集器启动脚本
# 使用方法: ./start_collector.sh [start|stop|restart|status]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_SCRIPT="${SCRIPT_DIR}/nginx_log_collector_direct.py"
PID_FILE="/var/run/nginx-log-collector.pid"
LOG_FILE="/var/log/nginx/collector.log"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查 Python 脚本是否存在
if [ ! -f "$PYTHON_SCRIPT" ]; then
    echo -e "${RED}❌ 错误: Python 脚本不存在: $PYTHON_SCRIPT${NC}"
    exit 1
fi

# 启动函数
start() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p "$PID" > /dev/null 2>&1; then
            echo -e "${YELLOW}⚠️  采集器已在运行 (PID: $PID)${NC}"
            return 1
        else
            rm -f "$PID_FILE"
        fi
    fi

    echo -e "${GREEN}🚀 启动 Nginx 日志采集器...${NC}"
    nohup python3 "$PYTHON_SCRIPT" >> "$LOG_FILE" 2>&1 &
    PID=$!
    echo $PID > "$PID_FILE"
    
    sleep 2
    if ps -p "$PID" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ 采集器启动成功 (PID: $PID)${NC}"
        echo -e "📁 日志文件: $LOG_FILE"
        return 0
    else
        echo -e "${RED}❌ 采集器启动失败${NC}"
        rm -f "$PID_FILE"
        return 1
    fi
}

# 停止函数
stop() {
    if [ ! -f "$PID_FILE" ]; then
        echo -e "${YELLOW}⚠️  采集器未运行${NC}"
        return 1
    fi

    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null 2>&1; then
        echo -e "${YELLOW}🛑 停止 Nginx 日志采集器 (PID: $PID)...${NC}"
        kill "$PID"
        sleep 2
        
        if ps -p "$PID" > /dev/null 2>&1; then
            echo -e "${YELLOW}⚠️  强制停止...${NC}"
            kill -9 "$PID"
            sleep 1
        fi
        
        rm -f "$PID_FILE"
        echo -e "${GREEN}✅ 采集器已停止${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  进程不存在，清理 PID 文件${NC}"
        rm -f "$PID_FILE"
        return 1
    fi
}

# 重启函数
restart() {
    stop
    sleep 1
    start
}

# 状态函数
status() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p "$PID" > /dev/null 2>&1; then
            echo -e "${GREEN}✅ 采集器正在运行 (PID: $PID)${NC}"
            
            # 显示最近日志
            echo -e "\n📋 最近日志:"
            tail -n 10 "$LOG_FILE" 2>/dev/null || echo "  (无法读取日志文件)"
            return 0
        else
            echo -e "${RED}❌ PID 文件存在但进程未运行${NC}"
            rm -f "$PID_FILE"
            return 1
        fi
    else
        echo -e "${YELLOW}⚠️  采集器未运行${NC}"
        return 1
    fi
}

# 主逻辑
case "$1" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    status)
        status
        ;;
    *)
        echo "使用方法: $0 {start|stop|restart|status}"
        echo ""
        echo "命令说明:"
        echo "  start   - 启动采集器"
        echo "  stop    - 停止采集器"
        echo "  restart - 重启采集器"
        echo "  status  - 查看运行状态"
        exit 1
        ;;
esac

exit $?
