# Common functions for Docker component installers
$DataRoot = "C:\Users\15434\Desktop\docker-data"

function log_info($msg)  { Write-Host "[$(Get-Date -Format 'HH:mm:ss')] [INFO] $msg" }
function log_warn($msg)  { Write-Host "[$(Get-Date -Format 'HH:mm:ss')] [WARN] $msg" -ForegroundColor Yellow }
function log_error($msg) { Write-Host "[$(Get-Date -Format 'HH:mm:ss')] [ERROR] $msg" -ForegroundColor Red }

function check_docker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { log_error "Docker not installed"; exit 1 }
    docker ps 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { log_error "Docker not running, start Docker Desktop first"; exit 1 }
}

function check_container_exists($name) {
    $found = docker ps -a --format '{{.Names}}' 2>$null | Select-String -Pattern "^$name$" -SimpleMatch
    if ($found) {
        if (docker ps --format '{{.Names}}' 2>$null | Select-String -Pattern "^$name$" -SimpleMatch) {
            log_warn "Container $name already running, skip"
        } else {
            log_warn "Container $name exists (stopped), run 'docker rm -f $name' to reinstall"
        }
        return $true
    }
    return $false
}

function cleanup_container($name) {
    docker ps -a --format '{{.Names}}' 2>$null | Select-String -Pattern "^$name$" -SimpleMatch | Out-Null
    if ($?) { log_info "Removing old container: $name"; docker rm -f $name 2>$null | Out-Null }
}

function pull_image($img) {
    docker image inspect $img 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { log_info "Image $img already exists" }
    else {
        log_info "Pulling image: $img"
        docker pull $img
        if ($LASTEXITCODE -ne 0) { log_error "Pull failed: $img"; exit 1 }
    }
}

function wait_for_container($name, $max=60) {
    for ($i = 0; $i -lt $max; $i++) {
        docker ps --format '{{.Names}}' 2>$null | Select-String -Pattern "^$name$" -SimpleMatch | Out-Null
        if ($?) { return }
        Start-Sleep 1
    }
    log_error "Container $name did not start in ${max}s"; exit 1
}

function done_banner($component) {
    log_info "=== $component ready ==="
}
