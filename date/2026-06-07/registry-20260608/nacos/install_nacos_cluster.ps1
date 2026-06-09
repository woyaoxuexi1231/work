# Nacos Cluster v2.3.2 | 3 nodes + existing MySQL (container: mysql) | nacos/nacos
. "$PSScriptRoot/lib/common.ps1"

check_docker

$Network  = "nacos-net"
$Image    = "nacos/nacos-server:$($env:NACOS_VERSION -replace '^$','v2.3.2')"
$BasePort = if ($env:NACOS_PORT) { [int]$env:NACOS_PORT } else { 8848 }

# Reuse existing MySQL container
$MysqlCtn = "mysql"
$MysqlPass = if ($env:MYSQL_PASS) { $env:MYSQL_PASS } else { "123456" }
$MysqlDb   = "nacos_config"

# ---- Docker Network ----
if (-not (docker network ls --format '{{.Name}}' | Select-String "^$Network$")) {
    docker network create $Network; log_info "Network '$Network' created"
}
# Connect existing mysql container to this network
docker network connect $Network $MysqlCtn 2>$null
log_info "Connected '$MysqlCtn' to '$Network'"

# ---- Init DB + nacos schema ----
pull_image $Image
log_info "Creating database & initializing Nacos schema..."

docker exec $MysqlCtn mysql -uroot "-p$MysqlPass" -e "CREATE DATABASE IF NOT EXISTS $MysqlDb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

docker create --name nacos-init-tmp $Image 2>$null | Out-Null
docker cp nacos-init-tmp:/home/nacos/conf/mysql-schema.sql "$env:TEMP\nacos-schema.sql" 2>$null
docker rm nacos-init-tmp 2>$null | Out-Null
Get-Content -Encoding UTF8 "$env:TEMP\nacos-schema.sql" | docker exec -i $MysqlCtn mysql -uroot "-p$MysqlPass" $MysqlDb

log_info "Schema initialized"

# ---- 3 Nacos Nodes ----
for ($i = 0; $i -lt 3; $i++) {
    $node = $i + 1; $ctn = "nacos$node"
    $offset = $i * 100
    $p    = $BasePort + $offset
    $g1   = $p + 1000; $g2 = $p + 1001
    $data = "${DataRoot}\nacos$node"

    if (check_container_exists $ctn) { continue }
    cleanup_container $ctn
    New-Item -ItemType Directory -Force -Path "$data\logs","$data\data" | Out-Null

    docker run -d --name $ctn --network $Network --restart unless-stopped `
      -p ${p}:8848 -p ${g1}:9848 -p ${g2}:9849 `
       --name $ctn `
      --hostname $ctn `
      --network $Network `
      -e MODE=cluster -e NACOS_AUTH_ENABLE=false -e TZ=Asia/Shanghai `
      -e NACOS_SERVERS="nacos1:8848,nacos2:8848,nacos3:8848" `
      -e SPRING_DATASOURCE_PLATFORM=mysql `
      -e MYSQL_SERVICE_HOST=$MysqlCtn -e MYSQL_SERVICE_PORT=3306 `
      -e MYSQL_SERVICE_DB_NAME=$MysqlDb `
      -e MYSQL_SERVICE_USER=root -e MYSQL_SERVICE_PASSWORD=$MysqlPass `
      -v "${data}\logs:/home/nacos/logs" -v "${data}\data:/home/nacos/data" `
      $Image

    wait_for_container $ctn 60
    log_info "$ctn  ->  http://localhost:${p}/nacos"
}

$urls = ((0..2).ForEach({ "http://localhost:$($BasePort + $_ * 100)/nacos" }) -join ', ')
done_banner "Nacos Cluster | $urls | nacos/nacos"
