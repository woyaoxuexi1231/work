# Nacos Cluster v2.3.2 | 3 nodes + MySQL | nacos/nacos
. "$PSScriptRoot/lib/common.ps1"

check_docker

$Network   = "nacos-net"
$Image     = "nacos/nacos-server:$($env:NACOS_VERSION -replace '^$','v2.3.2')"
$BasePort  = if ($env:NACOS_PORT) { [int]$env:NACOS_PORT } else { 8848 }
$MysqlPass = if ($env:MYSQL_PASS) { $env:MYSQL_PASS } else { "root123" }

# ---- Docker Network ----
if (-not (docker network ls --format '{{.Name}}' | Select-String "^$Network$")) {
    docker network create $Network; log_info "Network '$Network' created"
}

# ---- MySQL ----
$mysqlCtn = "nacos-mysql"
if (-not (check_container_exists $mysqlCtn)) {
    cleanup_container $mysqlCtn
    $mysqlDir = "${DataRoot}\nacos-mysql"
    New-Item -ItemType Directory -Force -Path $mysqlDir | Out-Null
    docker run -d --name $mysqlCtn --network $Network --restart unless-stopped `
      -e MYSQL_ROOT_PASSWORD=$MysqlPass -e MYSQL_DATABASE=nacos_config -e TZ=Asia/Shanghai `
      -v "${mysqlDir}:/var/lib/mysql" mysql:8.0
    wait_for_container $mysqlCtn 120
    log_info "Waiting MySQL ready..."; Start-Sleep 20

    # Extract & execute init SQL from nacos image
    pull_image $Image
    log_info "Initializing Nacos schema..."
    docker create --name nacos-init-tmp $Image 2>$null | Out-Null
    docker cp nacos-init-tmp:/home/nacos/conf/mysql-schema.sql "$env:TEMP\nacos-schema.sql" 2>$null
    docker rm nacos-init-tmp 2>$null | Out-Null
    Get-Content "$env:TEMP\nacos-schema.sql" | docker exec -i $mysqlCtn mysql -uroot "-p$MysqlPass" nacos_config
    log_info "Schema initialized"
} else {
    pull_image $Image
}

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
      -e MYSQL_SERVICE_HOST=$mysqlCtn -e MYSQL_SERVICE_PORT=3306 `
      -e MYSQL_SERVICE_DB_NAME=nacos_config `
      -e MYSQL_SERVICE_USER=root -e MYSQL_SERVICE_PASSWORD=$MysqlPass `
      -v "${data}\logs:/home/nacos/logs" -v "${data}\data:/home/nacos/data" `
      $Image

    wait_for_container $ctn 60
    log_info "$ctn  ->  http://localhost:${p}/nacos"
}

$urls = ((0..2).ForEach({ "http://localhost:$($BasePort + $_)/nacos" }) -join ', ')
done_banner "Nacos Cluster | $urls | nacos/nacos"
