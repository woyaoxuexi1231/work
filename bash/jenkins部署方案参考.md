我建议你这套环境**不要上 Agent**。

因为你现在只有一台 Windows Server：

```
Windows Server
    │
    └── Docker Desktop
            │
            ├── Jenkins
            ├── MySQL
            ├── Redis
            └── SpringBoot（后续自动部署）
```

这种情况下，最简单、最稳定、维护成本最低的方案就是：

> **Jenkins（Docker） + Docker Desktop + 宿主Docker API + Pipeline 自动部署**

很多中小团队都是这么干的。

---

# 一、整体架构

整个流程如下：

```
GitLab/Gitee/Github
         │
         ▼
+------------------+
| Jenkins          |
| (Docker容器)      |
+------------------+
         │
         │ git clone
         ▼
+------------------+
| Maven Build      |
| mvn package      |
+------------------+
         │
         │ docker build
         ▼
+------------------+
| Docker Desktop   |
|                  |
| springboot:latest|
+------------------+
         │
         │ docker run
         ▼
+------------------+
| SpringBoot容器    |
+------------------+
```

整个发布流程：

```
提交代码
    ↓
Jenkins拉代码
    ↓
Maven打包
    ↓
生成jar
    ↓
Docker构建镜像
    ↓
停止旧容器
    ↓
启动新容器
    ↓
发布完成
```

---

# 二、Windows 安装 Docker Desktop

假设已经安装完成。

打开：

```
Docker Desktop
    Settings
        Docker Engine
```

建议开启：

```
Expose daemon on tcp://localhost:2375 without TLS
```

新版可能在：

```
Settings
    General
        ☑ Expose daemon on tcp://localhost:2375 without TLS
```

保存并重启。

---

# 三、启动 Jenkins

建议不要使用默认镜像，而是自己做一个包含：

* Git
* Maven
* JDK17
* Docker CLI

的镜像。

## 1、创建 Dockerfile

```dockerfile
FROM jenkins/jenkins:lts

USER root

RUN apt-get update && \
    apt-get install -y \
    git \
    maven \
    openjdk-17-jdk \
    docker.io

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV DOCKER_HOST=tcp://host.docker.internal:2375

USER jenkins
```

保存：

```
Dockerfile
```

构建：

```bash
docker build -t my-jenkins .
```

---

## 2、启动 Jenkins

```bash
docker run -d \
--name jenkins \
-p 8080:8080 \
-p 50000:50000 \
-v jenkins_home:/var/jenkins_home \
-e DOCKER_HOST=tcp://host.docker.internal:2375 \
my-jenkins
```

查看日志：

```bash
docker logs -f jenkins
```

获取初始密码：

```bash
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

浏览器：

```
http://服务器IP:8080
```

完成初始化。

---

# 四、验证 Docker 是否可用

进入 Jenkins：

```bash
docker exec -it jenkins bash
```

执行：

```bash
docker ps
```

如果看到：

```
CONTAINER ID
xxxx jenkins
xxxx mysql
xxxx redis
```

说明 Jenkins 已经能够控制 Docker Desktop。

再测试：

```bash
docker images
```

```bash
mvn -version
```

```bash
git --version
```

全部正常即可。

---

# 五、准备 SpringBoot 项目

项目结构：

```
demo
│
├── src
├── pom.xml
├── Dockerfile
└── Jenkinsfile（可选）
```

---

## pom.xml

正常 SpringBoot 即可：

```xml
<packaging>jar</packaging>
```

打包：

```
target/demo-1.0.jar
```

---

# 六、编写 Dockerfile

放到项目根目录：

```dockerfile
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]
```

测试：

```bash
docker build -t demo:latest .
```

运行：

```bash
docker run -d \
--name demo \
-p 8081:8080 \
demo:latest
```

浏览器：

```
http://服务器IP:8081
```

能访问说明没问题。

---

# 七、Jenkins 创建 Pipeline

安装插件：

```
Pipeline
Git
GitHub（如果需要）
Docker Pipeline（可选）
```

创建：

```
New Item
    Pipeline
```

名称：

```
springboot-demo
```

Pipeline：

选择：

```
Pipeline script
```

---

# 八、完整 Jenkins Pipeline

如果 Jenkins 是 Linux 容器：

全部使用：

```groovy
sh ""
```

不要写 bat。

完整脚本：

```groovy
pipeline {

    agent any

    environment {
        IMAGE_NAME = "demo"
        CONTAINER_NAME = "demo"
    }

    stages {

        stage('Checkout') {
            steps {
                git branch: 'master',
                url: 'https://你的git地址/demo.git'
            }
        }

        stage('Build Jar') {
            steps {
                sh '''
                    mvn clean package -Dmaven.test.skip=true
                '''
            }
        }

        stage('Build Docker Image') {
            steps {
                sh '''
                    docker build -t ${IMAGE_NAME}:latest .
                '''
            }
        }

        stage('Stop Old Container') {
            steps {
                sh '''
                    docker rm -f ${CONTAINER_NAME} || true
                '''
            }
        }

        stage('Run New Container') {
            steps {
                sh '''
                    docker run -d \
                    --name ${CONTAINER_NAME} \
                    -p 8081:8080 \
                    ${IMAGE_NAME}:latest
                '''
            }
        }

    }

}
```

---

# 九、第一次执行流程

点击：

```
Build Now
```

后台执行：

```
Git Clone
    ↓
Maven Clean
    ↓
Maven Package
    ↓
target/demo.jar
    ↓
Docker Build
    ↓
demo:latest
    ↓
docker rm -f demo
    ↓
docker run -d demo
```

查看日志：

```
Console Output
```

最后看到：

```
Finished: SUCCESS
```

---

# 十、以后更新代码

开发：

```
修改代码
    ↓
git push
    ↓
点击 Jenkins Build
```

自动：

```
拉代码
    ↓
打包
    ↓
构建镜像
    ↓
删除旧容器
    ↓
启动新容器
```

整个过程大约：

```
30 秒 ~ 2 分钟
```

---

# 十一、推荐增加镜像清理

否则镜像会越来越多。

在 Pipeline 最后增加：

```groovy
stage('Clean Image') {
    steps {
        sh '''
            docker image prune -f
        '''
    }
}
```

或者：

```groovy
sh '''
docker images

docker image prune -af --filter "until=24h"
'''
```

---

# 十二、推荐目录结构

宿主机：

```
D:\
 ├── docker
 │     ├── jenkins_home
 │     └── data
 │
 ├── git
 │
 └── backup
```

Jenkins 数据建议映射：

```bash
-v D:/docker/jenkins_home:/var/jenkins_home
```

这样即使重新创建 Jenkins 容器，所有：

* Job
* 插件
* 用户
* Pipeline

都会保留。

---

# 十三、最终推荐架构（适合你当前）

```
Windows Server
│
├── Docker Desktop
│
├── Jenkins
│     │
│     ├── Git
│     ├── Maven
│     ├── JDK17
│     └── Docker CLI
│
├── MySQL
│
├── Redis
│
└── SpringBoot
       ↑
       │
   Jenkins自动发布
```

发布流程：

```
git push
    ↓
Jenkins
    ↓
git clone
    ↓
mvn clean package
    ↓
docker build
    ↓
docker rm -f demo
    ↓
docker run -d demo
    ↓
发布完成
```

这是你目前这台 **Windows Server + Docker Desktop + Jenkins 容器** 最简单且最容易维护的方案，后面如果再接入 Nacos、RabbitMQ、Vue 前端，也只需要在 Pipeline 里增加对应的构建步骤即可，不需要引入 Agent。
