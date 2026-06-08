# Eureka 2-Node Cluster | AP (自我保护模式) 测试环境
# eureka1:8761  eureka2:8762
. "$PSScriptRoot/lib/common.ps1"

$Prefix   = "eureka"
$Image    = "springcloud/eureka:$($env:EUREKA_VERSION -replace '^$','2021.0.9')"
$Port1    = if ($env:EUREKA_PORT1) { [int]$env:EUREKA_PORT1 } else { 8761 }
$Port2    = if ($env:EUREKA_PORT2) { [int]$env:EUREKA_PORT2 } else { 8762 }
$Data     = "${DataRoot}\eureka-data"

check_docker

# ---- 清理旧容器 ----
cleanup_container "${Prefix}1"
cleanup_container "${Prefix}2"

# ---- 创建目录 ----
New-Item -ItemType Directory -Force -Path "$Data\eureka1\logs","$Data\eureka2\logs" | Out-Null

# ---- 生成 docker-compose.yml ----
$ComposePath = "$Data\docker-compose.yml"
$ComposeContent = @"
version: '3.8'
services:
  ${Prefix}1:
    image: ${Image}
    container_name: ${Prefix}1
    restart: unless-stopped
    ports:
      - "${Port1}:8761"
    environment:
      - eureka.client.serviceUrl.defaultZone=http://${Prefix}2:8761/eureka/
      - eureka.client.registerWithEureka=true
      - eureka.client.fetchRegistry=true
      - eureka.server.enableSelfPreservation=true
      - eureka.server.renewalPercentThreshold=0.85
      - TZ=Asia/Shanghai
    volumes:
      - ${Data}\eureka1\logs:/var/log

  ${Prefix}2:
    image: ${Image}
    container_name: ${Prefix}2
    restart: unless-stopped
    ports:
      - "${Port2}:8761"
    environment:
      - eureka.client.serviceUrl.defaultZone=http://${Prefix}1:8761/eureka/
      - eureka.client.registerWithEureka=true
      - eureka.client.fetchRegistry=true
      - eureka.server.enableSelfPreservation=true
      - eureka.server.renewalPercentThreshold=0.85
      - TZ=Asia/Shanghai
    volumes:
      - ${Data}\eureka2\logs:/var/log
"@

$ComposeContent | Out-File -FilePath $ComposePath -Encoding UTF8

pull_image $Image
docker-compose -f $ComposePath up -d

# ---- 等待两个节点启动 ----
for ($n = 1; $n -le 2; $n++) {
    $name = "${Prefix}${n}"
    $port = if ($n -eq 1) { $Port1 } else { $Port2 }
    wait_for_container $name 90
    Write-Host "  Waiting for ${name} (port ${port})..." -ForegroundColor Yellow
    for ($j = 1; $j -le 45; $j++) {
        $resp = try { Invoke-WebRequest -Uri "http://localhost:${port}/" -UseBasicParsing -TimeoutSec 3 } catch { $null }
        if ($resp -and $resp.StatusCode -eq 200) { break }
        Start-Sleep 2
    }
}

done_banner "Eureka Cluster (2 nodes) | AP Self-Preservation Testable"
Write-Host "  Dashboard 1: http://localhost:${Port1}/" -ForegroundColor Cyan
Write-Host "  Dashboard 2: http://localhost:${Port2}/" -ForegroundColor Cyan
Write-Host ""
Write-Host "  AP Self-Preservation Test:" -ForegroundColor Green
Write-Host "    - 正常: 客户端停止心跳 -> 30s 后被剔除" -ForegroundColor Gray
Write-Host "    - 自我保护: 大量客户端停止心跳 -> 触发阈值，保留所有实例 (不剔除)" -ForegroundColor Gray
Write-Host "    - 模拟: docker stop eureka2 -> 观察 eureka1 Dashboard 是否进入自我保护" -ForegroundColor Gray
