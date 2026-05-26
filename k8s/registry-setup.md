# 本地 Docker Registry 搭建

## 作用

镜像构建后推到本地 Registry，所有 K8s 节点自动拉取，不用手动 scp 镜像到每台机器。

---

## 1. k8s-master 上启动 Registry

```bash
docker run -d --name registry --restart=always \
  -p 5000:5000 -v /root/registry-data:/var/lib/registry \
  registry:2

# 验证
curl http://192.168.3.100:5000/v2/
# → {}  表示正常
```

---

## 2. 配置所有节点的 containerd

**k8s-master / k8s-node1 / k8s-node2 都执行：**

```bash
# 把 registry 镜像地址嵌到已有的 registry.mirrors 段里面
sudo sed -i '/\[plugins\."io\.containerd\.grpc\.v1\.cri"\.registry\.mirrors\]/a\
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors."192.168.3.100:5000"]\
    endpoint = ["http://192.168.3.100:5000"]\
  [plugins."io.containerd.grpc.v1.cri".registry.configs."192.168.3.100:5000".tls]\
    insecure_skip_verify = true' /etc/containerd/config.toml

sudo systemctl restart containerd
```

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
curl http://192.168.3.100:5000/v2/_catalog
curl http://192.168.3.100:5000/v2/poker-tracker/tags/list
```
