# RabbitMQ 3.13 | Port: 5672, 15672 | admin/admin
. "$PSScriptRoot/lib/common.ps1"

$Container = "rabbitmq"; $Image = "rabbitmq:$($env:RMQ_VERSION -replace '^$','3.13')-management"
$Amqp = if ($env:RMQ_PORT) { $env:RMQ_PORT } else { "5672" }
$Mgmt = if ($env:RMQ_MGMT_PORT) { $env:RMQ_MGMT_PORT } else { "15672" }
$User = if ($env:RABBITMQ_USER) { $env:RABBITMQ_USER } else { "admin" }
$Pass = if ($env:RABBITMQ_PASSWORD) { $env:RABBITMQ_PASSWORD } else { "admin" }
$Data = "${DataRoot}\rabbitmq-data"

check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container
New-Item -ItemType Directory -Force -Path "$Data\data","$Data\log" | Out-Null

pull_image $Image
docker run -d --name $Container --restart unless-stopped `
  -p ${Amqp}:5672 -p ${Mgmt}:15672 `
  -e TZ=Asia/Shanghai -e "RABBITMQ_DEFAULT_USER=$User" -e "RABBITMQ_DEFAULT_PASS=$Pass" `
  -v "${Data}\data:/var/lib/rabbitmq" -v "${Data}\log:/var/log/rabbitmq" `
  $Image
wait_for_container $Container 60

done_banner "RabbitMQ | AMQP: $Amqp | Console: http://localhost:${Mgmt} | $User/$Pass"
