# Kafka UI | Port: 9097
. "$PSScriptRoot/lib/common.ps1"

$Container = "kafka-ui"; $Image = "provectuslabs/kafka-ui:$($env:UI_VERSION -replace '^$','latest')"
$Port  = if ($env:UI_PORT) { $env:UI_PORT } else { "9097" }
$Brk   = if ($env:KAFKA_BOOTSTRAP_SERVERS) { $env:KAFKA_BOOTSTRAP_SERVERS } else { "localhost:9092" }

check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container

pull_image $Image
docker run -d --name $Container --restart unless-stopped -p ${Port}:8080 -e TZ=Asia/Shanghai `
  -e KAFKA_CLUSTERS_0_NAME=local -e "KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=$Brk" `
  $Image
wait_for_container $Container 30

done_banner "Kafka UI | http://localhost:${Port}"
