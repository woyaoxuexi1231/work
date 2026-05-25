# Spring Boot 应用部署到 Kubernetes 完整教程

> 以你的 Poker Tracker（德州扑克计分器）为实战案例。
> 前提：K8s 集群已就绪，MetalLB 已安装。

---

## 一、你的项目概览

| 项 | 值 |
|---|------|
| JDK | **17** |
| Spring Boot | 3.2.4 |
| 服务端口 | **8084** |
| Context Path | **/poker** |
| 数据库 | MySQL `192.168.3.100:3306/poker` |
| 特殊能力 | WebSocket（STOMP）、Spring Security、Thymeleaf |
| JAR 名 | `poker-tracker-1.0.0-SNAPSHOT.jar` |

完整访问地址：`http://192.168.3.100:8084/poker`

---

## 二、整体流程

```
你的 poker 项目源码
        │
        ▼
 ① mvn package → JAR 包
        │
        ▼
 ② docker build → 镜像（已有 Dockerfile，稍作调整）
        │
        ▼
 ③ docker save | ctr import → 导入到 containerd
        │
        ▼
 ④ 写 K8s YAML → Deployment + Service
        │
        ▼
 ⑤ kubectl apply → 部署
        │
        ▼
 ⑥ MetalLB 分配 External IP → curl 验证
```

---

## 三、Step 1：调整 Dockerfile（适配 K8s）

你现有的 Dockerfile 可以直接用，但建议改用多阶段构建，省掉手动 `mvn package` + `cp jar` 两步：

```dockerfile
# ============================
# poker-tracker Dockerfile (K8s 版)
# 多阶段构建，一个 docker build 搞定全部
# ============================

# 阶段1: 构建（Maven + JDK 17）
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
# 先下载依赖，利用 Docker 缓存层
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# 阶段2: 运行（只带 JRE，镜像小）
FROM eclipse-temurin:17-jre-jammy
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

WORKDIR /app
COPY --from=builder /app/target/poker-tracker-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8084
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]
```

> 注意：`pom.xml` 里 `<finalName>` 是 `poker`，但实际 JAR 名以 artifact + version 为准。如果不确定，先本地 `mvn package` 看看 target 目录下 JAR 叫什么。

---

## 四、Step 2：构建镜像并导入集群

在你的 **Windows** 上编译 + 构建镜像（不需要 JDK，Docker 里面有 Maven）：

```powershell
cd d:\project\poker
docker build -t poker-tracker:1.0.0 .
```

构建完成后，把镜像传到 k8s-master：

```powershell
# 导出镜像
docker save poker-tracker:1.0.0 -o poker-tracker.tar

# 通过 scp 传到 k8s-master
scp poker-tracker.tar hulei@192.168.3.100:~/poker-tracker.tar
```

在 **k8s-master** 上导入 containerd（K8s 的容器运行时）：

```bash
# 先导入 docker
docker load -i ~/poker-tracker.tar

# 再从 docker 导入 containerd
docker save poker-tracker:1.0.0 | ctr -n k8s.io images import -

# 验证
crictl images | grep poker
```

---

## 五、Step 3：K8s 部署文件

### 5.1 Deployment

```yaml
# poker-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: poker-tracker
  labels:
    app: poker-tracker
spec:
  replicas: 1                    # 先 1 个副本，WebSocket 服务多副本需要会话亲和（见第八节）
  selector:
    matchLabels:
      app: poker-tracker
  template:
    metadata:
      labels:
        app: poker-tracker
    spec:
      containers:
      - name: poker-tracker
        image: poker-tracker:1.0.0
        imagePullPolicy: Never    # 本地导入的镜像，不要尝试拉取
        ports:
        - containerPort: 8084
          name: http

        # 资源限制
        resources:
          requests:
            cpu: 200m
            memory: 384Mi
          limits:
            cpu: 1000m
            memory: 512Mi

        # 存活探针：用登录页检测（它是公开的）
        livenessProbe:
          httpGet:
            path: /poker/login
            port: 8084
          initialDelaySeconds: 45   # Spring Boot 启动慢，等久一点
          periodSeconds: 15
          failureThreshold: 3

        # 就绪探针
        readinessProbe:
          httpGet:
            path: /poker/login
            port: 8084
          initialDelaySeconds: 20
          periodSeconds: 10

        # 传递 JVM 参数
        env:
        - name: JAVA_OPTS
          value: "-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC"
```

### 5.2 Service（LoadBalancer，MetalLB 分配 VIP）

```yaml
# poker-service.yaml
apiVersion: v1
kind: Service
metadata:
  name: poker-tracker
  labels:
    app: poker-tracker
spec:
  type: LoadBalancer
  selector:
    app: poker-tracker
  ports:
  - port: 80               # 对外端口（MetalLB VIP 的端口）
    targetPort: 8084       # 容器内 Spring Boot 端口
    protocol: TCP
```

---

## 六、Step 4：部署

```bash
# 在 k8s-master 上
cd ~/poker-k8s/

# 部署
kubectl apply -f poker-deployment.yaml
kubectl apply -f poker-service.yaml

# 等待 Pod 就绪
kubectl wait --for=condition=ready pod -l app=poker-tracker --timeout=120s

# 看状态
kubectl get pods -l app=poker-tracker -o wide
kubectl get svc poker-tracker

# 输出示例：
# NAME            TYPE           CLUSTER-IP     EXTERNAL-IP      PORT(S)
# poker-tracker   LoadBalancer   10.96.x.x      192.168.3.201    80:31234/TCP
#                                              ↑ MetalLB 分配的 VIP
```

---

## 七、Step 5：访问验证

MetalLB 分配了 `192.168.3.201`，Service 对外端口是 `80`，加上 Context Path `/poker`：

```
http://192.168.3.201/poker
```

在你的 Windows 浏览器打开这个地址，应该能看到登录页面。

```bash
# 在 k8s-master 上 curl 验证
curl -I http://192.168.3.201/poker/login
# HTTP/1.1 200 OK
```

---

## 八、WebSocket 注意事项

你的扑克项目用了 STOMP over WebSocket。单副本没问题，如果以后想扩容到 2+ 副本，需要处理 **WebSocket 粘性会话（Sticky Session）**：

### 8.1 Service 加 sessionAffinity

```yaml
# poker-service.yaml 里加一行
spec:
  type: LoadBalancer
  sessionAffinity: ClientIP    # ← 同一个客户端 IP 始终路由到同一个 Pod
  sessionAffinityConfig:
    clientIP:
      timeoutSeconds: 10800    # 3 小时
  # ... 其余不变
```

```bash
kubectl apply -f poker-service.yaml
```

### 8.2 扩容

```bash
kubectl scale deployment poker-tracker --replicas=2
```

加 `ClientIP` 亲和后，同一用户的 WebSocket 连接不会被切到另一个 Pod。

---

## 九、MySQL 连接

你的 `application.yml` 已经配了 `jdbc:mysql://192.168.3.100:3306/poker`。只要这台 MySQL 对 K8s 集群网络可达就行。

### 验证 Pod 是否能连 MySQL

```bash
# 进入 Pod
kubectl exec -it deployment/poker-tracker -- /bin/bash

# 测试 MySQL 连通性
apt-get update && apt-get install -y mysql-client 2>/dev/null
mysql -h 192.168.3.100 -u root -p -e "SELECT 1"
```

如果连不上，常见原因：
1. MySQL 只绑定了 `127.0.0.1` → 改成 `0.0.0.0` 或 `192.168.3.100`
2. MySQL 用户 `root` 没有远程访问权限 → `GRANT ALL ON *.* TO 'root'@'%'`
3. 防火墙 → `sudo ufw allow 3306`

---

## 十、日常操作

### 更新镜像

```powershell
# Windows 上重新构建
docker build -t poker-tracker:1.0.1 .
docker save poker-tracker:1.0.1 -o poker-tracker.tar
scp poker-tracker.tar hulei@192.168.3.100:~/
```

```bash
# k8s-master 上
docker load -i ~/poker-tracker.tar
docker save poker-tracker:1.0.1 | ctr -n k8s.io images import -
kubectl set image deployment/poker-tracker poker-tracker=poker-tracker:1.0.1
kubectl rollout status deployment/poker-tracker
```

### 看日志

```bash
kubectl logs -f deployment/poker-tracker
```

### 重启

```bash
kubectl rollout restart deployment/poker-tracker
```

### 删除

```bash
kubectl delete -f poker-deployment.yaml
kubectl delete -f poker-service.yaml
```

---

## 十一、一键部署文件

把 Deployment + Service 合并到一个文件，创建一个 `~/poker-k8s/all.yaml`：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: poker-tracker
spec:
  replicas: 1
  selector:
    matchLabels:
      app: poker-tracker
  template:
    metadata:
      labels:
        app: poker-tracker
    spec:
      containers:
      - name: poker-tracker
        image: poker-tracker:1.0.0
        imagePullPolicy: Never
        ports:
        - containerPort: 8084
        resources:
          requests: {cpu: 200m, memory: 384Mi}
          limits:   {cpu: 1000m, memory: 512Mi}
        livenessProbe:
          httpGet: {path: /poker/login, port: 8084}
          initialDelaySeconds: 45
          periodSeconds: 15
        readinessProbe:
          httpGet: {path: /poker/login, port: 8084}
          initialDelaySeconds: 20
          periodSeconds: 10
        env:
        - name: JAVA_OPTS
          value: "-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m"
---
apiVersion: v1
kind: Service
metadata:
  name: poker-tracker
spec:
  type: LoadBalancer
  sessionAffinity: ClientIP
  sessionAffinityConfig:
    clientIP:
      timeoutSeconds: 10800
  selector:
    app: poker-tracker
  ports:
  - port: 80
    targetPort: 8084
```

一条命令搞定：

```bash
kubectl apply -f all.yaml
```

---

## 十二、架构全景

```
    你的 Windows 浏览器
          │
          │ http://192.168.3.201/poker
          ▼
┌──────────────────┐
│    MetalLB        │  ← VIP 192.168.3.201
│   (Layer2)        │     节点挂了自动漂移
└────────┬─────────┘
         │
┌────────▼─────────┐
│  Service          │  ← type: LoadBalancer
│  poker-tracker    │     对外 80 → 容器 8084
│  sessionAffinity  │     WebSocket 粘性会话
│  :ClientIP        │
└────────┬─────────┘
         │
┌────────▼─────────┐
│  Pod poker-tracker│  ← Deployment 管理
│  :8084            │     1 副本（可扩）
│  Spring Boot 3.2  │
│  /poker/login     │
│  WebSocket STOMP  │
└────────┬─────────┘
         │
         │ jdbc:mysql://192.168.3.100:3306/poker
         ▼
┌──────────────────┐
│  MySQL            │  ← 外部数据库，不在 K8s 里
│  192.168.3.100    │
│  poker 库          │
└──────────────────┘
```

---

## 快速开始命令汇总

```bash
# 1. Windows 上构建镜像
cd d:\project\poker
docker build -t poker-tracker:1.0.0 .
docker save poker-tracker:1.0.0 -o poker-tracker.tar
scp poker-tracker.tar hulei@192.168.3.100:~/

# 2. k8s-master 上导入镜像
docker load -i ~/poker-tracker.tar
docker save poker-tracker:1.0.0 | ctr -n k8s.io images import -

# 3. 部署
mkdir -p ~/poker-k8s
# 把上面的 all.yaml 写入 ~/poker-k8s/all.yaml
kubectl apply -f ~/poker-k8s/all.yaml

# 4. 等待就绪 + 获取访问地址
kubectl wait --for=condition=ready pod -l app=poker-tracker --timeout=120s
kubectl get svc poker-tracker

# 5. 浏览器打开 http://<EXTERNAL-IP>/poker
```
