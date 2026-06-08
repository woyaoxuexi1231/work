# Eureka Server | Port: 8761 | Dashboard: http://localhost:8761
. "$PSScriptRoot/lib/common.ps1"

$Container = "eureka"; $Image = "springcloud/eureka:$($env:EUREKA_VERSION -replace '^$','2021.0.9')"
$Port      = if ($env:EUREKA_PORT) { $env:EUREKA_PORT } else { "8761" }
$Data      = "${DataRoot}\eureka-data"

check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container
New-Item -ItemType Directory -Force -Path "$Data\logs" | Out-Null

pull_image $Image
docker run -d --name $Container --restart unless-stopped -p ${Port}:8761 `
  -e "EUREKA_SERVER=http://localhost:8761/eureka/" `
  -e "eureka.client.registerWithEureka=false" `
  -e "eureka.client.fetchRegistry=false" `
  -e "eureka.server.enableSelfPreservation=true" `
  -e TZ=Asia/Shanghai `
  -v "${Data}\logs:/var/log" `
  $Image
wait_for_container $Container 90

# 等待 Eureka Server 启动完成 (Dashboard 可访问)
for ($i = 1; $i -le 45; $i++) {
    $resp = try { Invoke-WebRequest -Uri "http://localhost:${Port}/" -UseBasicParsing -TimeoutSec 3 } catch { $null }
    if ($resp -and $resp.StatusCode -eq 200) { break }
    Start-Sleep 2
}

done_banner "Eureka | Port: $Port | Data: $Data"
Write-Host "  Dashboard: http://localhost:${Port}/" -ForegroundColor Cyan
