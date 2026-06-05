# Docker Registry 2.8 | Port: 5000
. "$PSScriptRoot/lib/common.ps1"

# ==== Config ====
$Container = "registry"
$Image     = "registry:2.8"
$Port      = if ($env:REGISTRY_PORT) { $env:REGISTRY_PORT } else { "5000" }
$Data      = "${DataRoot}\registry-data"

# ==== Pre-check ====
check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container
New-Item -ItemType Directory -Force -Path $Data | Out-Null

# ==== Pull & Start ====
pull_image $Image
docker run -d --name $Container --restart unless-stopped -p ${Port}:5000 `
  -e TZ=Asia/Shanghai `
  -v "${Data}:/var/lib/registry" `
  $Image
wait_for_container $Container 15

done_banner "Docker Registry | localhost:${Port}"
