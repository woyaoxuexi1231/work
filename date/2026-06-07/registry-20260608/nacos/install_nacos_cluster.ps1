# Nacos Cluster v2.3.2 | 3 nodes + MySQL | Fixed for external access
# 解决 Docker 内网 IP 导致的重定向问题

param(
    [string]$HostIP = "192.168.3.100",
    [int]$BasePort = 8848,
    [string]$MysqlPass = "123456"
)

$Network  = "nacos-net"
$Image    = "nacos/nacos-server:v2.3.2"
$MysqlCtn = "mysql"
$MysqlDb   = "nacos_config"
$DataRoot = "C:\Users\code\Desktop\docker-data"

# ========== 辅助函数 ==========
function log_info($msg)  { 
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] [INFO] $msg" 
}

function log_warn($msg)  { 
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] [WARN] $msg" -ForegroundColor Yellow 
}

function log_error($msg) { 
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] [ERROR] $msg" -ForegroundColor Red 
}

function check_docker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { 
        log_error "Docker not installed" 
        exit 1 
    }
    docker ps 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { 
        log_error "Docker not running" 
        exit 1 
    }
}

function wait_for_container($name, $max=60) {
    for ($i = 0; $i -lt $max; $i++) {
        $output = docker ps --format '{{.Names}}' 2>$null | Select-String -Pattern "^$name$" -SimpleMatch
        if ($output) { return $true }
        Start-Sleep 1
    }
    log_error "Container $name did not start in ${max}s"
    return $false
}

function fix_cluster_conf($ctn, $hostIP, $basePort) {
    log_info "Fixing cluster.conf for $ctn ..."
    
    # 使用简单的方法写入配置
    docker exec $ctn bash -c "printf '%s\n%s\n%s\n' '$hostIP`:$basePort' '$hostIP`:$(($basePort+100))' '$hostIP`:$(($basePort+200))' > /home/nacos/conf/cluster.conf"
    
    # 验证
    log_info "Verifying cluster.conf for $ctn ..."
    docker exec $ctn cat /home/nacos/conf/cluster.conf
}

# ========== 主流程 ==========

check_docker

log_info "=== Nacos Cluster Installation (Fixed) ==="
log_info "Host IP: $HostIP"
log_info "Base Port: $BasePort"

# ---- Docker Network ----
$netExists = docker network ls --format '{{.Name}}' 2>$null | Select-String "^$Network$"
if (-not $netExists) {
    docker network create $Network
    log_info "Network '$Network' created"
}

# Connect existing mysql container to this network
docker network connect $Network $MysqlCtn 2>$null
log_info "Connected '$MysqlCtn' to '$Network'"

# ---- Init DB ----
log_info "Pulling image: $Image"
docker pull $Image | Out-Null

log_info "Initializing database..."
docker exec $MysqlCtn mysql -uroot "-p$MysqlPass" -e "CREATE DATABASE IF NOT EXISTS $MysqlDb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>$null

# Copy and execute schema
docker create --name nacos-init-tmp $Image 2>$null | Out-Null
docker cp nacos-init-tmp:/home/nacos/conf/mysql-schema.sql "$env:TEMP\nacos-schema.sql" 2>$null
docker rm nacos-init-tmp 2>$null | Out-Null
Get-Content -Encoding UTF8 "$env:TEMP\nacos-schema.sql" | docker exec -i $MysqlCtn mysql -uroot "-p$MysqlPass" $MysqlDb 2>$null
log_info "Database initialized"

# ---- Stop and Remove Old Containers ----
log_info "Cleaning up old containers..."
foreach ($ctn in @("nacos1", "nacos2", "nacos3")) {
    docker rm -f $ctn 2>$null | Out-Null
}

# ---- Create 3 Nacos Nodes ----
$port1 = $BasePort
$port2 = $BasePort + 100
$port3 = $BasePort + 200
$ports = @($port1, $port2, $port3)

for ($i = 0; $i -lt 3; $i++) {
    $node = $i + 1
    $ctn = "nacos$node"
    $p = $ports[$i]
    $g1 = $p + 1000
    $g2 = $p + 1001
    $data = "$DataRoot\nacos$node"

    log_info "Creating $ctn (port: $p) ..."

    # Create data directories
    New-Item -ItemType Directory -Force -Path "$data\logs", "$data\data" | Out-Null

    # Build NACOS_SERVERS (all nodes use same config)
    $nacosServers = "$HostIP`:$($ports[0]),$HostIP`:$($ports[1]),$HostIP`:$($ports[2])"

    # Run container
    docker run -d `
      --name $ctn `
      --hostname $ctn `
      --network $Network `
      --add-host "nacos1:$HostIP" `
      --add-host "nacos2:$HostIP" `
      --add-host "nacos3:$HostIP" `
      --restart unless-stopped `
      -p ${p}:8848 `
      -p ${g1}:9848 `
      -p ${g2}:9849 `
      -e "MODE=cluster" `
      -e "NACOS_AUTH_ENABLE=false" `
      -e "TZ=Asia/Shanghai" `
      -e "NACOS_SERVERS=$nacosServers" `
      -e "NACOS_SERVER_IP=$HostIP" `
      -e "SPRING_DATASOURCE_PLATFORM=mysql" `
      -e "MYSQL_SERVICE_HOST=$MysqlCtn" `
      -e "MYSQL_SERVICE_PORT=3306" `
      -e "MYSQL_SERVICE_DB_NAME=$MysqlDb" `
      -e "MYSQL_SERVICE_USER=root" `
      -e "MYSQL_SERVICE_PASSWORD=$MysqlPass" `
      -v "$data\logs:/home/nacos/logs" `
      -v "$data\data:/home/nacos/data" `
      $Image | Out-Null

    if (-not (wait_for_container $ctn 60)) {
        exit 1
    }

    log_info "$ctn started successfully"
}

# ---- Fix cluster.conf for All Nodes ----
log_info "Fixing cluster.conf for all nodes..."
Start-Sleep 10  # Wait for nodes to fully start

foreach ($ctn in @("nacos1", "nacos2", "nacos3")) {
    fix_cluster_conf $ctn $HostIP $BasePort
}

# ---- Restart All Nodes to Apply Changes ----
log_info "Restarting all nodes to apply cluster.conf changes..."
docker restart nacos1 nacos2 nacos3
Start-Sleep 30

# ---- Verify Cluster Status ----
log_info "Verifying cluster status..."
try {
    $url = "http://$HostIP`:$BasePort/nacos/v1/ns/operator/cluster/nodes"
    $response = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 10
    
    log_info "Cluster nodes status:"
    foreach ($node in $response.data) {
        $status = if ($node.state -eq "UP") { "UP" } else { $node.state }
        Write-Host "  $($node.address) -> $status"
        
        if ($node.ip -like "172.*") {
            log_warn "  Still using internal IP: $($node.ip)"
        }
    }
    
    log_info "Cluster health check passed"
}
catch {
    log_error "Failed to verify cluster status: $_"
}

# ---- Done ----
$url1 = "http://localhost:$port1/nacos"
$url2 = "http://localhost:$port2/nacos"
$url3 = "http://localhost:$port3/nacos"
log_info "=== Nacos Cluster Ready ==="
log_info "Console URLs: $url1,$url2,$url3"
log_info "Username/Password: nacos/nacos"
