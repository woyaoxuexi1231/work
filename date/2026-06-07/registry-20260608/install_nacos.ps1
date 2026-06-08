# Nacos 2.3.0 | Port: 8848 (HTTP) / 9848 (gRPC) | User: nacos/nacos
. "$PSScriptRoot/lib/common.ps1"

$Container = "nacos"; $Image = "nacos/nacos-server:$($env:NACOS_VERSION -replace '^$','v2.3.0')"
$Port      = if ($env:NACOS_PORT) { $env:NACOS_PORT } else { "8848" }
$GrpcPort  = if ($env:NACOS_GRPC_PORT) { $env:NACOS_GRPC_PORT } else { "9848" }
$Data      = "${DataRoot}\nacos-data"

check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container
New-Item -ItemType Directory -Force -Path "$Data\logs","$Data\conf","$Data\data" | Out-Null

pull_image $Image
docker run -d --name $Container --restart unless-stopped `
  -p ${Port}:8848 -p ${GrpcPort}:9848 `
  -e "MODE=standalone" `
  -e "NACOS_AUTH_IDENTITY_KEY=serverIdentity" `
  -e "NACOS_AUTH_IDENTITY_VALUE=security" `
  -e "NACOS_AUTH_TOKEN=SecretKey012345678901234567890123456789012345678901234567890123456789" `
  -e "PREFER_HOST_MODE=hostname" `
  -e TZ=Asia/Shanghai `
  -v "${Data}\logs:/home/nacos/logs" `
  -v "${Data}\data:/home/nacos/data" `
  $Image
wait_for_container $Container 60

# 等待 Nacos 启动完成 (HTTP 健康检查)
for ($i = 1; $i -le 30; $i++) {
    $resp = try { Invoke-WebRequest -Uri "http://localhost:${Port}/nacos/v1/console/health/readiness" -UseBasicParsing -TimeoutSec 3 } catch { $null }
    if ($resp -and $resp.StatusCode -eq 200) { break }
    Start-Sleep 2
}

done_banner "Nacos | HTTP: $Port | gRPC: $GrpcPort | User: nacos/nacos | Data: $Data"
Write-Host "  Console: http://localhost:${Port}/nacos" -ForegroundColor Cyan
