# ============================================
# Eureka 3-Node Cluster | Docker Compose
# eureka1:8761  eureka2:8762  eureka3:8763
# Build image first: .\build_eureka.ps1
# ============================================

$ErrorActionPreference = "Stop"

$Prefix    = "eureka"
$Image     = if ($env:EUREKA_IMAGE) { $env:EUREKA_IMAGE } else { "eureka-server:latest" }
$Network   = "${Prefix}-net"

# External port mapping
$Port1     = if ($env:EUREKA_PORT1) { [int]$env:EUREKA_PORT1 } else { 8761 }
$Port2     = if ($env:EUREKA_PORT2) { [int]$env:EUREKA_PORT2 } else { 8762 }
$Port3     = if ($env:EUREKA_PORT3) { [int]$env:EUREKA_PORT3 } else { 8763 }

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Eureka 3-Node Cluster" -ForegroundColor Cyan
Write-Host "  Image: $Image" -ForegroundColor Cyan
Write-Host "  Ports: ${Port1} / ${Port2} / ${Port3}" -ForegroundColor Cyan
Write-Host "  Mode: Self-Preservation ON (AP)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# ---- Check Docker ----
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: Docker not found. Please install Docker Desktop first." -ForegroundColor Red
    exit 1
}

# ---- Check image exists ----
$imageExists = docker images --format "{{.Repository}}:{{.Tag}}" | Select-String -Pattern ([regex]::Escape($Image)) -Quiet
if (-not $imageExists) {
    Write-Host "WARNING: Image '$Image' not found!" -ForegroundColor Yellow
    Write-Host "Run first: .\build_eureka.ps1" -ForegroundColor Yellow
    exit 1
}

# ---- Cleanup old containers & network ----
Write-Host "`nCleaning up old containers and network..." -ForegroundColor Yellow
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
foreach ($n in 1..3) {
    $name = "${Prefix}${n}"
    docker rm -f $name 2>$null | Out-Null
}
docker network rm $Network 2>$null | Out-Null
$ErrorActionPreference = $prevEAP

# ---- Create network ----
Write-Host "Creating Docker network: $Network" -ForegroundColor Yellow
docker network create $Network

# ---- Start 3 nodes ----
$nodes = @(
    @{
        Name        = "${Prefix}1"
        Port        = $Port1
        Hostname    = "${Prefix}1"
        DefaultZone = "http://${Prefix}2:8761/eureka/,http://${Prefix}3:8761/eureka/"
    },
    @{
        Name        = "${Prefix}2"
        Port        = $Port2
        Hostname    = "${Prefix}2"
        DefaultZone = "http://${Prefix}1:8761/eureka/,http://${Prefix}3:8761/eureka/"
    },
    @{
        Name        = "${Prefix}3"
        Port        = $Port3
        Hostname    = "${Prefix}3"
        DefaultZone = "http://${Prefix}1:8761/eureka/,http://${Prefix}2:8761/eureka/"
    }
)

foreach ($node in $nodes) {
    Write-Host "`nStarting node: $($node.Name) (host -> :$($node.Port))" -ForegroundColor Green
    docker run -d `
        --name $node.Name `
        --hostname $node.Hostname `
        --network $Network `
        -p "$($node.Port):8761" `
        -e SPRING_PROFILES_ACTIVE=docker `
        -e SERVER_PORT=8761 `
        -e EUREKA_HOSTNAME=$($node.Hostname) `
        -e EUREKA_DEFAULT_ZONE=$($node.DefaultZone) `
        -e JAVA_OPTS="-Xms256m -Xmx512m" `
        --restart unless-stopped `
        $Image
}

# ---- Wait for startup ----
Write-Host "`nWaiting for nodes to start..." -ForegroundColor Yellow
$maxWait = 90
foreach ($n in 1..3) {
    $name = "${Prefix}${n}"
    $port = if ($n -eq 1) { $Port1 } elseif ($n -eq 2) { $Port2 } else { $Port3 }
    Write-Host "  Waiting for ${name} (http://localhost:${port}/) ..." -ForegroundColor DarkYellow
    $ready = $false
    for ($i = 1; $i -le $maxWait; $i++) {
        try {
            $resp = Invoke-WebRequest -Uri "http://localhost:${port}/" -UseBasicParsing -TimeoutSec 2
            if ($resp.StatusCode -eq 200) {
                $ready = $true
                break
            }
        } catch {
            Start-Sleep 2
        }
    }
    if ($ready) {
        Write-Host "  -> ${name} READY" -ForegroundColor Green
    } else {
        Write-Host "  -> ${name} TIMEOUT, check: docker logs ${name}" -ForegroundColor Red
    }
}

# ---- Done ----
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  Eureka 3-Node Cluster Started" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Dashboards:" -ForegroundColor White
Write-Host "    Node 1: http://localhost:${Port1}/" -ForegroundColor Cyan
Write-Host "    Node 2: http://localhost:${Port2}/" -ForegroundColor Cyan
Write-Host "    Node 3: http://localhost:${Port3}/" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Commands:" -ForegroundColor White
Write-Host "    View logs   : docker logs -f ${Prefix}1" -ForegroundColor Gray
Write-Host "    Stop cluster: docker stop ${Prefix}1 ${Prefix}2 ${Prefix}3" -ForegroundColor Gray
Write-Host "    Delete all  : docker rm -f ${Prefix}1 ${Prefix}2 ${Prefix}3 ; docker network rm ${Network}" -ForegroundColor Gray
Write-Host ""
Write-Host "  AP Self-Preservation Test:" -ForegroundColor Green
Write-Host "    1. Open Dashboard and observe" -ForegroundColor Gray
Write-Host "    2. Stop one node: docker stop ${Prefix}3" -ForegroundColor Gray
Write-Host "    3. Watch remaining nodes enter self-preservation mode" -ForegroundColor Gray
