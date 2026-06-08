# ============================================
# Eureka 3-Node Cluster | 入口脚本
# 实际逻辑在 netflix-eureka-server/ 子目录
# ============================================
#
# 首次使用请先构建镜像:
#   cd netflix-eureka-server
#   .\build_eureka.ps1
#
# 然后运行本脚本启动集群
# ============================================

$ScriptDir = "$PSScriptRoot\netflix-eureka-server"

if (-not (Test-Path "$ScriptDir\install_eureka.ps1")) {
    Write-Host "ERROR: netflix-eureka-server/install_eureka.ps1 not found!" -ForegroundColor Red
    exit 1
}

# 先检查镜像是否已构建
$Image = if ($env:EUREKA_IMAGE) { $env:EUREKA_IMAGE } else { "eureka-server:latest" }
$imageExists = docker images --format "{{.Repository}}:{{.Tag}}" 2>$null | Select-String -Pattern ([regex]::Escape($Image)) -Quiet

if (-not $imageExists) {
    Write-Host "Image '$Image' not found, building first..." -ForegroundColor Yellow
    Push-Location $ScriptDir
    try {
        & ".\build_eureka.ps1"
    } finally {
        Pop-Location
    }
}

# 启动集群
& "$ScriptDir\install_eureka.ps1"
