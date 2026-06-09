# ============================================================
# Nacos 2.3.2 - 3-Node Cluster Deployment Script (Windows)
# Prereq: MySQL must already be running on host (deployed by install_mysql.ps1,
#         container name "mysql", host port 3306, root pass 123456).
# Cluster topology (host ports offset to avoid conflicts):
#   nacos1: host 8848 -> container 8848    gRPC: 9848 / 9849
#   nacos2: host 8849 -> container 8848    gRPC: 9849 / 9850
#   nacos3: host 8850 -> container 8848    gRPC: 9851 / 9852
# ============================================================
. "$PSScriptRoot/lib/common.ps1"

$NacosVersion = if ($env:NACOS_VERSION) { $env:NACOS_VERSION } else { "v2.3.2" }
$Image        = "nacos/nacos-server:$NacosVersion"
$ComposeFile  = Join-Path $PSScriptRoot "cluster-hostname.yaml"
$EnvFile      = Join-Path $PSScriptRoot "nacos-hostname.env"
$LogsRoot     = Join-Path $DataRoot "nacos-cluster\logs"
$DataRootDir  = Join-Path $DataRoot "nacos-cluster\data"

# ---------- Pre-checks ----------
check_docker

if (-not (Test-Path $ComposeFile)) {
    log_error "compose file not found: $ComposeFile"
    exit 1
}
if (-not (Test-Path $EnvFile)) {
    log_error "env file not found: $EnvFile"
    exit 1
}

# ---------- Check MySQL is up (mandatory pre-req) ----------
$mysqlContainer = "mysql"
$mysqlExists = docker ps -a --format '{{.Names}}' 2>$null | Select-String -Pattern "^$mysqlContainer$" -SimpleMatch
if (-not $mysqlExists) {
    log_error "MySQL container [$mysqlContainer] not found, please run install_mysql.ps1 first"
    exit 1
}
$mysqlRunning = docker ps --format '{{.Names}}' 2>$null | Select-String -Pattern "^$mysqlContainer$" -SimpleMatch
if (-not $mysqlRunning) {
    log_info "starting MySQL container: $mysqlContainer"
    docker start $mysqlContainer | Out-Null
}

log_info "waiting for MySQL to be ready ..."
for ($i = 1; $i -le 30; $i++) {
    docker exec $mysqlContainer mysqladmin ping -h localhost --silent 2>$null
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep 2
}
if ($LASTEXITCODE -ne 0) {
    log_error "MySQL is not ready, please check"
    exit 1
}
log_info "MySQL is ready"

# ---------- Ensure nacos database + user exist (idempotent) ----------
$MysqlPass = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "123456" }
$MysqlDb   = "nacos_devtest"
$MysqlUser = "nacos"
$MysqlPwd  = "nacos"

log_info "checking/creating nacos database and account ..."
$createSql = @"
CREATE DATABASE IF NOT EXISTS $MysqlDb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS '$MysqlUser'@'%' IDENTIFIED BY '$MysqlPwd';
GRANT ALL PRIVILEGES ON $MysqlDb.* TO '$MysqlUser'@'%';
FLUSH PRIVILEGES;
"@
$createSql | docker exec -i $mysqlContainer mysql -uroot -p$MysqlPass 2>$null
if ($LASTEXITCODE -ne 0) {
    log_warn "db/user init failed (may already exist), continuing"
}
else {
    log_info "database [$MysqlDb] and user [$MysqlUser] are ready"
}

# ---------- Import nacos schema (only when database is empty) ----------
$tableCount = docker exec $mysqlContainer mysql -u$MysqlUser -p$MysqlPwd -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$MysqlDb';" 2>$null
if ([string]::IsNullOrWhiteSpace($tableCount)) { $tableCount = "0" }
if ([int]$tableCount -lt 1) {
    log_info "importing nacos schema ..."
    $sqlFile = Join-Path $PSScriptRoot "sql.sql"
    if (-not (Test-Path $sqlFile)) {
        log_error "sql file not found: $sqlFile"
        exit 1
    }
    Get-Content $sqlFile -Raw | docker exec -i $mysqlContainer mysql -u$MysqlUser -p$MysqlPwd $MysqlDb
    if ($LASTEXITCODE -ne 0) {
        log_error "schema import failed"
        exit 1
    }
    log_info "schema import done"
}
else {
    log_info "database [$MysqlDb] already has $tableCount table(s), skip schema import"
}

# ---------- Prepare persistent directories ----------
New-Item -ItemType Directory -Force -Path `
    "$LogsRoot\nacos1","$LogsRoot\nacos2","$LogsRoot\nacos3", `
    "$DataRootDir\nacos1","$DataRootDir\nacos2","$DataRootDir\nacos3" | Out-Null

# ---------- Pull image ----------
log_info "pulling image: $Image"
$env:NACOS_VERSION = $NacosVersion
docker pull $Image | Out-Null
if ($LASTEXITCODE -ne 0) {
    log_error "image pull failed"
    exit 1
}

# ---------- Start cluster ----------
log_info "starting 3-node nacos cluster ..."
docker compose -f $ComposeFile --project-name nacos-cluster up -d
if ($LASTEXITCODE -ne 0) {
    log_error "docker compose up failed"
    exit 1
}

# ---------- Wait for containers to be up ----------
log_info "waiting for nacos containers to start ..."
foreach ($n in @("nacos1","nacos2","nacos3")) {
    wait_for_container $n 60
}

# ---------- Health check (Raft leader election takes ~30-60s) ----------
log_info "waiting for nacos to enter UP state (raft leader election may take 30~60s) ..."
$healthy = $false
for ($i = 1; $i -le 60; $i++) {
    Start-Sleep 3
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:8848/nacos/v2/core/cluster/node/self" -UseBasicParsing -TimeoutSec 5
        if ($resp.StatusCode -eq 200) { $healthy = $true; break }
    }
    catch { }
}
if ($healthy) {
    log_info "nacos1 health check returned 200, cluster is ready"
}
else {
    log_warn "health check timed out, but containers are up. Please check with 'docker logs nacos1'"
}

# ---------- Done banner ----------
Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  Nacos cluster deployed successfully!" -ForegroundColor Green
Write-Host "------------------------------------------------------------" -ForegroundColor Green
Write-Host "  Console:        http://localhost:8848/nacos" -ForegroundColor Green
Write-Host "  Username/Pass:  nacos / nacos" -ForegroundColor Green
Write-Host "  Nodes:          nacos1  nacos2  nacos3" -ForegroundColor Green
Write-Host "  Host HTTP:      8848  8849  8850" -ForegroundColor Green
Write-Host "  Host gRPC:      9848  9849  9850" -ForegroundColor Green
Write-Host "  Logs:           $LogsRoot" -ForegroundColor Green
Write-Host "  Data:           $DataRootDir" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  # list cluster nodes" -ForegroundColor Cyan
Write-Host "  curl http://localhost:8848/nacos/v1/core/cluster/nodes" -ForegroundColor Cyan
Write-Host ""
Write-Host "  # check current leader" -ForegroundColor Cyan
Write-Host "  curl http://localhost:8848/nacos/v2/core/cluster/node/self" -ForegroundColor Cyan
Write-Host ""
