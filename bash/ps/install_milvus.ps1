# Milvus 2.6.7 (standalone) | Port: 19530, 3000(Attu)
. "$PSScriptRoot/lib/common.ps1"

$Container = "milvus-standalone"; $Image = "milvusdb/milvus:$($env:MILVUS_VERSION -replace '^$','v2.6.7')"
$Port   = if ($env:MILVUS_PORT) { $env:MILVUS_PORT } else { "19530" }
$AttuP  = if ($env:ATTU_PORT) { $env:ATTU_PORT } else { "3000" }
$Data   = "${DataRoot}\milvus-data"

check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container
New-Item -ItemType Directory -Force -Path $Data | Out-Null

pull_image $Image
docker run -d --name $Container --restart unless-stopped -p ${Port}:19530 -p 9091:9091 -e TZ=Asia/Shanghai `
  -e ETCD_USE_EMBED=true -e ETCD_DATA_DIR=/var/lib/milvus/etcd -e COMMON_STORAGETYPE=local `
  -v "${Data}:/var/lib/milvus" `
  $Image milvus run standalone
wait_for_container $Container 90

# Attu
if (-not (check_container_exists "milvus-attu")) {
    cleanup_container "milvus-attu"
    $attuImg = "zilliz/attu:$($env:ATTU_VERSION -replace '^$','v2.3.4')"
    pull_image $attuImg
    docker run -d --name milvus-attu --restart unless-stopped -p ${AttuP}:3000 `
      -e "MILVUS_URL=${Container}:19530" $attuImg 2>$null
}

done_banner "Milvus | API: localhost:${Port} | Attu: http://localhost:${AttuP} | Data: $Data"
