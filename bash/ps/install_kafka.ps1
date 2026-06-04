# Kafka 4.1.1 (KRaft) | Port: 9092
. "$PSScriptRoot/lib/common.ps1"

$Container = "kafka"; $Image = "apache/kafka:$($env:KAFKA_VERSION -replace '^$','4.1.1')"
$Port = if ($env:KAFKA_PORT) { $env:KAFKA_PORT } else { "9092" }
$Data = "${DataRoot}\kafka-data"

check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container
New-Item -ItemType Directory -Force -Path $Data | Out-Null

pull_image $Image
docker run -d --name $Container --restart unless-stopped -p ${Port}:9092 -e TZ=Asia/Shanghai `
  -e KAFKA_NODE_ID=1 -e KAFKA_PROCESS_ROLES=broker,controller `
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 `
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 `
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 `
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 `
  -v "${Data}:/var/lib/kafka/data" `
  $Image
wait_for_container $Container 60

done_banner "Kafka | localhost:${Port} | Data: $Data"
