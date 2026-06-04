# Jenkins LTS + JDK8 + Docker CLI | Port: 8080
. "$PSScriptRoot/lib/common.ps1"

# ==== 配置 ====
$Container = "jenkins"
$Image     = "jenkins/jenkins:lts"
$Port      = if ($env:JENKINS_PORT) { $env:JENKINS_PORT } else { "8080" }
$Data      = "${DataRoot}\jenkins-data"

# ==== 前置检查 ====
check_docker
if (check_container_exists $Container) { exit 0 }
cleanup_container $Container
New-Item -ItemType Directory -Force -Path $Data | Out-Null

# ==== 拉取 + 启动 ====
pull_image $Image
log_info "启动 Jenkins..."
$jdkMount = @()
$jdkFile = "$PSScriptRoot\..\jdk-8-linux-x64.tar.gz"
if (Test-Path $jdkFile) { $jdkMount = @("-v","${jdkFile}:/tmp/jdk8.tar.gz:ro") }

docker run -d --name $Container --restart unless-stopped `
  -p ${Port}:8080 -e TZ=Asia/Shanghai `
  -e DOCKER_HOST=tcp://host.docker.internal:2375 `
  -v "${Data}:/var/jenkins_home" `
  $jdkMount `
  $Image
wait_for_container $Container 30

# ==== 等待 Jenkins 初始化 ====
log_info "等待 Jenkins 初始化（首次 1-3 分钟）..."
for ($i = 1; $i -le 120; $i++) {
    docker exec $Container test -f /var/jenkins_home/secrets/initialAdminPassword 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { log_info "✓ 初始化完成"; break }
    if ($i % 20 -eq 0) { log_info "  等待中... (${i}/120)" }
    Start-Sleep 2
}

# ==== 安装 Docker CLI ====
log_info "安装 Docker CLI..."
docker exec -u root $Container bash -c "apt-get update -qq && apt-get install -y -qq docker.io && apt-get clean" 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) { log_info "✓ Docker CLI 完成" } else { log_warn "Docker CLI 安装失败" }

# ==== 安装 JDK8 ====
if (Test-Path $jdkFile) {
    log_info "安装 JDK8 (离线)..."
    docker exec -u root $Container bash -c @'
mkdir -p /usr/lib/jvm
tar xzf /tmp/jdk8.tar.gz -C /usr/lib/jvm
rm -f /tmp/jdk8.tar.gz
JDK8_DIR=$(ls -d /usr/lib/jvm/jdk* 2>/dev/null | head -1)
ln -sfn "$JDK8_DIR" /usr/lib/jvm/java-8-openjdk-amd64
echo "JDK8: $JDK8_DIR"
'@
    log_info "✓ JDK8 完成"
} else {
    log_warn "未找到 ${jdkFile}，跳过 JDK8"
}

# ==== 切换更新中心 ====
log_info "切换更新中心..."
for ($i = 1; $i -le 15; $i++) {
    docker exec $Container test -f /var/jenkins_home/hudson.model.UpdateCenter.xml 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        docker exec $Container sed -i 's|https://updates.jenkins.io/[^<]*|https://mirrors.tuna.tsinghua.edu.cn/jenkins/updates/update-center.json|g' /var/jenkins_home/hudson.model.UpdateCenter.xml
        log_info "✓ 已切换"
        break
    }
    Start-Sleep 2
}

# ==== 完成 ====
$pass = docker exec $Container cat /var/jenkins_home/secrets/initialAdminPassword 2>$null
done_banner "Jenkins | http://localhost:${Port}"
log_info "初始密码: $pass"
log_info "JDK8: Manage Jenkins -> Tools -> JDK -> Name=JDK8, JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64"
