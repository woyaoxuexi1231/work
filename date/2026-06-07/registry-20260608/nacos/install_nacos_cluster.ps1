# Nacos Cluster v2.3.2 - 3 nodes + MySQL
# Use host IP to avoid Docker internal IP redirect issue

$Network  = "nacos-net"
$Image    = "nacos/nacos-server:v2.3.2"
$HostIP   = "192.168.3.100"
$MysqlCtn = "mysql"
$MysqlPass = "123456"
$MysqlDb   = "nacos_config"
$DataRoot = "C:\Users\code\Desktop\docker-data"

function log_info($msg)  { Write-Host "[$(Get-Date -Format 'HH:mm:ss')] [INFO] $msg" }
function log_warn($msg)  { Write-Host "[$(Get-Date -Format 'HH:mm:ss')] [WARN] $msg" -ForegroundColor Yellow }
function log_error($msg) { Write-Host "[$(Get-Date -Format 'HH:mm:ss')] [ERROR] $msg" -ForegroundColor Red }

# ========== Step 1: Check Docker ==========
log_info "Step 1: Check Docker"
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { 
    log_error "Docker not installed"; exit 1 
}
docker ps 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) { 
    log_error "Docker not running"; exit 1 
}
log_info "Docker OK"

# ========== Step 2: Create Network ==========
log_info "Step 2: Create Docker network"
$netExists = docker network ls --format '{{.Names}}' 2>$null | Select-String "^$Network$"
if (-not $netExists) {
    docker network create $Network
    log_info "Network '$Network' created"
}
docker network connect $Network $MysqlCtn 2>$null
log_info "Network ready"

# ========== Step 3: Init Database ==========
log_info "Step 3: Init database"
docker image inspect $Image 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    log_info "Pulling image: $Image"
    docker pull $Image | Out-Null
}

docker exec $MysqlCtn mysql -uroot "-p$MysqlPass" -e "CREATE DATABASE IF NOT EXISTS $MysqlDb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>$null

docker create --name nacos-init-tmp $Image 2>$null | Out-Null
docker cp nacos-init-tmp:/home/nacos/conf/mysql-schema.sql "$env:TEMP\nacos-schema.sql" 2>$null
docker rm nacos-init-tmp 2>$null | Out-Null
Get-Content -Encoding UTF8 "$env:TEMP\nacos-schema.sql" | docker exec -i $MysqlCtn mysql -uroot "-p$MysqlPass" $MysqlDb 2>$null
log_info "Database initialized"

# ========== Step 4: Clean Up Old Containers ==========
log_info "Step 4: Clean up old containers"
docker rm -f nacos1 2>$null | Out-Null
docker rm -f nacos2 2>$null | Out-Null
docker rm -f nacos3 2>$null | Out-Null
log_info "Old containers removed"
log_info "Waiting 5s for ports to be released..."
Start-Sleep 5

# ========== Step 5: Create nacos1 (port 8848) ==========
log_info "Step 5: Create nacos1 (port 8848)"
$data1 = "$DataRoot\nacos1"
New-Item -ItemType Directory -Force -Path "$data1\logs", "$data1\data" | Out-Null

docker run -d `
  --name nacos1 `
  --hostname nacos1 `
  --network $Network `
  --add-host "nacos1:$HostIP" `
  --add-host "nacos2:$HostIP" `
  --add-host "nacos3:$HostIP" `
  --restart unless-stopped `
  -p "8848:8848" `
  -p "9848:9848" `
  -p "9849:9849" `
  -e "MODE=cluster" `
  -e "NACOS_AUTH_ENABLE=false" `
  -e "TZ=Asia/Shanghai" `
  -e "NACOS_SERVERS=$HostIP`:8848,$HostIP`:8849,$HostIP`:8850" `
  -e "NACOS_SERVER_IP=$HostIP" `
  -e "SPRING_DATASOURCE_PLATFORM=mysql" `
  -e "MYSQL_SERVICE_HOST=$MysqlCtn" `
  -e "MYSQL_SERVICE_PORT=3306" `
  -e "MYSQL_SERVICE_DB_NAME=$MysqlDb" `
  -e "MYSQL_SERVICE_USER=root" `
  -e "MYSQL_SERVICE_PASSWORD=$MysqlPass" `
  -v "$data1\logs:/home/nacos/logs" `
  -v "$data1\data:/home/nacos/data" `
  $Image | Out-Null
log_info "nacos1 created"

# ========== Step 6: Create nacos2 (port 8849) ==========
log_info "Step 6: Create nacos2 (port 8849)"
$data2 = "$DataRoot\nacos2"
New-Item -ItemType Directory -Force -Path "$data2\logs", "$data2\data" | Out-Null

docker run -d `
  --name nacos2 `
  --hostname nacos2 `
  --network $Network `
  --add-host "nacos1:$HostIP" `
  --add-host "nacos2:$HostIP" `
  --add-host "nacos3:$HostIP" `
  --restart unless-stopped `
  -p "8849:8848" `
  -p "9850:9848" `
  -p "9851:9849" `
  -e "MODE=cluster" `
  -e "NACOS_AUTH_ENABLE=false" `
  -e "TZ=Asia/Shanghai" `
  -e "NACOS_SERVERS=$HostIP`:8848,$HostIP`:8849,$HostIP`:8850" `
  -e "NACOS_SERVER_IP=$HostIP" `
  -e "SPRING_DATASOURCE_PLATFORM=mysql" `
  -e "MYSQL_SERVICE_HOST=$MysqlCtn" `
  -e "MYSQL_SERVICE_PORT=3306" `
  -e "MYSQL_SERVICE_DB_NAME=$MysqlDb" `
  -e "MYSQL_SERVICE_USER=root" `
  -e "MYSQL_SERVICE_PASSWORD=$MysqlPass" `
  -v "$data2\logs:/home/nacos/logs" `
  -v "$data2\data:/home/nacos/data" `
  $Image | Out-Null
log_info "nacos2 created"

# ========== Step 7: Create nacos3 (port 8850) ==========
log_info "Step 7: Create nacos3 (port 8850)"
$data3 = "$DataRoot\nacos3"
New-Item -ItemType Directory -Force -Path "$data3\logs", "$data3\data" | Out-Null

docker run -d `
  --name nacos3 `
  --hostname nacos3 `
  --network $Network `
  --add-host "nacos1:$HostIP" `
  --add-host "nacos2:$HostIP" `
  --add-host "nacos3:$HostIP" `
  --restart unless-stopped `
  -p "8850:8848" `
  -p "9852:9848" `
  -p "9853:9849" `
  -e "MODE=cluster" `
  -e "NACOS_AUTH_ENABLE=false" `
  -e "TZ=Asia/Shanghai" `
  -e "NACOS_SERVERS=$HostIP`:8848,$HostIP`:8849,$HostIP`:8850" `
  -e "NACOS_SERVER_IP=$HostIP" `
  -e "SPRING_DATASOURCE_PLATFORM=mysql" `
  -e "MYSQL_SERVICE_HOST=$MysqlCtn" `
  -e "MYSQL_SERVICE_PORT=3306" `
  -e "MYSQL_SERVICE_DB_NAME=$MysqlDb" `
  -e "MYSQL_SERVICE_USER=root" `
  -e "MYSQL_SERVICE_PASSWORD=$MysqlPass" `
  -v "$data3\logs:/home/nacos/logs" `
  -v "$data3\data:/home/nacos/data" `
  $Image | Out-Null
log_info "nacos3 created"
#
## ========== Step 8: Wait and Fix cluster.conf ==========
#log_info "Step 8: Wait 30s for Nacos to start, then fix cluster.conf"
#Start-Sleep 30
#
#log_info "Fixing cluster.conf for nacos1 ..."
#docker exec nacos1 bash -c "printf '192.168.3.100:8848\n192.168.3.100:8849\n192.168.3.100:8850\n' > /home/nacos/conf/cluster.conf"
#docker exec nacos1 cat /home/nacos/conf/cluster.conf
#
#log_info "Fixing cluster.conf for nacos2 ..."
#docker exec nacos2 bash -c "printf '192.168.3.100:8848\n192.168.3.100:8849\n192.168.3.100:8850\n' > /home/nacos/conf/cluster.conf"
#docker exec nacos2 cat /home/nacos/conf/cluster.conf
#
#log_info "Fixing cluster.conf for nacos3 ..."
#docker exec nacos3 bash -c "printf '192.168.3.100:8848\n192.168.3.100:8849\n192.168.3.100:8850\n' > /home/nacos/conf/cluster.conf"
#docker exec nacos3 cat /home/nacos/conf/cluster.conf
#
## ========== Step 9: Restart to Apply cluster.conf ==========
#log_info "Step 9: Restart all nodes to apply cluster.conf"
#docker restart nacos1 nacos2 nacos3
#log_info "Waiting 60s for cluster to be ready..."
#Start-Sleep 60
#
## ========== Step 10: Verify ==========
#log_info "Step 10: Verify cluster status"
#try {
#    $url = "http://$HostIP`:8848/nacos/v1/ns/operator/cluster/nodes"
#    $response = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 10
#    log_info "Cluster nodes:"
#    foreach ($node in $response.data) {
#        if ($node.state -eq "UP") {
#            Write-Host "  [UP] $($node.address)" -ForegroundColor Green
#        } else {
#            Write-Host "  [$($node.state)] $($node.address)" -ForegroundColor Red
#        }
#    }
#}
#catch {
#    log_error "Cluster check failed: $_"
#    log_info "You can check manually: curl http://$HostIP`:8848/nacos/v1/ns/operator/cluster/nodes"
#}

# ========== Done ==========
log_info "=== Nacos Cluster Ready ==="
log_info "URLs: http://localhost:8848/nacos, http://localhost:8849/nacos, http://localhost:8850/nacos"
log_info "Account: nacos/nacos"
