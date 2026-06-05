# Prometheus + Grafana | Port: 9090(Prom), 3000(Grafana)
. "$PSScriptRoot/lib/common.ps1"

$PromP = if ($env:PROMETHEUS_PORT) { $env:PROMETHEUS_PORT } else { "9090" }
$GrafP = if ($env:GRAFANA_PORT) { $env:GRAFANA_PORT } else { "3000" }
$User  = if ($env:GRAFANA_USER) { $env:GRAFANA_USER } else { "admin" }
$Pass  = if ($env:GRAFANA_PASSWORD) { $env:GRAFANA_PASSWORD } else { "admin123" }
$Data  = "${DataRoot}\monitor-data"

check_docker
New-Item -ItemType Directory -Force -Path "$Data\prometheus","$Data\grafana" | Out-Null

if (-not (check_container_exists "prometheus")) {
    cleanup_container "prometheus"
    pull_image "prom/prometheus:v2.45.0"
    docker run -d --name prometheus --restart unless-stopped -p ${PromP}:9090 -e TZ=Asia/Shanghai `
      -v "${Data}\prometheus:/prometheus" `
      prom/prometheus:v2.45.0 --config.file=/etc/prometheus/prometheus.yml --web.enable-lifecycle
    wait_for_container "prometheus" 15
}

if (-not (check_container_exists "grafana")) {
    cleanup_container "grafana"
    pull_image "grafana/grafana:9.5.8"
    docker run -d --name grafana --restart unless-stopped -p ${GrafP}:3000 -e TZ=Asia/Shanghai `
      -e "GF_SECURITY_ADMIN_USER=$User" -e "GF_SECURITY_ADMIN_PASSWORD=$Pass" `
      -v "${Data}\grafana:/var/lib/grafana" `
      grafana/grafana:9.5.8
    wait_for_container "grafana" 15
}

done_banner "Prometheus: http://localhost:${PromP} | Grafana: http://localhost:${GrafP} ($User/$Pass)"
