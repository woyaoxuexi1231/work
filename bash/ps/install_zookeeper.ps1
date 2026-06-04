# ZooKeeper 3.9 | Port: 2181
. "$PSScriptRoot/lib/common.ps1"

$Container = "zookeeper"; $Image = "zookeeper:$($env:ZK_VERSION -replace '^$','3.9')"
$Port = if ($env:ZK_PORT) { $env:ZK_PORT } else { "2181" }
$Data = "${DataRoot}\zk-data"

check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container
New-Item -ItemType Directory -Force -Path "$Data\data","$Data\log" | Out-Null

pull_image $Image
docker run -d --name $Container --restart always -p ${Port}:2181 -e TZ=Asia/Shanghai `
  -v "${Data}\data:/data" -v "${Data}\log:/logs" `
  $Image
wait_for_container $Container 30

done_banner "ZooKeeper | Port: $Port | Data: $Data"
