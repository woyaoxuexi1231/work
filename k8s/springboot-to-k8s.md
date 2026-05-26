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
 ① mvn package → JAR 包（你在 Windows 本地打好）
        │
        ▼
 ② docker build → 镜像（Dockerfile 只做 COPY JAR + 打包）
        │
        ▼
 ③ docker save → scp → ctr import → 导入到 K8s 集群
        │
        ▼
 ④ kubectl apply → 部署 YAML
        │
        ▼
 ⑤ MetalLB 分配 External IP → curl 验证
```

---

## 三、Step 1：Dockerfile（直接用你现有的）

你已有的 Dockerfile 不需要改，单阶段构建，只做复制 JAR + 启动：

```dockerfile
FROM eclipse-temurin:17-jre-jammy

ARG JAR_FILE=poker-tracker.jar
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

VOLUME /tmp
WORKDIR /
COPY ${JAR_FILE} /app.jar
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:-} -jar /app.jar"]
```

> `ARG JAR_FILE` 是构建时传入的参数，`run.sh` 里已自动传。

---

## 四、Step 2：构建镜像并导入集群

### 4.1 在 Windows 上打 JAR + 构建镜像

```powershell
cd d:\project\poker

# 编译
mvn clean package -DskipTests

# 构建镜像（JAR_FILE 参数让 run.sh 自动找）
bash run.sh
```

或者手动指定 JAR 文件名：

```powershell
JAR_FILE=poker-tracker-1.0.0-SNAPSHOT.jar IMAGE_NAME=poker-tracker:1.0.0 bash run.sh
```

### 4.2 推送镜像（如有本地 Registry）

```powershell
docker tag poker-tracker:1.0.0 192.168.3.100:5000/poker-tracker:1.0.0
docker push 192.168.3.100:5000/poker-tracker:1.0.0
```

> 没有 Registry？先跑 `sudo bash setup-registry.sh`，然后给 worker 节点加配置（脚本末尾有提示）。

### 4.3 没 Registry？手动分发

```powershell
docker save poker-tracker:1.0.0 -o poker-tracker.tar
scp poker-tracker.tar hulei@192.168.3.100:~/
```

```bash
# k8s-master 上
docker load -i ~/poker-tracker.tar
docker save poker-tracker:1.0.0 | ctr -n k8s.io images import -
# 同样操作 k8s-node1 / k8s-node2
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
        image: 192.168.3.100:5000/poker-tracker:1.0.0
        imagePullPolicy: Always
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
        image: 192.168.3.100:5000/poker-tracker:1.0.0
        imagePullPolicy: Always
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

```powershell
# 1. Windows 上打 JAR + 构建镜像
cd d:\project\poker
mvn clean package -DskipTests
JAR_FILE=poker-tracker-1.0.0-SNAPSHOT.jar IMAGE_NAME=poker-tracker:1.0.0 bash run.sh
```

```powershell
# 2. 推送到本地 Registry
docker tag poker-tracker:1.0.0 192.168.3.100:5000/poker-tracker:1.0.0
docker push 192.168.3.100:5000/poker-tracker:1.0.0
```

```bash
# 3. k8s-master 上部署
kubectl apply -f ~/poker-k8s/all.yaml
kubectl wait --for=condition=ready pod -l app=poker-tracker --timeout=120s
kubectl get svc poker-tracker

# 4. 浏览器打开 http://<EXTERNAL-IP>/poker
```
