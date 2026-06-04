# Consul 1.15.4 | Port: 8500, 8600
. "$PSScriptRoot/lib/common.ps1"

$Container = "consul"; $Image = "consul:$($env:CONSUL_VERSION -replace '^$','1.15.4')"
$Http = if ($env:CONSUL_HTTP_PORT) { $env:CONSUL_HTTP_PORT } else { "8500" }
$Dns  = if ($env:CONSUL_DNS_PORT) { $env:CONSUL_DNS_PORT } else { "8600" }
$Data = "${DataRoot}\consul-data"

check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container
New-Item -ItemType Directory -Force -Path "$Data\data" | Out-Null

pull_image $Image
docker run -d --name $Container --restart always -e TZ=Asia/Shanghai `
  -p ${Http}:8500 -p ${Dns}:8600/tcp -p ${Dns}:8600/udp `
  -v "${Data}\data:/consul/data" `
  $Image agent -dev -client=0.0.0.0 -bind=0.0.0.0 -ui
wait_for_container $Container 15

done_banner "Consul | http://localhost:${Http}/ui | Data: $Data"
