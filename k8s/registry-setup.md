# 本地 Docker Registry 搭建教程

## 一、为什么需要本地 Registry

你之前的流程是：镜像构建 → `docker save` → scp 到 master → `ctr import`。Pod 调度到 node1/node2 还得再发一遍——**每次部署都要手动分发镜像到 3 台机器**。

有了本地 Registry 后：

```
之前（手动分发）:
  Windows build → docker save → scp × 3 → 每个节点手动 import

之后（本地 Registry）:
  Windows build → docker push 192.168.3.100:5000 → kubectl apply
                                                          │
                                          所有节点自动从 192.168.3.100:5000 拉取
```

---

## 二、架构

```
┌─────────────────┐
│  Windows (开发)  │
│  docker build    │
│  docker push ────┼──────────────────────────────┐
└─────────────────┘                              │
                                                 ▼
┌──────────────────────────────────────────────────────────┐
│  k8s-master (192.168.3.100)                               │
│  ┌──────────────────────────────────────────────────┐    │
│  │  Registry 容器 (端口 5000)                         │    │
│  │  docker run -d -p 5000:5000 registry:2            │    │
│  │  镜像存在 /root/registry-data/                     │    │
│  └──────────────────────────────────────────────────┘    │
│                          │                                │
│          containerd 配了 http://192.168.3.100:5000        │
└──────────────────────────────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
    ┌──────────┐    ┌──────────┐    ┌──────────┐
    │ k8s-node1│    │ k8s-node2│    │ k8s-master│
    │ containerd    │ containerd    │ containerd
    │ 自动拉取镜像  │ 自动拉取镜像  │ 自动拉取镜像
    └──────────┘    └──────────┘    └──────────┘
```

---

## 三、搭建步骤

### 3.1 在 k8s-master 上启动 Registry

```bash
sudo docker run -d \
    --name registry \
    --restart=always \
    -p 5000:5000 \
    -v /root/registry-data:/var/lib/registry \
    registry:2

# 验证
curl http://192.168.3.100:5000/v2/
# → {}  表示 Registry 正常运行
```

### 3.2 配置所有节点的 containerd 信任 HTTP Registry

Registry 默认只支持 HTTPS，但内网直接用 HTTP 最简单。需要让三台机器的 containerd 把这个 registry 当白名单。

**k8s-master / k8s-node1 / k8s-node2 都执行：**

```bash
sudo tee -a /etc/containerd/config.toml <<'EOF'

# Local registry (HTTP, no TLS)
[plugins."io.containerd.grpc.v1.cri".registry.mirrors."192.168.3.100:5000"]
  endpoint = ["http://192.168.3.100:5000"]
[plugins."io.containerd.grpc.v1.cri".registry.configs."192.168.3.100:5000".tls]
  insecure_skip_verify = true
EOF

sudo systemctl restart containerd
```

### 3.3 配置 Windows Docker Desktop 信任 HTTP Registry

Docker Desktop → Settings → Docker Engine，在 JSON 里加：

```json
{
  "insecure-registries": ["192.168.3.100:5000"]
}
```

Apply & Restart。

---

## 四、一键脚本

```bash
sudo bash setup-registry.sh
```

这个脚本会自动完成上面所有步骤（包括远程配置 worker 节点）。

---

## 五、日常使用

### 5.1 构建 + 推送

```powershell
# Windows 上
cd d:\project\poker

# 直接打上 registry tag
docker build -t 192.168.3.100:5000/poker-tracker:1.0.0 .

# 推送
docker push 192.168.3.100:5000/poker-tracker:1.0.0
```

### 5.2 K8s YAML 改动

```yaml
# 之前（本地镜像）
image: poker-tracker:1.0.0
imagePullPolicy: Never

# 之后（从 registry 拉取）
image: 192.168.3.100:5000/poker-tracker:1.0.0
imagePullPolicy: Always        # 或者 IfNotPresent
```

### 5.3 部署

```bash
kubectl apply -f ~/poker-k8s/all.yaml
# Pod 落到哪个节点，containerd 就自动从 192.168.3.100:5000 拉取
```

### 5.4 更新版本

```powershell
docker build -t 192.168.3.100:5000/poker-tracker:1.0.1 .
docker push 192.168.3.100:5000/poker-tracker:1.0.1
```

```bash
kubectl set image deployment/poker-tracker poker-tracker=192.168.3.100:5000/poker-tracker:1.0.1
```

### 5.5 查看 Registry 里的镜像

```bash
curl http://192.168.3.100:5000/v2/_catalog
# → {"repositories":["poker-tracker"]}

curl http://192.168.3.100:5000/v2/poker-tracker/tags/list
# → {"name":"poker-tracker","tags":["1.0.0","1.0.1"]}
```

---

## 六、数据持久化

Registry 数据存在 `/root/registry-data/`，用的是 Docker volume 挂载。

> 如果空间不够了，Registry 默认不自动删除旧镜像。清理：
> ```bash
> docker exec registry bin/registry garbage-collect /etc/docker/registry/config.yml
> ```

---

## 七、总结：新旧流程对比

```
旧流程:
  build → save → scp × 3 → ctr import × 3 → kubectl apply
  (5 步，每次部署都要手动分发)

新流程:
  build → push → kubectl apply
  (3 步，分发全自动)
```
