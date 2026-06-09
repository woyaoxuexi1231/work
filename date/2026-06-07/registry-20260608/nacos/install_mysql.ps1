# MySQL 8.1 | Port: 3306 | Pass: 123456
. "$PSScriptRoot/lib/common.ps1"

$Container = "mysql"; $Image = "mysql:$($env:MYSQL_VERSION -replace '^$','8.1')"
$Port      = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { "3306" }
$Pass      = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "123456" }
$Data      = "${DataRoot}\mysql-data"

check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container
New-Item -ItemType Directory -Force -Path "$Data\conf","$Data\data","$Data\log" | Out-Null

if (-not (Test-Path "$Data\conf\my.cnf")) {
@'
[client] default-character-set=utf8mb4
[mysqld] user=mysql; port=3306; character-set-server=utf8mb4; default-storage-engine=InnoDB
'@ | Out-File -FilePath "$Data\conf\my.cnf" -Encoding ASCII
}

pull_image $Image
docker run -d --name $Container --restart unless-stopped -p ${Port}:3306 `
  -e "MYSQL_ROOT_PASSWORD=$Pass" -e TZ=Asia/Shanghai `
  -v "${Data}\conf\my.cnf:/etc/mysql/my.cnf" `
  -v "${Data}\data:/var/lib/mysql" -v "${Data}\log:/var/log/mysql" `
  $Image
wait_for_container $Container 60

for ($i = 1; $i -le 30; $i++) {
    docker exec $Container mysqladmin ping -h localhost --silent 2>$null; if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep 2
}

done_banner "MySQL | Port: $Port | Pass: $Pass | Data: $Data"
