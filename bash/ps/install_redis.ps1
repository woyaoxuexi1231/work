# Redis 7.2 | Port: 6379 | Pass: 123456
. "$PSScriptRoot/lib/common.ps1"

$Container = "redis"; $Image = "redis:$($env:REDIS_VERSION -replace '^$','7.2')"
$Port      = if ($env:REDIS_PORT) { $env:REDIS_PORT } else { "6379" }
$Pass      = if ($env:REDIS_PASSWORD) { $env:REDIS_PASSWORD } else { "123456" }
$Data      = "${DataRoot}\redis-data"

check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container
New-Item -ItemType Directory -Force -Path "$Data\data" | Out-Null

pull_image $Image
docker run -d --name $Container --restart always -p ${Port}:6379 -e TZ=Asia/Shanghai `
  -v "${Data}\data:/data" `
  $Image redis-server --requirepass $Pass --appendonly yes
wait_for_container $Container 30

for ($i = 1; $i -le 15; $i++) {
    $out = docker exec $Container redis-cli -a $Pass ping 2>$null; if ($out -match "PONG") { break }
    Start-Sleep 1
}

done_banner "Redis | Port: $Port | Pass: $Pass | Data: $Data"
