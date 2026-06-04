# =============================================================================
# lib/common.ps1 — PowerShell 公共函数库
# 所有组件脚本 dot-source 引用: . "$PSScriptRoot/../lib/common.ps1"
# =============================================================================

$Script:DataRoot = "C:\Users\15434\Desktop\docker-data"

function log_info($msg)  { Write-Host "[$(Get-Date -Format 'HH:mm:ss')] [INFO] $msg" }
function log_warn($msg)  { Write-Host "[$(Get-Date -Format 'HH:mm:ss')] [WARN] $msg" -ForegroundColor Yellow }
function log_error($msg) { Write-Host "[$(Get-Date -Format 'HH:mm:ss')] [ERROR] $msg" -ForegroundColor Red }

function check_docker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { log_error "Docker 未安装"; exit 1 }
    docker ps 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { log_error "Docker 未运行，请启动 Docker Desktop"; exit 1 }
}

function check_container_exists($name) {
    $found = docker ps -a --format '{{.Names}}' 2>$null | Select-String -Pattern "^$name$" -SimpleMatch
    if ($found) {
        if (docker ps --format '{{.Names}}' 2>$null | Select-String -Pattern "^$name$" -SimpleMatch) {
            log_warn "容器 $name 已存在且运行中，跳过"
        } else {
            log_warn "容器 $name 已存在（已停止），如需重建请先 docker rm -f $name"
        }
        return $true
    }
    return $false
}

function cleanup_container($name) {
    docker ps -a --format '{{.Names}}' 2>$null | Select-String -Pattern "^$name$" -SimpleMatch | Out-Null
    if ($?) { log_info "清理旧容器: $name"; docker rm -f $name 2>$null | Out-Null }
}

function pull_image($img) {
    $found = docker image inspect $img 2>$null
    if ($LASTEXITCODE -eq 0) { log_info "镜像 $img 已存在" }
    else {
        log_info "拉取镜像: $img"
        docker pull $img
        if ($LASTEXITCODE -ne 0) { log_error "拉取失败: $img"; exit 1 }
    }
}

function wait_for_container($name, $max=60) {
    for ($i = 0; $i -lt $max; $i++) {
        docker ps --format '{{.Names}}' 2>$null | Select-String -Pattern "^$name$" -SimpleMatch | Out-Null
        if ($?) { return }
        Start-Sleep 1
    }
    log_error "容器 $name 在 ${max}s 内未启动"; exit 1
}

function done_banner($component) {
    log_info "=== $component 安装完成 ==="
}
