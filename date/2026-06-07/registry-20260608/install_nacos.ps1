# Nacos 3-Node Cluster | AP/CP 模式测试环境
# nacos1:8848  nacos2:8849  nacos3:8850
. "$PSScriptRoot/lib/common.ps1"

$Prefix    = "nacos"
$Image     = "nacos/nacos-server:$($env:NACOS_VERSION -replace '^$','v2.3.0')"
$BasePort  = if ($env:NACOS_BASE_PORT) { [int]$env:NACOS_BASE_PORT } else { 8848 }
$GrpcBase  = if ($env:NACOS_GRPC_BASE) { [int]$env:NACOS_GRPC_BASE } else { 9848 }
$Data      = "${DataRoot}\nacos-data"
$NodeCount = 3

check_docker

# ---- 清理旧容器 ----
for ($i = 1; $i -le $NodeCount; $i++) {
    $name = "${Prefix}${i}"
    cleanup_container $name
}

# ---- 创建目录 ----
for ($i = 1; $i -le $NodeCount; $i++) {
    New-Item -ItemType Directory -Force -Path "$Data\nacos${i}\logs","$Data\nacos${i}\data" | Out-Null
}

# ---- 构建集群成员列表 ----
$Members = @()
for ($i = 1; $i -le $NodeCount; $i++) {
    $Members += "${Prefix}${i}:$($BasePort + $i - 1)"
}
$ClusterMembers = $Members -join ","

# ---- 生成 docker-compose.yml ----
$ComposePath = "$Data\docker-compose.yml"
$ComposeContent = @"
version: '3.8'
services:
"@

for ($i = 1; $i -le $NodeCount; $i++) {
    $port = $BasePort + $i - 1
    $grpc = $GrpcBase + $i - 1
    $ComposeContent += @"

  ${Prefix}${i}:
    image: ${Image}
    container_name: ${Prefix}${i}
    restart: unless-stopped
    ports:
      - "${port}:8848"
      - "${grpc}:9848"
    environment:
      - MODE=cluster
      - NACOS_SERVERS=${ClusterMembers}
      - NACOS_AUTH_IDENTITY_KEY=serverIdentity
      - NACOS_AUTH_IDENTITY_VALUE=security
      - NACOS_AUTH_TOKEN=SecretKey012345678901234567890123456789012345678901234567890123456789
      - PREFER_HOST_MODE=hostname
      - TZ=Asia/Shanghai
    volumes:
      - ${Data}\nacos${i}\logs:/home/nacos/logs
      - ${Data}\nacos${i}\data:/home/nacos/data
"@
}

$ComposeContent | Out-File -FilePath $ComposePath -Encoding UTF8

pull_image $Image
docker-compose -f $ComposePath up -d

# ---- 等待所有节点启动 ----
for ($i = 1; $i -le $NodeCount; $i++) {
    $name = "${Prefix}${i}"
    $port = $BasePort + $i - 1
    wait_for_container $name 60
    Write-Host "  Waiting for ${name} (port ${port})..." -ForegroundColor Yellow
    for ($j = 1; $j -le 30; $j++) {
        $resp = try { Invoke-WebRequest -Uri "http://host.docker.internal:${port}/nacos/v1/console/health/readiness" -UseBasicParsing -TimeoutSec 3 } catch { $null }
        if ($resp -and $resp.StatusCode -eq 200) { break }
        Start-Sleep 2
    }
}

done_banner "Nacos Cluster (3 nodes) | AP/CP Testable"
for ($i = 1; $i -le $NodeCount; $i++) {
    $port = $BasePort + $i - 1
    Write-Host "  Console $i : http://host.docker.internal:${port}/nacos" -ForegroundColor Cyan
}
Write-Host ""
Write-Host "  AP/CP Test:" -ForegroundColor Green
Write-Host "    - AP (临时实例): ephemeral=true  -> Distro 协议，允许短暂不一致" -ForegroundColor Gray
Write-Host "    - CP (持久实例): ephemeral=false -> Raft 协议，强一致性，需多数节点存活" -ForegroundColor Gray
Write-Host "    - 模拟故障: docker stop nacos3 -> 观察 CP 实例是否仍可写入 (2/3 多数)" -ForegroundColor Gray
