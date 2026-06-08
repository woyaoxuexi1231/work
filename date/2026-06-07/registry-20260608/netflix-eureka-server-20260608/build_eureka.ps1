# ============================================
# Eureka Docker Image Build Script
# Usage: .\build_eureka.ps1 [image_tag]
# e.g.:  .\build_eureka.ps1
#        .\build_eureka.ps1 eureka-server:v1.0
# ============================================

$ErrorActionPreference = "Stop"

$ImageTag    = if ($args.Count -gt 0) { $args[0] } else { "eureka-server:latest" }
$ProjectRoot = $PSScriptRoot

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Eureka Server Image Build" -ForegroundColor Cyan
Write-Host "  Image Tag: $ImageTag" -ForegroundColor Cyan
Write-Host "  Project  : $ProjectRoot" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# --------------------------------------------------
# Resolve work directory (Maven requires non-UNC path)
# --------------------------------------------------
$MappedDrive = $null
$WorkDir     = $ProjectRoot

if ($ProjectRoot -match '^\\\\') {
    # Find a free drive letter
    $freeLetter = $null
    foreach ($c in 'Z','Y','X','W','V','U','T','S','R','Q','P','O','N','M') {
        if (-not (Test-Path "${c}:")) { $freeLetter = $c; break }
    }
    if (-not $freeLetter) {
        throw "No free drive letter available, please map a network drive manually and rerun from that drive."
    }
    $MappedDrive = "${freeLetter}:"
    Write-Host "UNC path detected, mapping ${ProjectRoot} -> ${MappedDrive}" -ForegroundColor DarkYellow
    net use $MappedDrive $ProjectRoot 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to map network drive ${MappedDrive}"
    }
    $WorkDir = $MappedDrive
}

try {
    # 1. Maven package
    Write-Host "`n[1/2] Maven packaging..." -ForegroundColor Yellow
    Push-Location $WorkDir
    try {
        mvn clean package -DskipTests -q
        if ($LASTEXITCODE -ne 0) {
            throw "Maven package failed!"
        }
        Write-Host "  -> Maven package done" -ForegroundColor Green
    } finally {
        Pop-Location
    }

    # 2. Docker build
    Write-Host "`n[2/2] Docker building..." -ForegroundColor Yellow
    docker build -t $ImageTag -f "$WorkDir\Dockerfile" $WorkDir
    if ($LASTEXITCODE -ne 0) {
        throw "Docker build failed!"
    }
    Write-Host "  -> Docker build done: $ImageTag" -ForegroundColor Green

    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "  Build Success!" -ForegroundColor Green
    docker images $ImageTag
    Write-Host "========================================" -ForegroundColor Cyan
} finally {
    # Cleanup mapped drive
    if ($MappedDrive) {
        Write-Host "`nCleaning up mapped drive ${MappedDrive}..." -ForegroundColor DarkYellow
        net use $MappedDrive /delete 2>&1 | Out-Null
    }
}
