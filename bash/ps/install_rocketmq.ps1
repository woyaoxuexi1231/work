# RocketMQ 5.3.2 | Ports: 9876, 10911, 8888(Dashboard)
. "$PSScriptRoot/lib/common.ps1"

$Image  = "apache/rocketmq:$($env:ROCKETMQ_VERSION -replace '^$','5.3.2')"
$NS     = "rmqnamesrv"; $BR = "rmqbroker"; $DB = "rmqdashboard"
$NsPort = if ($env:NAMESRV_PORT) { $env:NAMESRV_PORT } else { "9876" }
$BrPort = if ($env:BROKER_PORT_2) { $env:BROKER_PORT_2 } else { "10911" }
$DbPort = if ($env:ROCKETMQ_DASHBOARD_PORT) { $env:ROCKETMQ_DASHBOARD_PORT } else { "8888" }
$Net    = "rmq"
$Data   = "${DataRoot}\rocketmq-data"

check_docker
docker network ls --format '{{.Name}}' 2>$null | Select-String $Net | Out-Null
if (-not $?) { docker network create $Net }
New-Item -ItemType Directory -Force -Path "$Data\namesrv","$Data\broker","$Data\store" | Out-Null

pull_image $Image

# NameServer
if (-not (check_container_exists $NS)) {
    cleanup_container $NS
    docker run -d --name $NS --restart unless-stopped --network $Net -p ${NsPort}:9876 -e TZ=Asia/Shanghai `
      -v "${Data}\namesrv:/home/rocketmq/logs/rocketmqlogs" `
      $Image sh mqnamesrv
    wait_for_container $NS 15
}

# Broker + Proxy
if (-not (check_container_exists $BR)) {
    cleanup_container $BR
    docker run -d --name $BR --restart unless-stopped --network $Net `
      -p ${BrPort}:10911 -p 10909:10909 -p 8080:8080 -p 8081:8081 `
      -e "NAMESRV_ADDR=${NS}:9876" -e "JAVA_OPT=-Xms512m -Xmx1g" -e TZ=Asia/Shanghai `
      -v "${Data}\broker:/home/rocketmq/logs/rocketmqlogs" `
      -v "${Data}\store:/home/rocketmq/store" `
      $Image sh mqbroker --enable-proxy -n "${NS}:9876" autoCreateTopicEnable=true 2>$null
    wait_for_container $BR 30
}

# Dashboard
if (-not (check_container_exists $DB)) {
    cleanup_container $DB
    $dbImg = "apacherocketmq/rocketmq-dashboard:$($env:ROCKETMQ_DASHBOARD_VERSION -replace '^$','latest')"
    pull_image $dbImg
    docker run -d --name $DB --restart unless-stopped --network $Net `
      -e "JAVA_OPTS=-Drocketmq.namesrv.addr=${NS}:9876" -p ${DbPort}:8082 $dbImg 2>$null
}

done_banner "RocketMQ | NS: $NsPort | Broker: $BrPort | Dashboard: http://localhost:${DbPort}"
