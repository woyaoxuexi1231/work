# Docker Registry UI | Port: 8085 | joxit/docker-registry-ui
. "$PSScriptRoot/lib/common.ps1"

# ==== Config ====
$Container = "registry-ui"
$Image     = "joxit/docker-registry-ui:latest"
$Port      = if ($env:REGISTRY_UI_PORT) { $env:REGISTRY_UI_PORT } else { "8085" }
$RegUrl    = if ($env:REGISTRY_URL) { $env:REGISTRY_URL } else { "http://localhost:5000" }

# ==== Pre-check ====
check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container

# ==== Pull & Start ====
pull_image $Image
docker run -d --name $Container --restart always -p ${Port}:80 `
  -e "REGISTRY_URL=${RegUrl}" `
  -e DELETE_IMAGES=true `
  -e TZ=Asia/Shanghai `
  $Image
wait_for_container $Container 15

done_banner "Registry UI | http://localhost:${Port}"
