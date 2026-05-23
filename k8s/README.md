# Ubuntu 20.04/22.04 搭建 Kubernetes 1.28+ 集群完整教程

> 本教程详细介绍如何在 Ubuntu 系统上搭建生产级别的 Kubernetes 集群

---

## 🚀 快速开始（推荐使用自动化脚本）

### 1. 所有节点执行公共环境准备

```bash
# 在 k8s-master (192.168.2.102) / k8s-node1 (192.168.2.59) / k8s-node2 (192.168.2.60) 各自执行
sudo bash setup-common.sh
```

按提示输入主机名（`k8s-master` / `k8s-node1` / `k8s-node2`）

### 2. Master 节点初始化集群

```bash
# 仅在 k8s-master (192.168.2.102) 执行
sudo bash setup-master.sh
```

### 3. Worker 节点加入集群

```bash
# 在 k8s-node1 (192.168.2.59) 和 k8s-node2 (192.168.2.60) 上执行
sudo bash setup-node.sh
```

（如果 Master 配置了 SSH，脚本会自动获取 join 命令；否则手动复制粘贴）

### 4. 验证集群

```bash
# 在 Master 节点执行
kubectl get nodes -o wide
```

---

## 📋 目录

1. [环境准备](#1-环境准备)
2. [系统配置](#2-系统配置)
3. [安装容器运行时](#3-安装容器运行时)
4. [安装 Kubernetes 组件](#4-安装-kubernetes-组件)
5. [初始化 Master 节点](#5-初始化-master-节点)
6. [安装网络插件](#6-安装网络插件)
7. [加入 Worker 节点](#7-加入-worker-节点)
8. [部署示例应用](#8-部署示例应用)
9. [常用命令](#9-常用命令)
10. [故障排除](#10-故障排除)

---

## 1. 环境准备

### 1.1 集群规划

| 角色 | 主机名 | IP 地址 | 配置 |
|------|--------|---------|------|
| Master | k8s-master | 192.168.2.102 | 2核4G+ |
| Worker1 | k8s-node1 | 192.168.2.59 | 2核4G+ |
| Worker2 | k8s-node2 | 192.168.2.60 | 2核4G+ |

### 1.2 基本要求

- Ubuntu 20.04 LTS 或 22.04 LTS
- 2核CPU及以上
- 2GB内存及以上
- 30GB磁盘空间及以上
- 网络互通
- root 权限

### 1.3 设置主机名（每台机器执行）

```bash
# Master 节点
sudo hostnamectl set-hostname k8s-master

# Worker1 节点
sudo hostnamectl set-hostname k8s-node1

# Worker2 节点
sudo hostnamectl set-hostname k8s-node2
```

### 1.4 配置 hosts 文件

```bash
sudo tee /etc/hosts <<EOF
192.168.2.102 k8s-master
192.168.2.59  k8s-node1
192.168.2.60  k8s-node2
EOF
```

---

## 2. 系统配置

### 2.1 关闭 Swap

```bash
# 临时关闭
sudo swapoff -a

# 永久关闭（注释掉 swap 行）
sudo sed -i '/ swap / s/^\(.*\)$/#\1/' /etc/fstab
```

### 2.2 加载内核模块

```bash
cat <<EOF | sudo tee /etc/modules-load.d/k8s.conf
overlay
br_netfilter
EOF

sudo modprobe overlay
sudo modprobe br_netfilter
```

### 2.3 配置内核参数

```bash
cat <<EOF | sudo tee /etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF

sudo sysctl --system
```

### 2.4 验证配置

```bash
# 检查内核模块
lsmod | grep br_netfilter
lsmod | grep overlay

# 检查网络参数
sysctl net.bridge.bridge-nf-call-iptables net.bridge.bridge-nf-call-ip6tables net.ipv4.ip_forward
```

### 2.5 安装并配置 iptables

```bash
sudo apt update && sudo apt install -y iptables arptables ebtables

# 设置 iptables 默认策略为 ACCEPT
sudo iptables -P FORWARD ACCEPT
```

---

## 3. 安装容器运行时

### 3.1 安装 containerd

```bash
# 安装依赖
sudo apt update
sudo apt install -y apt-transport-https ca-certificates curl gnupg lsb-release

# 添加 Docker GPG 密钥
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

# 添加 Docker 仓库
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 安装 containerd
sudo apt update
sudo apt install -y containerd.io

# 配置 containerd
sudo mkdir -p /etc/containerd
containerd config default | sudo tee /etc/containerd/config.toml

# 修改配置文件，将 SystemdCgroup 设置为 true
sudo sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml

# 重启 containerd
sudo systemctl restart containerd
sudo systemctl enable containerd

# 验证
sudo systemctl status containerd
```

### 3.2 （可选）安装 Docker

```bash
# 安装 Docker
sudo apt install -y docker.io docker-compose

# 配置 Docker 使用 systemd
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<EOF
{
  "exec-opts": ["native.cgroupdriver=systemd"],
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m"
  },
  "storage-driver": "overlay2"
}
EOF

sudo systemctl restart docker
sudo systemctl enable docker
```

---

## 4. 安装 Kubernetes 组件

### 4.1 添加 Kubernetes 仓库

```bash
# 添加 Google GPG 密钥
curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo gpg --dearmor -o /etc/apt/keyrings/kubernetes-archive-keyring.gpg

# 添加 Kubernetes 仓库
echo "deb [signed-by=/etc/apt/keyrings/kubernetes-archive-keyring.gpg] https://apt.kubernetes.io/ kubernetes-xenial main" | sudo tee /etc/apt/sources.list.d/kubernetes.list

# 如果遇到问题，可使用阿里云镜像
# echo "deb https://mirrors.aliyun.com/kubernetes/apt kubernetes-xenial main" | sudo tee /etc/apt/sources.list.d/kubernetes.list
```

### 4.2 安装 kubeadm, kubelet, kubectl

```bash
# 更新仓库
sudo apt update

# 查看可用版本
apt-cache policy kubelet | head -20

# 安装指定版本（推荐 1.28.x）
KUBE_VERSION=1.28.0
sudo apt install -y kubelet=${KUBE_VERSION}-* kubeadm=${KUBE_VERSION}-* kubectl=${KUBE_VERSION}-*
sudo apt-mark hold kubelet kubeadm kubectl

# 安装最新版本
# sudo apt install -y kubelet kubeadm kubectl
# sudo apt-mark hold kubelet kubeadm kubectl
```

### 4.3 验证安装

```bash
kubeadm version
kubelet --version
kubectl version --client
```

---

## 5. 初始化 Master 节点

### 5.1 在 Master 节点执行初始化

```bash
# 使用 kubeadm init（根据实际情况修改 API server 地址）
sudo kubeadm init \
  --pod-network-cidr=10.244.0.0/16 \
  --service-cidr=10.96.0.0/12 \
  --kubernetes-version=1.28.0 \
  --apiserver-advertise-address=192.168.2.102

# 或者使用国内镜像加速
sudo kubeadm init \
  --pod-network-cidr=10.244.0.0/16 \
  --service-cidr=10.96.0.0/12 \
  --image-repository registry.cn-hangzhou.aliyuncs.com/google_containers
```

### 5.2 配置 kubectl

```bash
# 创建 .kube 目录
mkdir -p $HOME/.kube

# 复制配置文件
sudo cp -i /etc/kubernetes/admin.conf $HOME/.kube/config

# 设置权限
sudo chown $(id -u):$(id -g) $HOME/.kube/config

# 添加 kubectl 自动补全
echo "source <(kubectl completion bash)" >> ~/.bashrc
source ~/.bashrc
```

### 5.3 保存加入命令

初始化完成后会输出类似以下的 join 命令，请妥善保存：

```
kubeadm join 192.168.1.100:6443 --token xxxxxxx --discovery-token-ca-cert-hash sha256:xxxxxx
```

---

## 6. 安装网络插件

### 6.1 安装 Calico（推荐）

```bash
# 下载 Calico YAML
curl https://docs.projectcalico.org/manifests/calico.yaml -O

# 查看并修改 Pod CIDR（如果与初始化时不一致）
# vim calico.yaml
# 找到 CALICO_IPV4POOL_CIDR，设置为 10.244.0.0/16

# 应用 YAML
kubectl apply -f calico.yaml

# 验证
kubectl get pods -n kube-system
```

### 6.2 安装 Flannel（备选）

```bash
kubectl apply -f https://raw.githubusercontent.com/flannel-io/flannel/master/Documentation/kube-flannel.yml
```

### 6.3 等待集群就绪

```bash
# 等待 CoreDNS 就绪
kubectl get pods -n kube-system -w

# 确认所有 Pod 运行正常
kubectl get nodes
# 应显示 master 节点状态为 Ready
```

---

## 7. 加入 Worker 节点

### 7.1 在每个 Worker 节点执行

```bash
# 使用之前保存的 join 命令（如果没有，使用以下命令重新获取）
# 在 Master 节点执行：
# kubeadm token create --print-join-command

# Worker 节点执行
sudo kubeadm join 192.168.1.100:6443 --token xxxxxxx --discovery-token-ca-cert-hash sha256:xxxxxx
```

### 7.2 验证节点状态

```bash
# 在 Master 节点执行
kubectl get nodes

# 应该显示所有节点状态为 Ready
# NAME         STATUS   ROLES           AGE     VERSION
# k8s-master   Ready    control-plane   5m      v1.28.0
# k8s-node1    Ready    <none>          2m      v1.28.0
# k8s-node2    Ready    <none>          1m      v1.28.0
```

---

## 8. 部署示例应用

### 8.1 部署 Nginx

```bash
# 创建 deployment
kubectl create deployment nginx --image=nginx:1.25-alpine

# 暴露服务
kubectl expose deployment nginx --port=80 --type=NodePort

# 查看 pods
kubectl get pods

# 查看服务
kubectl get svc
```

### 8.2 部署完整示例（nginx-app.yaml）

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-app
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
---
apiVersion: v1
kind: Service
metadata:
  name: nginx-svc
spec:
  type: NodePort
  selector:
    app: nginx
  ports:
  - port: 80
    targetPort: 80
    nodePort: 30080
```

```bash
kubectl apply -f nginx-app.yaml
```

### 8.3 创建 Ingress 示例

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: nginx-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
  - host: nginx.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: nginx-svc
            port:
              number: 80
```

---

## 9. 常用命令

### 9.1 节点管理

```bash
# 查看节点
kubectl get nodes
kubectl get nodes -o wide

# 查看节点详情
kubectl describe node k8s-master

# 节点污点管理
kubectl taint nodes k8s-master node-role.kubernetes.io/control-plane:NoSchedule-

# 排除节点（用于维护）
kubectl cordon k8s-node1          # 标记为不可调度
kubectl drain k8s-node1 --ignore-daemonsets  # 驱逐 Pod
kubectl uncordon k8s-node1        # 恢复调度
```

### 9.2 Pod 管理

```bash
# 查看所有 namespace 的 Pod
kubectl get pods --all-namespaces

# 查看特定 namespace 的 Pod
kubectl get pods -n kube-system

# 查看 Pod 详情
kubectl describe pod <pod-name>

# 查看 Pod 日志
kubectl logs <pod-name>
kubectl logs -f <pod-name>                    # 实时日志
kubectl logs <pod-name> --previous            # 上一个容器的日志

# 进入 Pod
kubectl exec -it <pod-name> -- /bin/sh
kubectl exec -it <pod-name> -c <container> -- /bin/sh

# 删除 Pod
kubectl delete pod <pod-name>
```

### 9.3 Deployment 管理

```bash
# 查看 deployments
kubectl get deployments

# 扩缩容
kubectl scale deployment nginx --replicas=5

# 更新镜像
kubectl set image deployment/nginx nginx=nginx:1.25

# 回滚
kubectl rollout undo deployment/nginx
kubectl rollout history deployment/nginx
kubectl rollout undo deployment/nginx --to-revision=2

# 重启 deployment
kubectl rollout restart deployment/nginx
```

### 9.4 Service 管理

```bash
# 查看 services
kubectl get svc

# 查看 endpoints
kubectl get endpoints

# 端口转发（测试用）
kubectl port-forward svc/nginx-svc 8080:80
```

### 9.5 资源清理

```bash
# 删除所有资源
kubectl delete all --all

# 删除特定标签的资源
kubectl delete pods -l app=nginx
```

### 9.6 Dashboard

```bash
# 安装 Kubernetes Dashboard
kubectl apply -f https://raw.githubusercontent.com/kubernetes/dashboard/v2.7.0/aio/deploy/recommended.yaml

# 创建 admin 用户
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ServiceAccount
metadata:
  name: admin-user
  namespace: kubernetes-dashboard
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: admin-user
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: cluster-admin
subjects:
- kind: ServiceAccount
  name: admin-user
  namespace: kubernetes-dashboard
EOF

# 获取 token
kubectl -n kubernetes-dashboard create token admin-user

# 访问 Dashboard（代理方式）
kubectl proxy
# 访问：http://localhost:8001/api/v1/namespaces/kubernetes-dashboard/services/https:kubernetes-dashboard:/proxy/
```

---

## 10. 故障排除

### 10.1 常见问题

| 问题 | 解决方案 |
|------|----------|
| kubelet 启动失败 | 检查 swap 是否关闭、内核参数是否正确 |
| Pod 一直 Pending | 检查网络插件是否安装、节点资源是否充足 |
| Pod 一直 CrashLoopBackOff | 检查镜像是否可拉取、应用配置是否正确 |
| CoreDNS 一直是 Pending | 检查网络插件安装是否正确 |
| Node NotReady | 检查 kubelet 和 containerd 服务状态 |

### 10.2 查看日志

```bash
# kubelet 日志
sudo journalctl -u kubelet -f

# containerd 日志
sudo journalctl -u containerd -f

# kubeadm 日志
sudo cat /var/log/kubernetes/cluster-initialization.log
```

### 10.3 重置集群

```bash
# 在所有节点执行
sudo kubeadm reset
sudo systemctl stop kubelet
sudo systemctl stop containerd
sudo rm -rf /etc/kubernetes/manifests /var/lib/etcd /var/lib/kubelet
sudo iptables -F && iptables -t nat -F && iptables -t mangle -F && iptables -X
```

### 10.4 常用检查命令

```bash
# 检查 kubelet 状态
sudo systemctl status kubelet

# 检查 kubelet 日志
sudo journalctl -xeu kubelet --no-pager

# 检查容器运行时
sudo crictl info

# 检查容器列表
sudo crictl ps -a

# 检查 API server
curl -k https://localhost:6443/healthz

# 检查 etcd
kubectl exec -n kube-system etcd-k8s-master -- etcdctl --cacert=/etc/kubernetes/pki/etcd/ca.crt --cert=/etc/kubernetes/pki/etcd/server.crt --key=/etc/kubernetes/pki/etcd/server.key endpoint health
```

---

## 📚 附录

### A. Kubernetes 资源限制示例

```yaml
resources:
  requests:
    memory: "64Mi"
    cpu: "250m"
  limits:
    memory: "128Mi"
    cpu: "500m"
```

### B. ConfigMap 示例

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  APP_ENV: "production"
  LOG_LEVEL: "info"
```

### C. Secret 示例

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: app-secret
type: Opaque
stringData:
  DATABASE_PASSWORD: "mypassword"
```

### D. 健康检查示例

```yaml
livenessProbe:
  httpGet:
    path: /healthz
    port: 8080
  initialDelaySeconds: 3
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /ready
    port: 8080
  initialDelaySeconds: 5
  periodSeconds: 10
```

---

## 📞 参考链接

- [Kubernetes 官方文档](https://kubernetes.io/zh/docs/)
- [kubeadm 官方文档](https://kubernetes.io/zh/docs/reference/setup-tools/kubeadm/)
- [Calico 官方文档](https://projectcalico.docs.tigera.io/)
- [Docker 官方文档](https://docs.docker.com/)

---

> 本教程由 CodeBuddy 生成 | Kubernetes v1.28+ | Ubuntu 20.04/22.04
