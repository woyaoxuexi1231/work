# Eureka Cluster | 3 nodes | jdk1.8
. "$PSScriptRoot/lib/common.ps1"

check_docker

$Network   = "eureka-net"
$ImageName = "eureka-server:1.0"
$BasePort  = if ($env:EUREKA_PORT) { [int]$env:EUREKA_PORT } else { 8761 }

# ---- Build image (if not exists) ----
if (-not (docker images -q $ImageName 2>$null)) {
    log_info "Building $ImageName ..."
    Set-Location $PSScriptRoot
    mvn clean package -DskipTests -q
    if ($LASTEXITCODE -ne 0) { log_error "Maven build failed"; exit 1 }
    docker build -t $ImageName .
    log_info "Image built: $ImageName"
}

# ---- Docker Network ----
if (-not (docker network ls --format '{{.Name}}' | Select-String "^$Network$")) {
    docker network create $Network; log_info "Network '$Network' created"
}

$EurekaZone = "http://eureka1:8761/eureka/,http://eureka2:8761/eureka/,http://eureka3:8761/eureka/"

# ---- 3 Eureka Nodes ----
for ($i = 0; $i -lt 3; $i++) {
    $node   = $i + 1; $ctn = "eureka$node"
    $offset = $i * 100
    $p      = $BasePort + $offset

    if (check_container_exists $ctn) { continue }
    cleanup_container $ctn

    docker run -d --name $ctn --network $Network --restart unless-stopped `
      -p ${p}:8761 `
      -e SERVER_PORT=8761 `
      -e EUREKA_HOST=$ctn `
      -e EUREKA_ZONE=$EurekaZone `
      $ImageName

    wait_for_container $ctn 120
    log_info "$ctn  ->  http://localhost:${p}"
}

$urls = ((0..2).ForEach({ "http://localhost:$($BasePort + $_ * 100)" }) -join ', ')
done_banner "Eureka Cluster | $urls"
