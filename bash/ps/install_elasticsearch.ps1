# Elasticsearch 8.11 | Port: 9200, 9300
. "$PSScriptRoot/lib/common.ps1"

$Container = "elasticsearch"; $Image = "elasticsearch:$($env:ES_VERSION -replace '^$','8.11.0')"
$Port   = if ($env:ES_PORT) { $env:ES_PORT } else { "9200" }
$Trans  = if ($env:ES_TRANSPORT_PORT) { $env:ES_TRANSPORT_PORT } else { "9300" }
$Data   = "${DataRoot}\es-data"

check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container
New-Item -ItemType Directory -Force -Path "$Data\data","$Data\log" | Out-Null

pull_image $Image
docker run -d --name $Container --restart always -p ${Port}:9200 -p ${Trans}:9300 `
  -e "discovery.type=single-node" -e "xpack.security.enabled=false" `
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" -e TZ=Asia/Shanghai `
  -v "${Data}\data:/usr/share/elasticsearch/data" -v "${Data}\log:/usr/share/elasticsearch/logs" `
  $Image
wait_for_container $Container 90

done_banner "Elasticsearch | Port: $Port | Data: $Data"
