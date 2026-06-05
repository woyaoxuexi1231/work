# MinIO | Port: 9000(API), 9001(Console) | minioadmin/minioadmin
. "$PSScriptRoot/lib/common.ps1"

$Container = "minio"; $Image = "minio/minio:$($env:MINIO_VERSION -replace '^$','latest')"
$ApiPort   = if ($env:MINIO_API_PORT) { $env:MINIO_API_PORT } else { "9000" }
$ConPort   = if ($env:MINIO_CONSOLE_PORT) { $env:MINIO_CONSOLE_PORT } else { "9001" }
$User      = if ($env:MINIO_ROOT_USER) { $env:MINIO_ROOT_USER } else { "minioadmin" }
$Pass      = if ($env:MINIO_ROOT_PASSWORD) { $env:MINIO_ROOT_PASSWORD } else { "minioadmin" }
$Data      = "${DataRoot}\minio-data"

check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container
New-Item -ItemType Directory -Force -Path "$Data\data" | Out-Null

pull_image $Image
docker run -d --name $Container --restart unless-stopped -p ${ApiPort}:9000 -p ${ConPort}:9001 `
  -e "MINIO_ROOT_USER=$User" -e "MINIO_ROOT_PASSWORD=$Pass" -e TZ=Asia/Shanghai `
  -v "${Data}\data:/data" `
  $Image server /data --console-address ":9001"
wait_for_container $Container 20

done_banner "MinIO | API: $ApiPort | Console: http://localhost:${ConPort} | $User/$Pass"
