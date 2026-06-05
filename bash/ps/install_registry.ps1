# Docker Registry 2.8 + CORS | Port: 5000
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

# ==== Registry config (CORS for UI) ====
@'
version: 0.1
log:
  fields: {service: registry}
storage:
  delete: {enabled: true}
  filesystem: {rootdirectory: /var/lib/registry}
http:
  addr: :5000
  headers:
    Access-Control-Allow-Origin: ['*']
    Access-Control-Allow-Methods: ['HEAD','GET','OPTIONS','DELETE']
    Access-Control-Allow-Headers: ['Authorization','Accept']
    Access-Control-Expose-Headers: ['Docker-Content-Digest']
'@ | Out-File -FilePath "$Data\config.yml" -Encoding ASCII

# ==== Pull & Start ====
pull_image $Image
docker run -d --name $Container --restart unless-stopped -p ${Port}:5000 `
  -e TZ=Asia/Shanghai `
  -v "${Data}:/var/lib/registry" `
  -v "${Data}\config.yml:/etc/docker/registry/config.yml" `
  $Image
wait_for_container $Container 15

done_banner "Docker Registry | localhost:${Port}"
