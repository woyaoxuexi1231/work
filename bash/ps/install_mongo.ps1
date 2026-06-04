# MongoDB 8.0 | Port: 27017 | admin/admin123
. "$PSScriptRoot/lib/common.ps1"

$Container = "mongo"; $Image = "mongo:$($env:MONGO_VERSION -replace '^$','8.0')"
$Port      = if ($env:MONGO_PORT) { $env:MONGO_PORT } else { "27017" }
$User      = if ($env:MONGO_ROOT_USER) { $env:MONGO_ROOT_USER } else { "admin" }
$Pass      = if ($env:MONGO_ROOT_PASSWORD) { $env:MONGO_ROOT_PASSWORD } else { "admin123" }
$Data      = "${DataRoot}\mongo-data"

check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container
New-Item -ItemType Directory -Force -Path "$Data\data","$Data\log" | Out-Null

pull_image $Image
docker run -d --name $Container --restart always -p ${Port}:27017 -e TZ=Asia/Shanghai `
  -e "MONGO_INITDB_ROOT_USERNAME=$User" -e "MONGO_INITDB_ROOT_PASSWORD=$Pass" `
  -v "${Data}\data:/data/db" -v "${Data}\log:/var/log/mongodb" `
  $Image
wait_for_container $Container 60

done_banner "MongoDB | Port: $Port | $User/$Pass | Data: $Data"
