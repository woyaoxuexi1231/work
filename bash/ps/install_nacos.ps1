# Nacos 2.3.2 | Port: 8848, 9848, 9849 | nacos/nacos
. "$PSScriptRoot/lib/common.ps1"

$Container = "nacos"; $Image = "nacos/nacos-server:$($env:NACOS_VERSION -replace '^$','v2.3.2')"
$Port  = if ($env:NACOS_PORT) { $env:NACOS_PORT } else { "8848" }
$G1    = [int]$Port + 1000; $G2 = [int]$Port + 1001
$Data  = "${DataRoot}\nacos-data"

check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container
New-Item -ItemType Directory -Force -Path "$Data\logs","$Data\data" | Out-Null

pull_image $Image
docker run -d --name $Container --restart always `
  -p ${Port}:8848 -p ${G1}:9848 -p ${G2}:9849 `
  -e MODE=standalone -e NACOS_AUTH_ENABLE=false -e TZ=Asia/Shanghai `
  -v "${Data}\logs:/home/nacos/logs" -v "${Data}\data:/home/nacos/data" `
  $Image
wait_for_container $Container 60

done_banner "Nacos | http://localhost:${Port}/nacos | nacos/nacos | Data: $Data"
