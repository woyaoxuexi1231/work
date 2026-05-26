# 本地 Docker Registry 搭建

## 架构

```
192.168.3.100 (Docker 主机)
  └── Registry :5000         ← 镜像仓库，运行在这台机器上

192.168.3.90  k8s-master     ← 只跑 K8s，containerd 从 Registry 拉镜像
192.168.3.101 k8s-node1
192.168.3.102 k8s-node2
```

---

## 1. 在 Docker 主机上启动 Registry

```bash
# 192.168.3.100 上执行
docker run -d --name registry --restart=always \
  -p 5000:5000 -v /root/registry-data:/var/lib/registry \
  registry:2

# 验证
curl http://192.168.3.100:5000/v2/
# → {}  表示正常
```

---

## 2. 配置所有 K8s 节点

**192.168.3.90 / 192.168.3.101 / 192.168.3.102 都执行：**

```bash
sudo sed -i '/\[plugins\."io\.containerd\.grpc\.v1\.cri"\.registry\.mirrors\]/a\
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors."192.168.3.100:5000"]\
    endpoint = ["http://192.168.3.100:5000"]\
  [plugins."io.containerd.grpc.v1.cri".registry.configs."192.168.3.100:5000".tls]\
    insecure_skip_verify = true' /etc/containerd/config.toml

sudo systemctl restart containerd
```

> 注意：K8s 节点不需要装 Docker，containerd 本身就认识 registry 地址。

---

## 3. 配置 Windows Docker Desktop

Settings → Docker Engine → 加：

```json
{
  "insecure-registries": ["192.168.3.100:5000"]
}
```

Apply & Restart。

---

## 日常使用

```powershell
# Windows 上构建 + 推送
docker build -t 192.168.3.100:5000/poker-tracker:1.0.0 .
docker push 192.168.3.100:5000/poker-tracker:1.0.0
```

K8s YAML 里镜像名：

```yaml
image: 192.168.3.100:5000/poker-tracker:1.0.0
imagePullPolicy: Always
```

---

## 查看 Registry

```bash
# 在 192.168.3.100 上
curl http://127.0.0.1:5000/v2/_catalog
curl http://127.0.0.1:5000/v2/poker-tracker/tags/list
```
