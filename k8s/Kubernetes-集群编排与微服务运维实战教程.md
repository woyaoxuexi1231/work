# Kubernetes 集群编排与微服务运维实战教程

> 目标：从已有集群出发，掌握 Kubernetes 集群编排核心概念与微服务运维流程
> 前置条件：已搭建好 K8s 集群（1 Master + 2 Worker），kubectl 可正常使用

---

## 目录

- [第一部分：集群编排核心](#第一部分集群编排核心)
  - [第1课 Pod —— 最小调度单元](#第1课-pod--最小调度单元)
  - [第2课 Deployment —— 声明式应用管理](#第2课-deployment--声明式应用管理)
  - [第3课 Service —— 稳定的网络入口](#第3课-service--稳定的网络入口)
  - [第4课 Namespace & Label —— 组织与选择](#第4课-namespace--label--组织与选择)
  - [第5课 ConfigMap & Secret —— 配置管理](#第5课-configmap--secret--配置管理)
  - [第6课 健康检查 —— 自愈机制](#第6课-健康检查--自愈机制)
  - [第7课 资源管理 —— Requests & Limits](#第7课-资源管理--requests--limits)
  - [第8课 滚动更新与回滚](#第8课-滚动更新与回滚)
  - [第9课 HPA —— 自动伸缩](#第9课-hpa--自动伸缩)
- [第二部分：微服务运维流程](#第二部分微服务运维流程)
  - [第10课 服务发现 —— Pod 间如何找到对方](#第10课-服务发现--pod-间如何找到对方)
  - [第11课 Ingress —— 统一网关入口](#第11课-ingress--统一网关入口)
  - [第12课 存储 —— PV & PVC](#第12课-存储--pv--pvc)
  - [第13课 网络策略 NetworkPolicy](#第13课-网络策略-networkpolicy)
  - [第14课 监控与日志](#第14课-监控与日志)
  - [第15课 CI/CD 集成思路](#第15课-cicd-集成思路)
  - [第16课 生产最佳实践 Checklist](#第16课-生产最佳实践-checklist)

---

# 第一部分：集群编排核心

## 第1课 Pod —— 最小调度单元

### 1.1 概念

**Pod** 是 Kubernetes 中**最小的部署和调度单元**。一个 Pod 封装一个或多个容器，共享：

- **网络**：同一个 Pod 内的容器共享 IP 和端口空间（通过 localhost 通信）
- **存储**：可以挂载相同的 Volume
- **生命周期**：一起创建、一起销毁

> 一句话记忆：Pod 是 K8s 里的"逻辑主机"，而不是单个进程。

### 1.2 动手：创建第一个 Pod

用命令式方式启动一个 Nginx Pod：

```bash
# 创建一个 Nginx Pod
kubectl run my-nginx --image=nginx:1.25-alpine --port=80
# 临时暴露端口
kubectl port-forward pod/my-nginx 8080:80 --address=0.0.0.0 &
# NodePort Service（正式方案）
kubectl expose pod my-nginx --type=NodePort --port=80 --target-port=80 --name=my-nginx-svc


# 查看 Pod 状态
kubectl get pods -o wide

# 查看详细信息（包括事件、IP、挂载等）
kubectl describe pod my-nginx
```

输出解读：

```
NAME       READY   STATUS    RESTARTS   AGE   IP              NODE
my-nginx   1/1     Running   0          30s   10.244.1.2      k8s-node1
```

- **READY**: `1/1` 表示 Pod 中 1 个容器全部就绪
- **IP**: `10.244.1.2` — Calico 分配的 Pod IP，**集群内可直接访问**
- **NODE**: 调度到了 k8s-node1

### 1.3 验证网络

```bash
# 在另一个 Pod 中访问这个 Pod（集群内网络直通）
kubectl run test-pod --rm -it --image=busybox -- /bin/sh -c "wget -q -O- http://10.244.1.2"
```

看到 Nginx 欢迎页说明 Pod IP 直通成功。**这就是 Calico 网络插件的作用**。

### 1.4 删除 Pod

```bash
kubectl delete pod my-nginx
```

### 1.5 用 YAML 声明式创建（推荐）

```yaml
# 01-pod.yaml
apiVersion: v1
kind: Pod
metadata:
  name: my-nginx
  labels:
    app: nginx
    environment: demo
spec:
  containers:
  - name: nginx
    image: nginx:1.25-alpine
    ports:
    - containerPort: 80
```

```bash
kubectl apply -f 01-pod.yaml
kubectl delete -f 01-pod.yaml
```

| 字段 | 含义 |
|------|------|
| `apiVersion: v1` | API 版本，不同资源类型不同 |
| `kind: Pod` | 资源类型 |
| `metadata.name` | Pod 名称（集群内唯一） |
| `metadata.labels` | 标签（后续 Service / Deployment 靠它关联） |
| `spec.containers` | 容器定义列表 |
| `spec.containers[].image` | 容器镜像 |

### 1.6 多容器 Pod（边车模式）

```yaml
# 01-sidecar.yaml
apiVersion: v1
kind: Pod
metadata:
  name: sidecar-demo
spec:
  containers:
  - name: app
    image: nginx:1.25-alpine
    ports:
    - containerPort: 80
  - name: sidecar
    image: alpine:3.18
    command: ["/bin/sh", "-c", "while true; do echo 'sidecar is alive' >> /var/log/messages; sleep 5; done"]
```

**场景**：日志收集 Sidecar（Fluentd/filebeat 与主应用同 Pod）、代理 Sidecar（Istio Envoy）

---

## 第2课 Deployment —— 声明式应用管理

### 2.1 为什么不用 Pod？

直接用 Pod 的问题：

1. Pod 挂了**不会自动恢复**（没有自愈能力）
2. **不能扩缩容** — 想跑 3 个副本就得手动创建 3 个 Pod
3. **更新麻烦** — 删除重建，无法滚动

**Deployment** 是管理 Pod 的**上层控制器**，提供：

- ✅ 声明式更新（改 YAML → apply 即可）
- ✅ 副本管理（replicas 控制 Pod 数量）
- ✅ 滚动更新 & 回滚
- ✅ 自愈（Pod 挂了自动重建）

### 2.2 动手：创建 Deployment

```yaml
# 02-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deploy
  labels:
    app: nginx
spec:
  replicas: 3
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: nginx:1.25-alpine
        ports:
        - containerPort: 80
```

```bash
kubectl apply -f 02-deployment.yaml

# 查看 Deployment
kubectl get deployments

# 查看它管理的 Pod（自动创建了 3 个）
kubectl get pods -l app=nginx
```

### 2.3 架构关系

```
Deployment (声明期望状态: 3 副本, 镜像 nginx:1.25)
    │
    ├── ReplicaSet (通过 selector 维护 Pod 数量)
    │       │
    │       ├── Pod nginx-deploy-xxxxx-a
    │       ├── Pod nginx-deploy-xxxxx-b
    │       └── Pod nginx-deploy-xxxxx-c
```

Deployment → ReplicaSet → Pod 是三层关系。RS 自动创建，你只需要管 Deployment。

### 2.4 扩缩容

```bash
# 方式一：kubectl scale
kubectl scale deployment nginx-deploy --replicas=5

# 方式二：修改 YAML (replicas: 5)，然后 apply
kubectl apply -f 02-deployment.yaml

# 验证
kubectl get pods -l app=nginx
kubectl get deployments
```

### 2.5 自愈演示

```bash
# 删除一个 Pod
kubectl delete pod -l app=nginx --max=1

# 观察 —— Deployment 会立刻重建
kubectl get pods -l app=nginx -w
```

你会看到：一个 Pod 被删除，另一个新 Pod 立即被创建（名字后缀变了）。**这就是自愈**。你不需要手动干预。

### 2.6 清除

```bash
kubectl delete deployment nginx-deploy
```

---

## 第3课 Service —— 稳定的网络入口

### 3.1 问题

Pod 有两个特性导致直接访问不安全：

1. **IP 不固定** — Pod 重启重建后 IP 会变
2. **Pod 可以扩缩** — 你无法预先知道有多少个 Pod

**Service** 提供：

- 一个**稳定的虚拟 IP（ClusterIP）**
- 自动负载均衡到后端的 Pod 集合
- 通过 **Label Selector** 自动发现 Pod

### 3.2 动手：创建 Service

先用 Deployment 启动一组 Pod：

```bash
kubectl create deployment web --image=nginx:1.25-alpine --replicas=3
```

然后创建 Service：

```bash
# 命令式
kubectl expose deployment web --port=80 --target-port=80

# 或声明式
```

```yaml
# 03-service.yaml
apiVersion: v1
kind: Service
metadata:
  name: web-svc
spec:
  selector:
    app: web
  ports:
  - port: 80          # Service 端口
    targetPort: 80    # Pod 容器端口
  type: ClusterIP     # 默认类型，集群内可访问
```

```bash
kubectl apply -f 03-service.yaml

# 查看 Service
kubectl get svc
```

输出：

```
NAME         TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)   AGE
web-svc      ClusterIP   10.96.100.50    <none>        80/TCP    10s
kubernetes   ClusterIP   10.96.0.1       <none>        443/TCP   1h
```

注意 `kubernetes` 这个 Service 是集群自带的，指向 API Server。

### 3.3 验证 Service 的负载均衡

```bash
# 进入集群内的一个临时 Pod，访问 Service
kubectl run test --rm -it --image=busybox -- /bin/sh

# 在 busybox shell 中
wget -q -O- http://web-svc
# 看到 Nginx 欢迎页

# 查看 DNS 解析
nslookup web-svc
# 输出: Name: web-svc.default.svc.cluster.local
#       Address: 10.96.100.50
```

**关键知识点**：

- Service 名称就是 DNS 名称：`<service>.<namespace>.svc.cluster.local`
- 每次请求会随机转发到一个后端 Pod
- ClusterIP 是虚拟 IP，**无法 ping 通**（它是 iptables/IPVS 规则，不是真实网卡）

### 3.4 Service 类型对比

| 类型 | 访问范围 | 典型场景 |
|------|---------|---------|
| **ClusterIP**（默认） | 集群内部 | 微服务间调用 |
| **NodePort** | 集群外部（节点 IP + 端口） | 开发测试、对外暴露 |
| **LoadBalancer** | 外部（云厂商 LB） | 云原生生产环境 |
| **ExternalName** | DNS CNAME 映射 | 将集群外服务映射为集群内 DNS |

### 3.5 NodePort 类型

```yaml
# 03-nodeport.yaml
apiVersion: v1
kind: Service
metadata:
  name: web-nodeport
spec:
  selector:
    app: web
  ports:
  - port: 80
    targetPort: 80
    nodePort: 30080
  type: NodePort
```

```bash
kubectl apply -f 03-nodeport.yaml

# 查看
kubectl get svc web-nodeport
# 输出: web-nodeport   NodePort   10.96.xx.xx   <none>   80:30080/TCP
```

访问方式：`http://<任意节点IP>:30080`

```bash
# 在你的环境（Master/Woker）上测试
curl http://192.168.3.100:30080
curl http://192.168.3.101:30080
curl http://192.168.3.102:30080
```

**三者都返回 Nginx 欢迎页** — 因为 kube-proxy 在每个节点上都创建了转发规则。

### 3.6 Service 工作原理图

```
用户请求 → NodeIP:30080
                ↓
          kube-proxy (每个节点都有)
                ↓
        IPVS / iptables 规则
                ↓
    ┌───────────┼───────────┐
    ↓           ↓           ↓
 Pod-A       Pod-B       Pod-C
(10.244.1.2) (10.244.2.3) (10.244.1.5)
```

### 3.7 清除

```bash
kubectl delete deployment web
kubectl delete svc web-svc web-nodeport
```

---

## 第4课 Namespace & Label —— 组织与选择

### 4.1 Namespace：逻辑隔离

**Namespace** 将集群资源划分为多个虚拟集群。常见用途：

- 环境隔离：`dev` / `staging` / `prod`
- 团队隔离：`team-a` / `team-b`
- 系统组件：`kube-system` / `kube-public`

```bash
# 查看已有 Namespace
kubectl get namespaces

# 创建 Namespace
kubectl create namespace demo

# 在 Namespace 中操作
kubectl get pods -n demo
kubectl run nginx -n demo --image=nginx:1.25-alpine
```

YAML 方式：

```yaml
# 04-namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: demo
---
apiVersion: v1
kind: Pod
metadata:
  name: nginx-demo
  namespace: demo
spec:
  containers:
  - name: nginx
    image: nginx:1.25-alpine
```

### 4.2 Label：组织和选择

**Label** 是 K8s 的核心设计模式——一切资源通过标签关联，而非硬编码：

```yaml
metadata:
  labels:
    app: web
    tier: frontend
    environment: production
    version: v2
```

常见用途：

| Label 用途 | 示例 |
|-----------|------|
| 环境标识 | `environment=production` / `environment=staging` |
| 应用分层 | `tier=frontend` / `tier=backend` / `tier=db` |
| 版本管理 | `version=v1` / `version=v2` |
| 团队归属 | `team=platform` / `team=payment` |

### 4.3 Label Selector 实战

```bash
# 给 Pod 打标签
kubectl label pod nginx-demo -n demo version=v1

# 按照标签查询
kubectl get pods -n demo -l version=v1
kubectl get pods -n demo -l 'environment in (production, staging)'
kubectl get pods -A -l 'tier=frontend'

# 删除标签（减号）
kubectl label pod nginx-demo -n demo version-
```

**Service 通过 Label Selector 绑定 Pod** — 这就是解耦：

```yaml
# Service 的 selector 匹配 Pod 的 labels
apiVersion: v1
kind: Service
metadata:
  name: demo-svc
spec:
  selector:
    app: nginx-demo    # ← 只选择有这个 label 的 Pod
  ports:
  - port: 80
```

### 4.4 常用操作

```bash
# 清理
kubectl delete namespace demo
# 注意：删除 Namespace 会删除其中的所有资源！
```

---

## 第5课 ConfigMap & Secret —— 配置管理

### 5.1 为什么需要它们？

微服务中，配置**不应该硬编码在镜像里**。原因：

1. 不同环境（dev/staging/prod）配置不同 → 不能每个环境打一个镜像
2. 配置变化不需要重新构建镜像
3. 敏感信息（密码/证书）需要**单独保护**

### 5.2 ConfigMap —— 非敏感配置

```yaml
# 05-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  APP_ENV: "production"
  LOG_LEVEL: "info"
  APP_PORT: "8080"
  nginx.conf: |
    server {
      listen 80;
      server_name example.com;
    }
```

```bash
kubectl apply -f 05-configmap.yaml

# 查看
kubectl get configmap app-config -o yaml
```

### 5.3 在 Pod 中使用 ConfigMap

**方式一：环境变量注入**

```yaml
# 05-pod-env.yaml
apiVersion: v1
kind: Pod
metadata:
  name: configmap-env-pod
spec:
  containers:
  - name: app
    image: nginx:1.25-alpine
    env:
    - name: APP_ENV
      valueFrom:
        configMapKeyRef:
          name: app-config
          key: APP_ENV
    - name: LOG_LEVEL
      valueFrom:
        configMapKeyRef:
          name: app-config
          key: LOG_LEVEL
```

**方式二：挂载为文件**

```yaml
# 05-pod-volume.yaml
apiVersion: v1
kind: Pod
metadata:
  name: configmap-volume-pod
spec:
  containers:
  - name: app
    image: nginx:1.25-alpine
    volumeMounts:
    - name: config
      mountPath: /etc/config
  volumes:
  - name: config
    configMap:
      name: app-config
```

```bash
# 验证
kubectl exec configmap-volume-pod -- ls /etc/config
kubectl exec configmap-volume-pod -- cat /etc/config/APP_ENV
# 输出: production
```

### 5.4 Secret —— 敏感配置

Secret 与 ConfigMap 用法几乎一样，但：

- 值用 **Base64 编码**（不是加密！仅是防意外泄露）
- 支持更细粒度的访问控制
- etcd 中建议加密存储（生产环境）

```yaml
# 05-secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: app-secret
type: Opaque
stringData:       # 直接用明文写，K8s 自动 base64 编码
  DB_PASSWORD: "MyPassw0rd!"
  API_KEY: "sk-xxxxx"
---
apiVersion: v1
kind: Pod
metadata:
  name: secret-pod
spec:
  containers:
  - name: app
    image: nginx:1.25-alpine
    env:
    - name: DB_PASSWORD
      valueFrom:
        secretKeyRef:
          name: app-secret
          key: DB_PASSWORD
```

```bash
kubectl apply -f 05-secret.yaml

# 查看（注意：值显示为 base64）
kubectl get secret app-secret -o yaml

# 解码查看
kubectl get secret app-secret -o jsonpath='{.data.DB_PASSWORD}' | base64 -d
```

### 5.5 ConfigMap 更新后的生效方式

| 注入方式 | 是否自动更新 | 说明 |
|---------|------------|------|
| 环境变量注入 | ❌ 不更新 | 需重建 Pod（滚动重启 Deployment） |
| 文件挂载 | ✅ 自动更新 | 但应用可能不热加载，需配合 reload 机制 |

```bash
# ConfigMap 更新后重启 Deployment（让环境变量生效）
kubectl rollout restart deployment my-app
```

### 5.6 生产建议

- **明文配置** → ConfigMap
- **密码/Token/证书** → Secret
- **不要**将 Secret 提交到 Git（用 SealedSecret 或 External Secrets Operator 管理）

---

## 第6课 健康检查 —— 自愈机制

### 6.1 三种探针

| 探针 | 作用 | 失败后果 |
|------|------|---------|
| **livenessProbe**（存活探针） | 检查容器是否存活 | 重启容器 |
| **readinessProbe**（就绪探针） | 检查容器是否可接受流量 | 从 Service 端点中移除 |
| **startupProbe**（启动探针） | 检查容器是否启动完成 | 延缓 liveness/readiness 检查 |

### 6.2 动手：添加健康检查

```yaml
# 06-healthcheck.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web-health
spec:
  replicas: 3
  selector:
    matchLabels:
      app: web
  template:
    metadata:
      labels:
        app: web
    spec:
      containers:
      - name: nginx
        image: nginx:1.25-alpine
        ports:
        - containerPort: 80
        livenessProbe:
          httpGet:
            path: /      # Nginx 返回 200
            port: 80
          initialDelaySeconds: 5   # 启动 5 秒后开始检查
          periodSeconds: 10        # 每 10 秒检查一次
          timeoutSeconds: 3        # 超时 3 秒
          failureThreshold: 3      # 连续失败 3 次重启
        readinessProbe:
          httpGet:
            path: /
            port: 80
          initialDelaySeconds: 3
          periodSeconds: 5
```

```bash
kubectl apply -f 06-healthcheck.yaml

# 查看 Pod 的 Ready 列
kubectl get pods -l app=web

# 注意：刚启动时可能 0/1（readiness 未通过），几秒后变 1/1
```

### 6.3 演示：探针生效

```bash
# 进入一个 Pod 把 Nginx 停掉
kubectl exec -it <pod-name> -- nginx -s stop

# 观察 —— livenessProbe 检测到服务挂了，重启容器
kubectl get pods -l app=web -w
# RESTARTS 列会 +1
```

### 6.4 exec 类型探针（适用于非 HTTP 应用）

```yaml
livenessProbe:
  exec:
    command:
    - cat
    - /tmp/healthy
  initialDelaySeconds: 5
  periodSeconds: 10
```

### 6.5 端口类型探针

```yaml
readinessProbe:
  tcpSocket:
    port: 3306
  initialDelaySeconds: 5
  periodSeconds: 10
```

---

## 第7课 资源管理 —— Requests & Limits

### 7.1 为什么需要资源限制？

没有资源限制的集群就像**没有交通规则的高速公路**：

- 一个 Pod 可以把节点 CPU 打满 → 其他 Pod 饥饿
- 一个 Pod 内存泄漏 → 整个节点 OOM（操作系统的 OOM Killer 可能杀掉其他进程）
- 调度器不知道 Pod 需要多少资源 → 把太多 Pod 放到同一个节点

### 7.2 Requests vs Limits

| 概念 | 含义 | 作用 |
|------|------|------|
| **requests**（请求量） | 容器**最低保障**的资源 | 调度依据——节点必须有这么多空闲资源才会调度过来 |
| **limits**（限制量） | 容器**最多能用**的资源 | 强制上限——超过 CPU limit 会被限流，超过内存 limit 会被 OOM Kill |

```yaml
# 07-resources.yaml
apiVersion: v1
kind: Pod
metadata:
  name: resource-demo
spec:
  containers:
  - name: app
    image: nginx:1.25-alpine
    resources:
      requests:
        cpu: "250m"        # 0.25 核
        memory: "128Mi"    # 128 MiB
      limits:
        cpu: "500m"        # 0.5 核（最多用到 0.5 核，超了限流）
        memory: "256Mi"    # 256 MiB（超了 OOM Kill）
```

### 7.3 单位说明

| 资源 | 常用单位 | 示例 |
|------|---------|------|
| CPU | m（millicore，千分之一核） | `500m` = 0.5 核，`1` = 1 核 |
| 内存 | Mi/Gi（二进制） | `256Mi`，`1Gi` |
| 内存 | M/G（十进制，少用） | `256M` = 256 × 1000 × 1000 字节 |

### 7.4 验证资源限制

```bash
kubectl apply -f 07-resources.yaml

# 查看 Pod 的资源分配
kubectl describe pod resource-demo | grep -A 5 -E "(Requests|Limits)"

# 检查节点资源使用
kubectl top nodes
# 需要先安装 metrics-server: kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

### 7.5 最佳实践

- **一定要设置 requests**，否则调度器无法合理调度
- **limits 建议 = requests 或略高**，避免"超卖"
- **内存超限会被 Kill**，比 CPU 限流更严重——设置内存 limits 时留余量
- **不要设置过低的 requests**，否则节点看起来资源充足但运行时性能差

### 7.6 Quality of Service 类

根据 requests 和 limits 的设置方式，K8s 给 Pod 划分三个 QoS 等级：

| QoS 等级 | 设置条件 | 节点资源紧张时 |
|---------|---------|--------------|
| **Guaranteed** | requests = limits | 最后被驱逐 |
| **Burstable** | requests < limits | 中等优先级 |
| **BestEffort** | 不设置 requests 和 limits | 最先被驱逐 |

```bash
# 查看 Pod 的 QoS 等级
kubectl describe pod resource-demo | grep QoS
```

---

## 第8课 滚动更新与回滚

### 8.1 概念

**滚动更新（Rolling Update）**：逐步替换旧版本 Pod，保持服务不中断。

```
更新前: [v1][v1][v1][v1][v1]
步骤1:  [v2][v1][v1][v1][v1]  ← 启动 1 个 v2，确认就绪后删除 1 个 v1
步骤2:  [v2][v2][v1][v1][v1]  ← 逐个替换
步骤3:  [v2][v2][v2][v1][v1]
步骤4:  [v2][v2][v2][v2][v1]
更新后: [v2][v2][v2][v2][v2]
```

### 8.2 动手：滚动更新

```yaml
# 08-rollout.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web-rollout
spec:
  replicas: 4
  selector:
    matchLabels:
      app: web
  minReadySeconds: 5           # 新 Pod 就绪后等待 5 秒才算成功
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1        # 更新中最多允许 1 个 Pod 不可用
      maxSurge: 1              # 更新中最多允许超出 1 个 Pod
  template:
    metadata:
      labels:
        app: web
    spec:
      containers:
      - name: nginx
        image: nginx:1.25-alpine
        ports:
        - containerPort: 80
        readinessProbe:
          httpGet:
            path: /
            port: 80
          initialDelaySeconds: 3
          periodSeconds: 3
```

```bash
kubectl apply -f 08-rollout.yaml

# 持续观察更新状态
kubectl get pods -l app=web -w
```

另一个终端执行更新：

```bash
# 更新镜像到新版本
kubectl set image deployment/web-rollout nginx=nginx:1.26-alpine

# 查看滚动更新状态
kubectl rollout status deployment/web-rollout

# 查看版本历史
kubectl rollout history deployment/web-rollout

# 查看 ReplicaSet（每个版本一个 RS）
kubectl get replicasets -l app=web
```

### 8.3 回滚

```bash
# 回滚到上一个版本
kubectl rollout undo deployment/web-rollout

# 回滚到指定版本
kubectl rollout undo deployment/web-rollout --to-revision=1

# 查看回滚过程
kubectl rollout status deployment/web-rollout
```

### 8.4 更新策略对比

| 策略 | 行为 | 适用场景 |
|------|------|---------|
| **RollingUpdate**（默认） | 逐步替换 | 线上服务，追求零停机 |
| **Recreate** | 先删所有旧 Pod，再创建新 Pod | 有状态应用、数据库迁移 |

```yaml
strategy:
  type: Recreate
```

### 8.5 暂停与继续（金丝雀发布）

```bash
# 更新镜像（先不自动继续）
kubectl set image deployment/web-rollout nginx=nginx:1.26-alpine

# 手动暂停 — 只更新了一部分 Pod
kubectl rollout pause deployment/web-rollout

# 观察 —— 部分 Pod 是 v1，部分是 v2
kubectl get pods -l app=web -o custom-columns=NAME:.metadata.name,IMAGE:.spec.containers[*].image

# 验证没问题后继续
kubectl rollout resume deployment/web-rollout
```

---

## 第9课 HPA —— 自动伸缩

### 9.1 概念

**HorizontalPodAutoscaler（HPA）** 根据 CPU/内存使用率自动调整副本数。

```
流量上升 → Pod CPU ↑ → HPA 检测到 → 增加副本数 → CPU 下降 → 稳定
流量下降 → Pod CPU ↓ → HPA 检测到 → 减少副本数 → 节约资源 → 稳定
```

### 9.2 前置条件：安装 metrics-server

```bash
# metrics-server 提供 kubectl top 数据，HPA 依赖它
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# 等待就绪
kubectl wait --for=condition=available --timeout=60s deployment/metrics-server -n kube-system

# 验证
kubectl top nodes
kubectl top pods
```

### 9.3 动手：创建 HPA

先创建一个带资源 requests 的 Deployment（HPA 需要 requests 做基准）：

```yaml
# 09-hpa-deploy.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web-hpa
spec:
  replicas: 1
  selector:
    matchLabels:
      app: web
  template:
    metadata:
      labels:
        app: web
    spec:
      containers:
      - name: nginx
        image: nginx:1.25-alpine
        resources:
          requests:
            cpu: "200m"
            memory: "128Mi"
          limits:
            cpu: "500m"
            memory: "256Mi"
```

```bash
kubectl apply -f 09-hpa-deploy.yaml
```

创建 HPA：

```bash
# 命令式：基于 CPU，目标 50%
kubectl autoscale deployment web-hpa --cpu-percent=50 --min=1 --max=10

# 或声明式
```

```yaml
# 09-hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: web-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: web-hpa
  minReplicas: 1
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 50
```

```bash
kubectl apply -f 09-hpa.yaml

# 查看 HPA
kubectl get hpa -w
```

### 9.4 模拟负载，触发扩容

```bash
# 开一个新终端监控 HPA
kubectl get hpa -w

# 主终端：生成 CPU 压力
kubectl run load-generator --rm -it --image=busybox -- /bin/sh

# 在 busybox 中反复请求你的 Nginx
while true; do wget -q -O- http://web-hpa-svc; done
```

观察 HPA 输出变化：

```
NAME      REFERENCE            TARGETS         MINPODS   MAXPODS   REPLICAS
web-hpa   Deployment/web-hpa   0%/50%          1         10        1
web-hpa   Deployment/web-hpa   150%/50%        1         10        4       ← 扩容
web-hpa   Deployment/web-hpa   45%/50%         1         10        4       ← 稳定
```

按下 Ctrl+C 停掉压力后，几分钟内会自动缩回 1 副本。

### 9.5 HPA 最佳实践

- **必须设置 Pod 的 requests**，HPA 以它为基准计算利用率
- **必须安装 metrics-server** 或 Prometheus Adapter
- 缩容有默认冷却期（`--horizontal-pod-autoscaler-downscale-stabilization`，默认 5 分钟），防止"抖动"
- 生产环境推荐用 **KEDA**（基于事件驱动的伸缩器，可基于消息队列长度、请求数等伸缩）

---

# 第二部分：微服务运维流程

## 第10课 服务发现 —— Pod 间如何找到对方

### 10.1 微服务通信的三个挑战

1. Pod IP 不固定（重启就变）
2. 副本数可变（扩缩容后数量变化）
3. 需要负载均衡

### 10.2 方案：Kubernetes DNS

K8s 自带 DNS（CoreDNS），每个 Service 自动注册 DNS 记录：

```
<service-name>.<namespace>.svc.cluster.local
```

### 10.3 动手演示

创建两个 Deployment + 对应的 Service：

```yaml
# 10-frontend.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: frontend
  template:
    metadata:
      labels:
        app: frontend
    spec:
      containers:
      - name: app
        image: nginx:1.25-alpine
---
apiVersion: v1
kind: Service
metadata:
  name: frontend-svc
spec:
  selector:
    app: frontend
  ports:
  - port: 80
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: backend
  template:
    metadata:
      labels:
        app: backend
    spec:
      containers:
      - name: app
        image: nginx:1.25-alpine
---
apiVersion: v1
kind: Service
metadata:
  name: backend-svc
spec:
  selector:
    app: backend
  ports:
  - port: 80
```

```bash
kubectl apply -f 10-frontend.yaml
```

前端访问后端的服务发现验证：

```bash
# 进入前端 Pod
kubectl exec -it <frontend-pod-name> -- /bin/sh

# 在 Pod 内（Nginx Alpine shell）
nslookup backend-svc
# 输出: Name: backend-svc.default.svc.cluster.local
#       Address: 10.96.xx.xx

# 访问后端服务（用 Service 名称而不是 IP）
wget -q -O- http://backend-svc
```

**这就是微服务服务发现的核心**：代码里写 `http://backend-svc/api/xxx`，不需要知道具体 IP。

### 10.4 跨 Namespace 访问

```bash
# 如果我方在 namespace-a，后端在 namespace-b
# 用完整 DNS 名称
http://backend-svc.namespace-b.svc.cluster.local
```

### 10.5 清理

```bash
kubectl delete deployment frontend backend
kubectl delete svc frontend-svc backend-svc
```

---

## 第11课 Ingress —— 统一网关入口

### 11.1 为什么需要 Ingress？

有了 Service，你的微服务已经可以在集群内互相调用了。但**对外暴露**仍有问题：

- NodePort 端口范围有限（30000-32767），不优雅
- 每个 Service 一个端口，客户端记不住
- 没有 TLS 终止、路径路由、虚拟主机等功能

**Ingress** 解决的问题：

```
客户端 (http://app.example.com)
          │
          ▼
    ┌────────────┐
    │  Ingress   │  ← 统一的入口网关，按域名/路径分发
    └─────┬──────┘
           │
    ┌──────┴──────┐
    ▼             ▼
 Service-A     Service-B
    │             │
    ▼             ▼
  Pod-A-1       Pod-B-1
  Pod-A-2       Pod-B-2
```

### 11.2 前提：安装 Ingress Controller

Ingress 只是一个 API 资源，需要 **Ingress Controller** 才能工作（类似 Service 需要 kube-proxy）：

```bash
# 安装 Nginx Ingress Controller
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.9.4/deploy/static/provider/baremetal/deploy.yaml

# 等待
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s

# 检查
kubectl get pods -n ingress-nginx
```

### 11.3 动手：创建 Ingress

部署两个微服务：

```yaml
# 11-services.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: svc-a
spec:
  replicas: 2
  selector:
    matchLabels:
      app: svc-a
  template:
    metadata:
      labels:
        app: svc-a
    spec:
      containers:
      - name: nginx
        image: nginx:1.25-alpine
---
apiVersion: v1
kind: Service
metadata:
  name: svc-a
spec:
  selector:
    app: svc-a
  ports:
  - port: 80
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: svc-b
spec:
  replicas: 2
  selector:
    matchLabels:
      app: svc-b
  template:
    metadata:
      labels:
        app: svc-b
    spec:
      containers:
      - name: nginx
        image: nginx:1.25-alpine
---
apiVersion: v1
kind: Service
metadata:
  name: svc-b
spec:
  selector:
    app: svc-b
  ports:
  - port: 80
```

```bash
kubectl apply -f 11-services.yaml
```

创建 Ingress：

```yaml
# 11-ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: example-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /   # 路径重写
spec:
  rules:
  - host: demo.example.com
    http:
      paths:
      - path: /api/v1
        pathType: Prefix
        backend:
          service:
            name: svc-a
            port:
              number: 80
      - path: /api/v2
        pathType: Prefix
        backend:
          service:
            name: svc-b
            port:
              number: 80
```

```bash
kubectl apply -f 11-ingress.yaml

# 查看
kubectl get ingress
```

### 11.4 测试

```bash
# 添加 hosts 解析（本机测试用）
echo "192.168.3.100 demo.example.com" | sudo tee -a /etc/hosts

# 测试（注意找 ingress-nginx 的 NodePort 端口）
kubectl get svc -n ingress-nginx
# 通常: ingress-nginx-controller   NodePort   10.96.x.x   <none>   80:xxxxx/TCP,443:xxxxx/TCP

curl -H "Host: demo.example.com" http://192.168.3.100:<NodePort>/api/v1
curl -H "Host: demo.example.com" http://192.168.3.100:<NodePort>/api/v2
```

### 11.5 Ingress 的核心价值

| 能力 | 说明 |
|------|------|
| **域名路由** | 不同域名转发到不同 Service |
| **路径路由** | 同一个域名按路径拆分：`/api` → A 服务，`/web` → B 服务 |
| **TLS 终止** | Ingress 处理 HTTPS 证书，后端 Service 走 HTTP |
| **速率限制** | annotations 配置限流 |
| **灰度发布** | 通过 annotation 实现 canary 发布 |

### 11.6 清理

```bash
kubectl delete -f 11-ingress.yaml
kubectl delete -f 11-services.yaml
```

---

## 第12课 存储 —— PV & PVC

### 12.1 问题

默认情况下，Pod 内的文件在容器重启后**丢失**。有状态应用（数据库、消息队列、文件存储）需要**持久化存储**。

### 12.2 概念分层

```
┌─────────────────────────────────────┐
│  Pod                                │
│  ┌──────────────────────────────┐   │
│  │  容器                        │   │
│  │  volumeMounts: /data         │   │
│  └──────────┬───────────────────┘   │
│             │                       │
│  ┌──────────▼───────────┐           │
│  │  PVC (PersistentVolumeClaim) │   │  ← 用户：我要多少存储
│  └──────────┬───────────┘           │
└─────────────┼───────────────────────┘
              │
┌─────────────▼───────────────────┐
│  PV (PersistentVolume)          │  ← 管理员：提供存储
│  类型: hostPath / NFS / Ceph    │
│  容量: 10Gi                     │
│  访问模式: ReadWriteOnce        │
└─────────────────────────────────┘
```

| 组件 | 谁管 | 说明 |
|------|------|------|
| **PV**（PersistentVolume） | 集群管理员 | 底层存储的抽象（NFS / Ceph / 云硬盘） |
| **PVC**（PersistentVolumeClaim） | 应用开发者 | 申请存储资源（我要 5Gi，读写模式） |
| **StorageClass** | 集群管理员 | 动态供给模板（PVC 自动创建 PV） |

### 12.3 静态供给（手动创建 PV）

```yaml
# 12-pv-hostpath.yaml（测试用，生产用 NFS/Ceph）
apiVersion: v1
kind: PersistentVolume
metadata:
  name: pv-local
spec:
  capacity:
    storage: 5Gi
  accessModes:
  - ReadWriteOnce
  hostPath:
    path: /data/k8s-pv
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: pvc-demo
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
---
apiVersion: v1
kind: Pod
metadata:
  name: pv-pod
spec:
  volumes:
  - name: data
    persistentVolumeClaim:
      claimName: pvc-demo
  containers:
  - name: app
    image: nginx:1.25-alpine
    volumeMounts:
    - name: data
      mountPath: /usr/share/nginx/html
```

```bash
# 在节点上先创建目录
kubectl get nodes -o wide  # 看哪个节点
# 假设 Pod 调度到 k8s-node1
ssh hulei@192.168.3.101 "sudo mkdir -p /data/k8s-pv && echo 'Hello from PV!' | sudo tee /data/k8s-pv/index.html"

kubectl apply -f 12-pv-hostpath.yaml

# 验证
kubectl get pv
kubectl get pvc
curl http://<pv-pod-ip>
# 输出: Hello from PV!
```

### 12.4 动态供给（推荐生产用）

需要 **StorageClass**：

```bash
# 查看集群中的 StorageClass
kubectl get storageclass

# 如果没有，需要安装一个（如 NFS CSI Driver 或云厂商的云盘驱动）
# 有 StorageClass 后，PVC 创建时自动创建 PV
```

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: dynamic-pvc
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
  storageClassName: standard    # ← 动态供给
```

### 12.5 常见存储场景

| 应用类型 | 推荐存储 | 说明 |
|---------|---------|------|
| 无状态（Web API） | 不需要持久卷 | 重启即恢复 |
| 有状态（MySQL/Redis） | PVC + StatefulSet | 每个 Pod 独立存储 |
| 文件存储（MinIO/NextCloud） | PVC + 分布式存储 | Ceph/NFS/Longhorn |
| 配置文件 | ConfigMap | 无需持久卷 |

### 12.6 清理

```bash
kubectl delete pod pv-pod
kubectl delete pvc pvc-demo
kubectl delete pv pv-local
```

---

## 第13课 网络策略 NetworkPolicy

### 13.1 概念

默认：K8s 集群中所有 Pod 之间可以自由通信（扁平网络）。

**NetworkPolicy** 是 K8s 的**防火墙**，基于标签选择器控制 Pod 间流量。

### 13.2 前置条件

Calico 已安装 NetworkPolicy 支持，直接可用：

```bash
# 确认 Calico 支持 NetworkPolicy
kubectl get clusterinformation -o yaml | grep -i policy
```

### 13.3 动手：隔离数据库

场景模拟：frontend 可以访问 backend，但 frontend **不能**直接访问 database。

```yaml
# 13-pods.yaml
apiVersion: v1
kind: Pod
metadata:
  name: frontend
  labels:
    role: frontend
spec:
  containers:
  - name: app
    image: nginx:1.25-alpine
---
apiVersion: v1
kind: Pod
metadata:
  name: database
  labels:
    role: database
spec:
  containers:
  - name: app
    image: nginx:1.25-alpine
```

```bash
kubectl apply -f 13-pods.yaml

# 默认：frontend 和 database 互通
kubectl exec frontend -- wget -q -O- http://<database-pod-ip>
```

### 13.4 创建网络策略

```yaml
# 13-networkpolicy.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: db-policy
spec:
  podSelector:
    matchLabels:
      role: database
  policyTypes:
  - Ingress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          role: backend      # 只有 role=backend 的 Pod 可以访问
    ports:
    - port: 80
```

```bash
kubectl apply -f 13-networkpolicy.yaml

# 测试：frontend 不能再访问 database
kubectl exec frontend -- wget -q -O- --timeout=5 http://<database-pod-ip>
# 结果: 超时！NetworkPolicy 阻断了流量

# 创建一个 backend Pod 就可以访问
kubectl run backend --image=nginx:1.25-alpine -l role=backend
kubectl exec backend -- wget -q -O- http://<database-pod-ip>
# 结果: 正常访问
```

### 13.5 常用策略模式

```yaml
# 拒绝所有入站
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: deny-all
spec:
  podSelector: {}
  policyTypes:
  - Ingress
```

```yaml
# 只允许特定命名空间访问
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: namespace-allow
spec:
  podSelector:
    matchLabels:
      app: api
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: frontend-ns
```

### 13.6 清理

```bash
kubectl delete networkpolicy db-policy
kubectl delete pod frontend database backend
```

---

## 第14课 监控与日志

### 14.1 监控架构（Prometheus + Grafana）

```
Pod/Node (暴露 /metrics)
      │
      ▼
Prometheus (抓取指标)
      │
      ▼
Grafana (可视化面板)
     ┌─── 集群资源 (CPU/内存/网络)
     ├─── Pod 资源
     ├─── 应用指标 (请求量/延迟/错误率)
     └─── 告警规则
```

### 14.2 安装 Prometheus Stack（一键部署）

```bash
# 用 helm 安装（如未安装 helm，先装）
# curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# 安装 kube-prometheus-stack（包含 Prometheus + Grafana + AlertManager）
helm install monitoring prometheus-community/kube-prometheus-stack --namespace monitoring --create-namespace

# 等待就绪
kubectl wait --namespace monitoring \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/name=grafana \
  --timeout=120s
```

### 14.3 访问 Grafana

```bash
# 方式一：端口转发
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80

# 浏览器访问: http://localhost:3000
# 默认账号: admin / prom-operator
```

Grafana 中预置了 **Kubernetes 集群监控** 仪表盘，可直接查看：

- 节点 CPU/内存/磁盘/网络
- Pod 资源使用排行
- 集群组件状态

### 14.4 日志

```bash
# 方式一：kubectl logs（临时排查）
kubectl logs <pod-name>
kubectl logs -f <pod-name>              # 实时流式输出
kubectl logs <pod-name> --previous      # 看上一次崩溃的容器日志
kubectl logs -l app=nginx --tail=100    # 按标签查询
```

**生产环境的日志架构**（EFK / Loki Stack）：

```
Pod (stdout/stderr)
    │
docker/containerd (日志文件)
    │
    ▼
Fluentbit / Filebeat (采集 + 转发)
    │
    ├── Elasticsearch ← Kibana (可视化)
    └── Loki ← Grafana (轻量方案)
```

### 14.5 安装 Loki（轻量日志方案）

```bash
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

helm install loki grafana/loki-stack --namespace logging --create-namespace

# 在 Grafana 中添加 Loki 数据源: http://loki:3100
# 即可通过 LogQL 查询日志
```

### 14.6 排查三步法

当线上故障时，按这个顺序排查：

```bash
# 第1步：看基础状态
kubectl get pods -A | grep -v Running   # 找异常状态
kubectl get events --sort-by='.lastTimestamp' | tail -20  # 最近事件

# 第2步：看详情
kubectl describe pod <problem-pod>
# → Conditions/Life events/容器状态/挂载日志

# 第3步：看日志
kubectl logs <problem-pod> --tail=100
kubectl logs <problem-pod> --previous   # 如果容器在 CrashLoopBackOff
```

---

## 第15课 CI/CD 集成思路

### 15.1 传统部署 vs K8s 部署

```
传统部署:       编译 → 上传 → 登录服务器 → 停服 → 替换 → 重启
K8s 部署:       编译 → 构建镜像 → push 镜像仓库 → kubectl set image
```

### 15.2 标准 K8s CI/CD 流程

```
开发者 Push 代码
      │
      ▼
CI (GitHub Actions / GitLab CI / Jenkins)
  ├── 1. 单元测试
  ├── 2. 构建 Docker 镜像
  ├── 3. Push 到镜像仓库
  └── 4. 更新 K8s YAML 中的镜像 Tag
      │
      ▼
CD (ArgoCD / Flux)
  └── 自动同步到 K8s 集群
      │
      ▼
K8s 集群 (滚动更新)
```

### 15.3 GitHub Actions 示例

```yaml
# .github/workflows/deploy.yaml
name: Deploy to K8s

on:
  push:
    branches: [main]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4

    - name: Build Docker image
      run: |
        docker build -t my-registry/my-app:${{ github.sha }} .
        docker push my-registry/my-app:${{ github.sha }}

    - name: Deploy to K8s
      run: |
        # 更新 Deployment 的镜像版本，触发滚动更新
        kubectl set image deployment/my-app \
          app=my-registry/my-app:${{ github.sha }} \
          --kubeconfig=${{ secrets.KUBECONFIG }}
```

### 15.4 金丝雀 / 蓝绿部署（生产用）

```yaml
# 金丝雀发布：先给 10% 流量跑新版本
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  annotations:
    nginx.ingress.kubernetes.io/canary: "true"           # 启用金丝雀
    nginx.ingress.kubernetes.io/canary-weight: "10"       # 10% 流量
spec:
  rules:
  - host: app.example.com
    http:
      paths:
      - backend:
          service:
            name: my-app-canary    # 金丝雀版本
            port: 80
```

```
验证金丝雀 10% 流量 OK 后 → 权重改为 100% → 删除旧版本 → 完成发布
```

### 15.5 GitOps 模式（推荐）

**GitOps** = Git 仓库作为集群状态的"唯一真相源"

```
开发者改 YAML → Push 到 Git
       │
       ▼
ArgoCD / Flux (在 K8s 集群中运行)
       │
       ▼
自动同步到集群（Git 中有啥，集群里有啥）
```

优势：
- 所有变更都有 Git 历史可追溯
- 集群出问题，回滚 Git 即可
- 审计友好（谁在什么时候改了啥）

---

## 第16课 生产最佳实践 Checklist

### 16.1 集群层面

- [ ] **高可用控制平面**：至少 3 Master，用负载均衡器分发 API Server 请求
- [ ] **etcd 备份**：定期备份 `/var/lib/etcd`（备份脚本 + 异地存储）
- [ ] **节点资源预留**：system-reserved / kube-reserved 保留系统资源
- [ ] **RBAC 权限管控**：不要用 cluster-admin 的 token，按需分配角色
- [ ] **网络策略**：默认 deny-all + 只开必要端口

### 16.2 应用层面

- [ ] **资源限制**：每个容器都有 requests + limits（防止"吵闹的邻居"）
- [ ] **健康检查**：livenessProbe + readinessProbe（实现自愈）
- [ ] **优雅关闭**：`preStop` hook + `terminationGracePeriodSeconds`
- [ ] **Pod 反亲和**：避免同一 Deployment 的 Pod 放在同一节点

```yaml
# Pod 反亲和示例
affinity:
  podAntiAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
    - weight: 100
      podAffinityTerm:
        labelSelector:
          matchLabels:
            app: my-app
        topologyKey: kubernetes.io/hostname
```

- [ ] **配置与代码分离**：ConfigMap / Secret，不硬编码
- [ ] **只读根文件系统**：`readOnlyRootFilesystem: true`（安全加固）
- [ ] **PDB（PodDisruptionBudget）**：保证最少可用副本

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: my-app-pdb
spec:
  minAvailable: 2
  selector:
    matchLabels:
      app: my-app
```

### 16.3 运维流程

- [ ] **滚动更新策略**：设置 maxUnavailable + maxSurge，避免全停
- [ ] **HPA 自动伸缩**：配置最小/最大副本数 + 触发阈值
- [ ] **监控告警**：集群资源 / Pod 状态 / 应用黄金指标（延迟/流量/错误/饱和度）
- [ ] **日志归档**：按天/按 GB 轮转，长期存储到对象存储
- [ ] **镜像版本策略**：不用 `:latest`，用语义版本或 Git SHA
- [ ] **定期演练**：节点故障 / 网络分区 / 流量突增 的场景演练

### 16.4 常见陷阱

| 陷阱 | 表现 | 解决 |
|------|------|------|
| 镜像拉取策略 `:latest` | 集群中 Pod 版本不一致 | 指定版本号 + `imagePullPolicy: Always` |
| 没有设置 resources | 一个 Pod 打满全节点 | 每个容器必须设置 requests |
| ConfigMap 更新不生效 | 改了配置但 Pod 仍是旧值 | 环境变量方式需要重启 Pod |
| Service 端口不匹配 | 访问不通 | 检查 port / targetPort / containerPort 链 |
| 忘记容忍 Taint | Pod 一直 Pending | `kubectl describe pod` 查看事件 |
| PVC 未绑定 | Pod 一直 Pending | `kubectl get pvc` 检查 STATUS |
| HPA/监控无数据 | kubectl top 报错 | 检查 metrics-server 是否正常运行 |
| 日志不出现 | kubectl logs 无输出 | 确认应用写 stdout/stderr，不是文件 |

---

## 结语

通过这 16 课的学习，你已经在你的真实集群上：

- ✅ 掌握了 **Pod → Deployment → Service → Ingress** 的完整链路
- ✅ 掌握了 **ConfigMap/Secret** 配置管理方法
- ✅ 实现了 **健康检查 + 自愈 + 滚动更新**
- ✅ 了解了 **HPA 自动伸缩** 的工作机制
- ✅ 理解了 **服务发现** 和 **微服务间通信** 原理
- ✅ 了解了 **存储、网络策略、监控日志** 等运维体系
- ✅ 熟悉了 **CI/CD / GitOps** 等生产交付模式

下一步你可以：
1. 在集群上部署一个完整的微服务 Demo（如 3-Tier App）
2. 尝试用 Helm 管理应用包
3. 尝试用 ArgoCD 实现 GitOps
4. 研究 Service Mesh（Istio）进阶

---

> 📁 本教程配套 YAML 文件位于同目录，文件名前缀与课号对应
> 🐞 遇到报错时先用 `kubectl describe` 和 `kubectl logs` 排查
