# 本地 Docker Registry 搭建

## 作用

镜像构建后推到本地 Registry，所有 K8s 节点自动拉取，不用手动 scp 镜像到每台机器。

---

## 搭建

```bash
# 1. k8s-master 上一键搭建
sudo bash setup-registry.sh

# 2. k8s-node1 和 k8s-node2 上分别执行
sudo tee -a /etc/containerd/config.toml <<'EOF'

[plugins."io.containerd.grpc.v1.cri".registry.mirrors."192.168.3.100:5000"]
  endpoint = ["http://192.168.3.100:5000"]
[plugins."io.containerd.grpc.v1.cri".registry.configs."192.168.3.100:5000".tls]
  insecure_skip_verify = true
EOF

sudo systemctl restart containerd

# 3. Windows Docker Desktop → Settings → Docker Engine 加:
#    { "insecure-registries": ["192.168.3.100:5000"] }
#    → Apply & Restart
```

---

## 日常使用

```powershell
# Windows 上
docker build -t 192.168.3.100:5000/poker-tracker:1.0.0 .
docker push 192.168.3.100:5000/poker-tracker:1.0.0
```

K8s YAML 里镜像名：

```yaml
image: 192.168.3.100:5000/poker-tracker:1.0.0
imagePullPolicy: Always
```

---

## 查看 Registry 里有啥

```bash
curl http://192.168.3.100:5000/v2/_catalog
curl http://192.168.3.100:5000/v2/poker-tracker/tags/list
```
