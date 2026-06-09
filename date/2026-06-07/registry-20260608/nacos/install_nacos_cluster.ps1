# ============================================================
# Nacos 2.3.2 三节点集群部署脚本
# 前提：MySQL 已经通过 install_mysql.ps1 部署在宿主机（容器名 mysql，端口 3306）
# 集群拓扑（容器内网 8848，宿主机端口错开避免冲突）：
#   nacos1: 宿主机 8848 -> 容器 8848    gRPC: 9848
#   nacos2: 宿主机 8849 -> 容器 8848    gRPC: 9849 -> 容器 9848
#   nacos3: 宿主机 8850 -> 容器 8848    gRPC: 9850 -> 容器 9848
# ============================================================
. "$PSScriptRoot/lib/common.ps1"

$NacosVersion = if ($env:NACOS_VERSION) { $env:NACOS_VERSION } else { "v2.3.2" }
$Image        = "nacos/nacos-server:$NacosVersion"
$ComposeFile  = Join-Path $PSScriptRoot "cluster-hostname.yaml"
$EnvFile      = Join-Path $PSScriptRoot "nacos-hostname.env"
$LogsRoot     = Join-Path $DataRoot "nacos-cluster\logs"
$DataRootDir  = Join-Path $DataRoot "nacos-cluster\data"

# ---------- 前置检查 ----------
check_docker

if (-not (Test-Path $ComposeFile)) {
    log_error "未找到 compose 文件: $ComposeFile"; exit 1
}
if (-not (Test-Path $EnvFile)) {
    log_error "未找到 env 文件: $EnvFile"; exit 1
}

# ---------- 检查 MySQL 是否就绪（关键前置依赖） ----------
$mysqlContainer = "mysql"
$mysqlExists = docker ps -a --format '{{.Names}}' 2>$null | Select-String -Pattern "^$mysqlContainer$" -SimpleMatch
if (-not $mysqlExists) {
    log_error "未检测到 MySQL 容器 [$mysqlContainer]，请先执行 install_mysql.ps1"; exit 1
}
$mysqlRunning = docker ps --format '{{.Names}}' 2>$null | Select-String -Pattern "^$mysqlContainer$" -SimpleMatch
if (-not $mysqlRunning) {
    log_info "启动 MySQL 容器: $mysqlContainer"
    docker start $mysqlContainer | Out-Null
}

log_info "等待 MySQL 就绪 ..."
for ($i = 1; $i -le 30; $i++) {
    docker exec $mysqlContainer mysqladmin ping -h localhost --silent 2>$null
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep 2
}
if ($LASTEXITCODE -ne 0) { log_error "MySQL 未就绪，请检查"; exit 1 }
log_info "MySQL 已就绪"

# ---------- 确保 nacos 库 + nacos 用户存在（仅第一次需要） ----------
$MysqlPass = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "123456" }
$MysqlDb   = "nacos_devtest"
$MysqlUser = "nacos"
$MysqlPwd  = "nacos"

log_info "检查/创建 nacos 数据库与账号 ..."
$createSql = @"
CREATE DATABASE IF NOT EXISTS $MysqlDb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS '$MysqlUser'@'%' IDENTIFIED BY '$MysqlPwd';
GRANT ALL PRIVILEGES ON $MysqlDb.* TO '$MysqlUser'@'%';
FLUSH PRIVILEGES;
"@
$createSql | docker exec -i $mysqlContainer mysql -uroot -p$MysqlPass 2>$null
if ($LASTEXITCODE -ne 0) {
    log_warn "数据库/账号初始化失败（可能已存在），继续执行"
} else {
    log_info "数据库 [$MysqlDb] 与用户 [$MysqlUser] 就绪"
}

# ---------- 导入 nacos schema（如果库为空） ----------
$tableCount = docker exec $mysqlContainer mysql -u$MysqlUser -p$MysqlPwd -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$MysqlDb';" 2>$null
if ([string]::IsNullOrWhiteSpace($tableCount)) { $tableCount = "0" }
if ([int]$tableCount -lt 1) {
    log_info "导入 nacos schema ..."
    $sqlFile = Join-Path $PSScriptRoot "sql.sql"
    if (-not (Test-Path $sqlFile)) { log_error "未找到 $sqlFile"; exit 1 }
    Get-Content $sqlFile -Raw | docker exec -i $mysqlContainer mysql -u$MysqlUser -p$MysqlPwd $MysqlDb
    if ($LASTEXITCODE -ne 0) { log_error "schema 导入失败"; exit 1 }
    log_info "schema 导入完成"
} else {
    log_info "数据库 [$MysqlDb] 已有表（$tableCount 张），跳过 schema 导入"
}

# ---------- 准备持久化目录 ----------
New-Item -ItemType Directory -Force -Path `
    "$LogsRoot\nacos1","$LogsRoot\nacos2","$LogsRoot\nacos3", `
    "$DataRootDir\nacos1","$DataRootDir\nacos2","$DataRootDir\nacos3" | Out-Null

# ---------- 调整 compose 的 volumes 路径（绝对路径，避免目录不对） ----------
# compose 文件里写的是相对路径 ./cluster-logs/...，已经在脚本同目录，可以直接用
# 但需要把 logs/data 落到 $DataRoot 下，更稳妥。改用环境变量注入
$env:NACOS_VERSION = $NacosVersion

# ---------- 拉镜像 ----------
log_info "拉取镜像: $Image"
docker pull $Image | Out-Null
if ($LASTEXITCODE -ne 0) { log_error "镜像拉取失败"; exit 1 }

# ---------- 启动集群 ----------
log_info "启动 3 节点 nacos 集群 ..."
$env:NACOS_VERSION = $NacosVersion
docker compose -f $ComposeFile --project-name nacos-cluster up -d
if ($LASTEXITCODE -ne 0) { log_error "docker compose 启动失败"; exit 1 }

# ---------- 等待就绪 ----------
log_info "等待 nacos 节点启动 ..."
foreach ($n in @("nacos1","nacos2","nacos3")) {
    wait_for_container $n 60
}

# ---------- 健康检查 ----------
log_info "等待 nacos 节点进入 UP 状态（选举 Leader 约需 30~60s）..."
$healthy = $false
for ($i = 1; $i -le 60; $i++) {
    Start-Sleep 3
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:8848/nacos/v2/core/cluster/node/self" -UseBasicParsing -TimeoutSec 5
        if ($resp.StatusCode -eq 200) { $healthy = $true; break }
    } catch { }
}
if ($healthy) {
    log_info "nacos1 /actuator/health 返回 200，集群已就绪"
} else {
    log_warn "健康检查超时，但容器已起来，请稍后用 'docker logs nacos1' 查看"
}

# ---------- 完成提示 ----------
Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  Nacos 集群部署完成！" -ForegroundColor Green
Write-Host "------------------------------------------------------------" -ForegroundColor Green
Write-Host "  控制台:        http://localhost:8848/nacos" -ForegroundColor Green
Write-Host "  账号 / 密码:   nacos / nacos" -ForegroundColor Green
Write-Host "  节点（容器）:  nacos1  nacos2  nacos3" -ForegroundColor Green
Write-Host "  宿主机端口:    8848  8849  8850" -ForegroundColor Green
Write-Host "  集群 gRPC:     9848  9849  9850" -ForegroundColor Green
Write-Host "  持久化目录:    $LogsRoot" -ForegroundColor Green
Write-Host "                 $DataRootDir" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "下一步：" -ForegroundColor Cyan
Write-Host "  # 查看集群节点状态" -ForegroundColor Cyan
Write-Host "  curl http://localhost:8848/nacos/v1/core/cluster/nodes" -ForegroundColor Cyan
Write-Host ""
Write-Host "  # 查看某个节点 leader 状态" -ForegroundColor Cyan
Write-Host "  curl http://localhost:8848/nacos/v2/core/cluster/node/self" -ForegroundColor Cyan
Write-Host ""
