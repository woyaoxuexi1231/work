# Nacos Cluster v2.3.2 | 3 nodes + existing MySQL | nacos/nacos
. "$PSScriptRoot/lib/common.ps1"

check_docker

$Network  = "nacos-net"
$Image    = "nacos/nacos-server:$($env:NACOS_VERSION -replace '^$','v2.3.2')"
$BasePort = if ($env:NACOS_PORT) { [int]$env:NACOS_PORT } else { 8848 }

# Existing MySQL (Docker Desktop -> host.docker.internal)
$MysqlHost = if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { "host.docker.internal" }
$MysqlPort = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { "3306" }
$MysqlUser = if ($env:MYSQL_USER) { $env:MYSQL_USER } else { "root" }
$MysqlPass = if ($env:MYSQL_PASS) { $env:MYSQL_PASS } else { "132456" }
$MysqlDb   = "nacos_config"

# ---- Docker Network ----
if (-not (docker network ls --format '{{.Name}}' | Select-String "^$Network$")) {
    docker network create $Network; log_info "Network '$Network' created"
}

# ---- Init DB + nacos schema ----
pull_image $Image
log_info "Creating database & initializing Nacos schema..."

"CREATE DATABASE IF NOT EXISTS $MysqlDb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" `
  | docker run --rm -i --network $Network mysql:8.0 mysql -h$MysqlHost -P$MysqlPort -u$MysqlUser -p$MysqlPass

docker create --name nacos-init-tmp $Image 2>$null | Out-Null
docker cp nacos-init-tmp:/home/nacos/conf/mysql-schema.sql "$env:TEMP\nacos-schema.sql" 2>$null
docker rm nacos-init-tmp 2>$null | Out-Null
Get-Content "$env:TEMP\nacos-schema.sql" `
  | docker run --rm -i --network $Network mysql:8.0 mysql -h$MysqlHost -P$MysqlPort -u$MysqlUser -p$MysqlPass $MysqlDb

log_info "Schema initialized"

# ---- 3 Nacos Nodes ----
for ($i = 0; $i -lt 3; $i++) {
    $node = $i + 1; $ctn = "nacos$node"
    $p    = $BasePort + $i
    $g1   = $p + 1000; $g2 = $p + 1001
    $data = "${DataRoot}\nacos$node"

    if (check_container_exists $ctn) { continue }
    cleanup_container $ctn
    New-Item -ItemType Directory -Force -Path "$data\logs","$data\data" | Out-Null

    docker run -d --name $ctn --network $Network --restart unless-stopped `
      -p ${p}:8848 -p ${g1}:9848 -p ${g2}:9849 `
      -e MODE=cluster -e NACOS_AUTH_ENABLE=false -e TZ=Asia/Shanghai `
      -e NACOS_SERVERS="nacos1:8848,nacos2:8848,nacos3:8848" `
      -e SPRING_DATASOURCE_PLATFORM=mysql `
      -e MYSQL_SERVICE_HOST=$MysqlHost -e MYSQL_SERVICE_PORT=$MysqlPort `
      -e MYSQL_SERVICE_DB_NAME=$MysqlDb `
      -e MYSQL_SERVICE_USER=$MysqlUser -e MYSQL_SERVICE_PASSWORD=$MysqlPass `
      -v "${data}\logs:/home/nacos/logs" -v "${data}\data:/home/nacos/data" `
      $Image

    wait_for_container $ctn 60
    log_info "$ctn  ->  http://localhost:${p}/nacos"
}

$urls = ((0..2).ForEach({ "http://localhost:$($BasePort + $_)/nacos" }) -join ', ')
done_banner "Nacos Cluster | $urls | nacos/nacos"
