# Jenkins LTS + JDK8 + Docker CLI | Port: 8080
. "$PSScriptRoot/lib/common.ps1"

# ==== Config ====
$Container = "jenkins"
$Image     = "jenkins/jenkins:lts"
$Port      = if ($env:JENKINS_PORT) { $env:JENKINS_PORT } else { "8080" }
$Data      = "${DataRoot}\jenkins-data"

# ==== Pre-check ====
check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container
New-Item -ItemType Directory -Force -Path $Data | Out-Null

# ==== Pull & Start ====
pull_image $Image
log_info "Starting Jenkins..."
$jdkMount = @()
$jdkFile = "$PSScriptRoot\..\jdk-8-linux-x64.tar.gz"
if (Test-Path $jdkFile) { $jdkMount = @("-v","${jdkFile}:/tmp/jdk8.tar.gz:ro") }

docker run -d --name $Container --restart unless-stopped `
  -p ${Port}:8080 -e TZ=Asia/Shanghai `
  -e DOCKER_HOST=tcp://host.docker.internal:2375 `
  -v "${Data}:/var/jenkins_home" `
  $jdkMount `
  $Image
log_info "Container started, waiting for init..."
for ($i = 1; $i -le 30; $i++) {
    docker ps --format '{{.Names}}' 2>$null | Select-String -Pattern "^${Container}$" | Out-Null
    if ($?) { break }
    Start-Sleep 2
}

# ==== Wait for Jenkins ====
log_info "Waiting for Jenkins init (1-3 min first time)..."
for ($i = 1; $i -le 120; $i++) {
    docker exec $Container test -f /var/jenkins_home/secrets/initialAdminPassword 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { log_info "Jenkins initialized"; break }
    if ($i % 20 -eq 0) { log_info "  waiting... (${i}/120)" }
    Start-Sleep 2
}

# ==== Install Docker CLI ====
log_info "Installing Docker CLI..."
docker exec -u root $Container bash -c 'apt-get update -qq && apt-get install -y -qq docker.io && apt-get clean' 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) { log_info "Docker CLI done" } else { log_warn "Docker CLI install failed" }

# ==== Install JDK8 ====
if (Test-Path $jdkFile) {
    log_info "Installing JDK8 (offline)..."
    docker exec -u root $Container bash -c @'
mkdir -p /usr/lib/jvm
tar xzf /tmp/jdk8.tar.gz -C /usr/lib/jvm
rm -f /tmp/jdk8.tar.gz
JDK8_DIR=$(ls -d /usr/lib/jvm/jdk* 2>/dev/null | head -1)
ln -sfn "$JDK8_DIR" /usr/lib/jvm/java-8-openjdk-amd64
echo "JDK8: $JDK8_DIR"
'@
    log_info "JDK8 done"
} else {
    log_warn "jdk-8-linux-x64.tar.gz not found, JDK8 skipped"
}

# ==== Update center mirror ====
log_info "Setting update center mirror..."
for ($i = 1; $i -le 15; $i++) {
    docker exec $Container test -f /var/jenkins_home/hudson.model.UpdateCenter.xml 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        docker exec $Container sed -i 's|https://updates.jenkins.io/[^<]*|https://mirrors.tuna.tsinghua.edu.cn/jenkins/updates/update-center.json|g' /var/jenkins_home/hudson.model.UpdateCenter.xml
        log_info "Mirror set"
        break
    }
    Start-Sleep 2
}

# ==== Done ====
$pass = docker exec $Container cat /var/jenkins_home/secrets/initialAdminPassword 2>$null
done_banner "Jenkins | http://localhost:${Port}"
log_info "Init password: $pass"
log_info "JDK8: Manage Jenkins -> Tools -> JDK -> Name=JDK8, JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64"
