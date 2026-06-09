# Nacos 3-Node Cluster | AP/CP Test
# nacos1:8848  nacos2:8849  nacos3:8850
. "$PSScriptRoot/lib/common.ps1"

$Prefix    = "nacos"
$Image     = "nacos/nacos-server:$($env:NACOS_VERSION -replace '^$','v2.3.0')"
$BasePort  = if ($env:NACOS_BASE_PORT) { [int]$env:NACOS_BASE_PORT } else { 8848 }
$GrpcBase  = if ($env:NACOS_GRPC_BASE) { [int]$env:NACOS_GRPC_BASE } else { 9848 }
$Network   = "${Prefix}-net"
$NodeCount = 3

check_docker

# ---- cleanup old containers ----
for ($i = 1; $i -le $NodeCount; $i++) {
    $name = "${Prefix}${i}"
    cleanup_container $name
}

# ---- cleanup and create network ----
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
docker network rm $Network 2>$null | Out-Null
$ErrorActionPreference = $prevEAP
docker network create $Network | Out-Null

# ---- build NACOS_SERVERS (space-separated for the startup script) ----
# Nacos startup.sh uses: for server in ${NACOS_SERVERS}
# It splits by SPACE, not comma. So use spaces.
$Servers = @()
for ($i = 1; $i -le $NodeCount; $i++) {
    $Servers += "${Prefix}${i}:8848"
}
$NacosServers = $Servers -join ","   # Nacos app reads comma-separated
$NacosServersSpace = $Servers -join " "  # startup.sh for-loop reads space-separated

pull_image $Image

# ---- start 3 nodes with docker run ----
for ($i = 1; $i -le $NodeCount; $i++) {
    $name  = "${Prefix}${i}"
    $port  = $BasePort + $i - 1
    $grpc  = $GrpcBase + $i - 1

    Write-Host "Starting $name (host -> :$port)..." -ForegroundColor Green
    docker run -d `
        --name $name `
        --hostname $name `
        --network $Network `
        -p "${port}:8848" `
        -p "${grpc}:9848" `
        -e MODE=cluster `
        -e NACOS_SERVERS=$NacosServers `
        -e PREFER_HOST_MODE=hostname `
        -e NACOS_AUTH_IDENTITY_KEY=serverIdentity `
        -e NACOS_AUTH_IDENTITY_VALUE=security `
        -e "NACOS_AUTH_TOKEN=SecretKey012345678901234567890123456789012345678901234567890123456789" `
        -e JVM_XMS=256m `
        -e JVM_XMX=512m `
        -e JVM_XMN=128m `
        -e JVM_MS=64m `
        -e JVM_MMS=128m `
        -e TZ=Asia/Shanghai `
        --restart unless-stopped `
        $Image | Out-Null
}

# ---- wait for all nodes ----
for ($i = 1; $i -le $NodeCount; $i++) {
    $name = "${Prefix}${i}"
    $port = $BasePort + $i - 1
    Write-Host "  Waiting for ${name} (port ${port})..." -ForegroundColor Yellow
    for ($j = 1; $j -le 60; $j++) {
        $resp = try { Invoke-WebRequest -Uri "http://localhost:${port}/nacos/v1/console/health/readiness" -UseBasicParsing -TimeoutSec 3 } catch { $null }
        if ($resp -and $resp.StatusCode -eq 200) { break }
        Start-Sleep 2
    }
    if ($resp -and $resp.StatusCode -eq 200) {
        Write-Host "  -> ${name} READY" -ForegroundColor Green
    } else {
        Write-Host "  -> ${name} TIMEOUT, check: docker logs ${name}" -ForegroundColor Red
    }
}

done_banner "Nacos Cluster (3 nodes) | AP/CP Testable"
for ($i = 1; $i -le $NodeCount; $i++) {
    $port = $BasePort + $i - 1
    Write-Host "  Console $i : http://localhost:${port}/nacos" -ForegroundColor Cyan
}
Write-Host ""
Write-Host "  AP/CP Test:" -ForegroundColor Green
Write-Host "    - AP mode: ephemeral=true  -> Distro, allows temporary inconsistency" -ForegroundColor Gray
Write-Host "    - CP mode: ephemeral=false -> Raft, strong consistency, needs majority alive" -ForegroundColor Gray
Write-Host "    - Failover: docker stop nacos3 -> check if CP instances still writable 2/3 majority" -ForegroundColor Gray
