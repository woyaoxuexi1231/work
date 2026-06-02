# Jenkins 部署 Spring Boot 项目 — Pipeline 流水线教程

从头到尾：拉代码 → 编译 → 打包 → 构建镜像 → 运行容器。

---

## 一、项目准备

假设你的 Spring Boot 项目结构：

```
my-app/
├── src/
├── pom.xml          ← Maven 项目
├── Dockerfile       ← Docker 构建文件
└── Jenkinsfile      ← Jenkins 流水线定义（本文档重点）
```

---

## 二、创建 Dockerfile

在项目根目录新建 `Dockerfile`：

```dockerfile
FROM openjdk:8-jdk-slim
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

> 端口号改成你项目实际的，比如 8080。

---

## 三、创建 Jenkinsfile

在项目根目录新建 `Jenkinsfile`，内容如下：

```groovy
pipeline {
    agent any

    tools {
        jdk 'JDK8'
        maven 'Maven3'
    }

    environment {
        // 容器参数（按需修改）
        APP_NAME = 'my-springboot-app'
        APP_PORT = '9090'
    }

    stages {
        stage('Checkout') {
            steps {
                echo '=== 拉取代码 ==='
                checkout scm
            }
        }

        stage('Maven Build') {
            steps {
                echo '=== Maven 编译打包 ==='
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Docker Build') {
            steps {
                echo '=== 构建 Docker 镜像 ==='
                sh "docker build -t ${APP_NAME}:latest ."
            }
        }

        stage('Deploy') {
            steps {
                echo '=== 停止旧容器 ==='
                sh "docker rm -f ${APP_NAME} || true"
                echo '=== 启动新容器 ==='
                sh """
                    docker run -d \
                      --name ${APP_NAME} \
                      --restart=unless-stopped \
                      -p ${APP_PORT}:8080 \
                      -e TZ=Asia/Shanghai \
                      ${APP_NAME}:latest
                """
            }
        }

        stage('Verify') {
            steps {
                echo '=== 验证部署 ==='
                sh "sleep 5 && curl -sf -o /dev/null http://localhost:${APP_PORT}/actuator/health || echo 'Health check not available, check logs'"
                sh "docker ps --filter name=${APP_NAME} --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'"
            }
        }
    }

    post {
        success {
            echo "✅ ${APP_NAME} 部署成功 -> http://localhost:${APP_PORT}"
        }
        failure {
            echo "❌ 部署失败，检查日志"
        }
    }
}
```

---

## 四、推送代码到 Git

确保项目（含 `Dockerfile` + `Jenkinsfile`）已推送：

```bash
git add .
git commit -m "add pipeline"
git push
```

---

## 五、Jenkins 创建流水线任务

### 5.1 新建 Pipeline

1. Jenkins 首页 → **New Item**
2. 输入任务名，比如 `my-springboot-app`
3. 选择 **Pipeline**，点 **OK**

### 5.2 配置 Git 仓库

拉到 **Pipeline** 配置区域：

| 配置项 | 值 |
|--------|-----|
| Definition | `Pipeline script from SCM` |
| SCM | `Git` |
| Repository URL | 你的 Git 仓库地址，比如 `https://github.com/xxx/my-app.git` |
| Branches to build | `*/main`（或你的分支） |
| Script Path | `Jenkinsfile` |

> 如果是私有仓库，点 **Add** → **Jenkins** 添加用户名密码凭据。

### 5.3 配置触发器（可选）

勾选 **Poll SCM**，Schedule 填 `H/5 * * * *`，每 5 分钟检查一次代码变更自动构建。

### 5.4 保存

点 **Save**。

---

## 六、首次构建

回到任务页面，点 **Build Now**。左侧会看到构建进度条，点进构建号 → **Console Output** 可以看实时日志。

流水线的五个阶段依次执行：

```
Checkout     → 拉取 Git 代码
Maven Build  → mvn clean package
Docker Build → docker build
Deploy       → 停止旧容器，启动新容器
Verify       → 健康检查
```

全部绿色就是成功了。

---

## 七、后续使用

### 自动触发

- 代码 push 后会自动检测（如果配置了 Poll SCM）
- 也可以在 Jenkins 页面手动点 **Build Now**

### 查看部署状态

```bash
docker ps --filter name=my-springboot-app
curl http://localhost:9090/actuator/health
```

### 回滚

```bash
# 重新构建上一个版本
docker ps -a  # 找到之前的容器或镜像
```

---

## 八、常见问题

| 问题 | 解决 |
|------|------|
| `docker: command not found` | Jenkins 容器里没 Docker CLI，需要挂载 `-v /var/run/docker.sock:/var/run/docker.sock` |
| Maven 构建太慢 | 用阿里云 Maven 仓库：`settings.xml` 配置 `mirrors.aliyun.com` |
| 端口冲突 | 改 Jenkinsfile 里的 `APP_PORT` |
| 健康检查失败 | 确保项目有 `/actuator/health` 端点（加 `spring-boot-starter-actuator` 依赖） |

---

## 九、让 Jenkins 能调 Docker（DinD）

两个启动脚本已经默认挂载了 `-v /var/run/docker.sock:/var/run/docker.sock`，无需额外配置。Windows Docker Desktop 同样支持，不需要修改。

---

## 十、完整流程总结

```
你 push 代码
    ↓
Jenkins 检测到变更（或手动 Build Now）
    ↓
拉代码 → mvn package → docker build → docker run
    ↓
应用运行在 http://localhost:9090
```

Done.
