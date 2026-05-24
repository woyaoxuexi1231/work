# MetalLB 深度解析：为什么它是裸金属 K8s 的标配

---

## 一、背景：一个真实的困境

### 1.1 你遇到的具体问题

你在 k8s-node1 上跑了一个 nginx Pod，用 `--type=NodePort --port=80` 暴露出去，分配了端口 `30080`。Windows 上访问 `http://192.168.3.101:30080`，一切正常。

然后 k8s-node1 挂了，或者你执行了 `kubectl drain k8s-node1`，Pod 被自动漂移到 k8s-node2。

**问题来了**：你的 Windows 还在访问 `http://192.168.3.101:30080`，但这个地址已经不可用了。你需要手动把 URL 改成 `http://192.168.3.102:30080` —— 这在实际业务中是不可能的。

### 1.2 Kubernetes 的设计矛盾

Kubernetes 的核心承诺之一：**Pod 是无状态的、可被任意调度的**。你对 Pod 说"不管你在哪台机器上，你都得能服务"，但外部用户却说"我只认一个固定 IP"。

```
Kubernetes 的承诺                     用户的需求
┌──────────────────┐              ┌──────────────────┐
│ Pod 随便飘       │      ?       │ 我只认一个 IP    │
│ 节点随时挂       │──────────────│ 换 IP 是不可接受的│
│ 调度全自动       │              │ 我需要稳定的入口  │
└──────────────────┘              └──────────────────┘
```

这两个需求本质上**互相矛盾**。K8s 自己解决不了，它把这个责任推给了"外部"——在云上是云厂商的 LoadBalancer，在裸金属上，就是 **MetalLB**。

---

## 二、MetalLB 要解决的问题

一句话：**给裸金属 Kubernetes 集群提供 LoadBalancer 类型的 Service**。

具体拆开，它解决三个问题：

| 问题 | 现有方案弊端 | MetalLB 方案 |
|------|-------------|-------------|
| **外部入口固定** | NodePort 绑定具体节点 IP，节点挂了入口就没了 | 分配一个虚拟 IP（VIP），节点挂了 VIP 自动漂移 |
| **YAML 可移植** | 裸金属写 NodePort，上云改 LoadBalancer，两套配置 | 裸金属也用 `type: LoadBalancer`，跟云环境写法完全一样 |
| **单点故障** | 外部 LB/HAProxy 自己成为新的单点 | VIP 自动漂移，没有额外单点 |

### 你集群的实际对比

```
┌─────────── 方案一：NodePort ───────────┐
│                                        │
│  curl 192.168.3.101:30080  ──→  Node1  │
│                                        │
│  Node1 挂了以后：                       │
│  curl 192.168.3.101:30080  ──→  ❌      │
│  需要手动改：                           │
│  curl 192.168.3.102:30080  ──→  Node2  │
└────────────────────────────────────────┘

┌─────────── 方案二：MetalLB ────────────┐
│                                        │
│  curl 192.168.3.200  ──→  VIP ──→  Pod │
│                                        │
│  Node1 挂了以后：                       │
│  curl 192.168.3.200  ──→  VIP ──→  Pod │
│    ↑                    ↑        ↑     │
│    完全不变           自动漂到    不变  │
│                        Node2           │
└────────────────────────────────────────┘
```

---

## 三、设计思想：做减法

MetalLB 不是"再造一个负载均衡器"，它的设计哲学是：

### 3.1 融入 K8s 原生 API

```
大部分人理解的方案：
  "装一个外部软件 → 配置 IP → 手动关联 K8s Service"

MetalLB 的方案：
  "用户写好 type: LoadBalancer → K8s 自动通知 MetalLB → 自动分配 IP"
```

MetalLB 把自己注册为一个 K8s **控制器**，Watch 所有 `type: LoadBalancer` 的 Service。当用户创建这样的 Service 时，MetalLB 自动响应，就像云上的 LoadBalancer 一样。用户甚至**感觉不到 MetalLB 的存在**——他只是在写标准的 K8s YAML。

### 3.2 只做一件事：IP 分配 + 通告

```
          ┌──────────────┐
          │   MetalLB    │
          │              │
用户请求──│ 1. 分配 VIP  │──→ VIP 分配给 Service
          │ 2. 通告 VIP  │──→ 告诉网络"这个 IP 在我这里"
          └──────────────┘
              ↓
         流量到了节点之后？
              ↓
  ┌───────────────────────────────────┐
  │  kube-proxy（K8s 自带）接管        │
  │  VIP:80 → ClusterIP → Pod:80      │
  └───────────────────────────────────┘
```

MetalLB **不管**流量如何转发到 Pod。它只管两件事：
1. 从 IP 池里给 Service 分配一个 External IP
2. 向局域网通告"这个 IP 在我这里，给我发过来"

收到流量之后的路由，是 K8s 自己的 kube-proxy 负责的。这就是"做减法"——不重复造轮子。

### 3.3 两种工作模式的选择

MetalLB 提供了两种模式：

| | Layer2 (ARP) | BGP |
|------|-------------|-----|
| **原理** | 响应 ARP 请求，告诉路由器 "这个 IP 的 MAC 地址是我" | 跟路由器建立 BGP 对等，宣告路由 |
| **适用** | 家庭网络、小型机房、所有交换机都支持 | 企业网络、有 BGP 路由器 |
| **单点？** | 同一时刻只有一个节点持有 VIP（但挂了会漂移） | 多个节点同时宣告（真·负载均衡） |
| **配置** | 选一个网段就行，不碰网络设备 | 需要配路由器 |

**你的场景选 Layer2**：同网段、不碰路由器、挂了自己漂移。

---

## 四、一个请求的完整流转

假设配置：
- MetalLB IP 池：`192.168.3.200 - 192.168.3.210`
- Service 类型：LoadBalancer，分配了 `192.168.3.200`
- Pod `my-nginx` 当前运行在 `k8s-node2 (192.168.3.102)`，容器端口 `80`

### 4.1 请求流转全景

```
 ① curl http://192.168.3.200
         │
         ▼
 ② 你的 Windows(192.168.3.x) 发出 ARP 广播：
    "谁有 192.168.3.200？"
         │
         ├──── ARP 请求 ────→ 局域网所有机器
         │
         ▼
 ③ k8s-node2 上的 MetalLB speaker 回答：
    "192.168.3.200 在我这里，MAC 地址是 xx:xx:xx:xx:xx:xx"
    （此时 node2 是 VIP 持有者）
         │
         ▼
 ④ TCP 包到达 k8s-node2 的物理网卡
         │
         ▼
 ⑤ 内核网络栈看到目标 IP 是 192.168.3.200
    → 查 iptables/IPVS 规则（kube-proxy 写的）
    → 发现 192.168.3.200:80 对应 Service:80
         │
         ▼
 ⑥ Service:80 → 查 Endpoints
    → 发现后端是 Pod my-nginx 的 IP (10.244.x.x:80)
         │
         ▼
 ⑦ 包被改写：目标 IP 从 192.168.3.200 变成 10.244.x.x
    → 走 CNI 网络（Calico）到达 Pod
         │
         ▼
 ⑧ Pod 里的 nginx 处理请求
    → 响应原路返回
         │
         ▼
 ⑨ 响应包回到 Windows，TCP 连接完成
```

### 4.2 每一步在做什么

```
步骤       谁负责          在干什么
─────────────────────────────────────────────────
① - ②     你的 Windows    发起 HTTP 请求
③          MetalLB         ARP 响应，告诉网络"IP 在我这"
④          物理网卡        收包
⑤          kube-proxy      iptables/IPVS DNAT，改写目标地址
⑥          kube-proxy      从 Endpoints 列表里选一个 Pod IP
⑦          Calico          Pod 网络路由
⑧          Pod(niginx)     处理业务
⑨          Calico + 内核   原路返回
```

### 4.3 节点宕机时 VIP 如何漂移

```
节点宕机前：
  VIP 192.168.3.200 由 k8s-node2 持有
  Windows 的 ARP 缓存: 192.168.3.200 → k8s-node2 的 MAC

节点宕机后：
  1. MetalLB speaker 在 node2 上的心跳停止
  2. Leader election → k8s-node3 当选新 Leader
  3. node3 上的 speaker 发送 Gratuitous ARP（免费 ARP）：
     "192.168.3.200 现在在我这里，MAC 是 xx:xx:xx:xx:xx"
  4. 局域网交换机更新 MAC 表
  5. Windows 的 ARP 缓存过期后，重新查询
     得到新 MAC：192.168.3.200 → k8s-node3 的 MAC
  6. 后续请求自动发到 node3
  7. kube-proxy 在 node3 上同样有 Service → Pod 的路由规则

  整个过程对外部用户透明，通常 10-30 秒内恢复。
```

---

## 五、你的集群实际操作

### 5.1 安装

```bash
cd ~/k8s/service-expose
sudo bash setup-metallb.sh
```

### 5.2 使用

之前你是这样暴露服务的：

```bash
# 旧方式：NodePort，绑死在具体节点上
kubectl expose pod my-nginx --type=NodePort --port=80
# → curl http://192.168.3.101:30080  ← 节点挂了就完蛋
```

现在这样：

```bash
# 新方式：LoadBalancer，VIP 自动分配、自动漂移
kubectl expose pod my-nginx --type=LoadBalancer --port=80
# → curl http://192.168.3.200       ← 永远是它，Pod 飘到哪都不怕
```

### 5.3 查看效果

```bash
# 查看分配的 External IP
kubectl get svc my-nginx

# 输出示例：
# NAME       TYPE           CLUSTER-IP     EXTERNAL-IP      PORT(S)
# my-nginx   LoadBalancer   10.96.x.x      192.168.3.200    80:xxxxx/TCP
#
#                     固定的 ClusterIP ──┘  └── MetalLB 分配的 VIP
```

### 5.4 验证高可用

```bash
# 找出 Pod 在哪个节点
kubectl get pod my-nginx -o wide

# 假设在 node1 上，驱逐它
# 把 k8s-node1 这个节点上的所有业务 Pod 全部安全赶走 → 然后把节点设为 “不可用、维护状态”
# 我要对这台服务器关机 / 重启 / 维护了，先把上面的容器全部挪走，不影响业务！
kubectl drain k8s-node1 --ignore-daemonsets --delete-emptydir-data

# 维护完了，怎么恢复节点？
kubectl uncordon k8s-node1

# Pod 会自动调度到 node2 或 node3
# 同时 VIP 漂移到新的节点
# curl http://192.168.3.200 仍然可访问，最多中断 10-30 秒
```



### There are pending nodes to be drained: k8s-node1 cannot delete 

我用**最通俗、最接地气**的话给你讲明白，你一下就懂了👇

1. 啥叫 **“没人管的 Pod”（裸奔 Pod）**

你现在的 `my-nginx` 就是 **裸奔的 Pod**：
- 你手动创建的
- 删了就没了
- **没有“老板”管它**
- 挂了、删了，**没人帮你重启、重建**

K8s 觉得：
**这东西很重要，我不敢随便删！所以 drain 不让你动。**

---

2. 什么叫 **“有人管”**？

就是给 Pod 配一个 **老板（控制器）**：
- **Deployment（最常用）**
- **StatefulSet**
- **DaemonSet**

有老板管的 Pod：
- 删了 → 老板立刻新建一个
- 挂了 → 老板自动重启
- 迁移节点 → 老板安排

所以 `drain` 可以放心删，**因为知道删了还会回来**。

---

3. 你现在的 my-nginx 是怎么来的？

你大概率是这么创建的（裸奔命令）：
```bash
kubectl run my-nginx --image=nginx
```
✅ 运行成功
❌ **没有控制器，没人管**

---

4. 我教你：**1 分钟把它变成“有人管”**

第一步：删掉现在裸奔的 Pod

```bash
kubectl delete pod my-nginx
```

第二步：用 Deployment 创建（有人管！）

```bash
kubectl create deployment my-nginx --image=nginx
```

搞定！

现在这个 `my-nginx` 就**有老板管了**。

以后：
- 删了 → 自动重建
- drain 节点 → 可以安全驱逐
- 不会再报那个错了！

---

5. 再试一次 drain（绝对成功）

```bash
kubectl drain k8s-node1 --ignore-daemonsets --delete-emptydir-data
```
**这次不会报错！**

---

# 一句话总结（你一定要记住）
- **kubectl run** → 裸奔 Pod（没人管，drain 报错）
- **kubectl create deployment** → 托管 Pod（有人管，安全）

---

需要我再给你讲讲 **Deployment 是什么、怎么扩缩容、怎么更新**吗？
你学 K8s 这个必须懂～



---

## 六、再往上：Ingress Controller

MetalLB 解决了"固定 IP"问题。但如果你的集群跑了 10 个服务，难道要分配 10 个 External IP？而且用户不想记 IP，想像访问网站一样用域名。

这时候在 MetalLB 之上加 **Ingress Controller**：

```
               ┌─────────────────────────────────┐
               │        Ingress Controller        │
               │                                  │
               │  nginx.example.com/api  ──→  api-svc:8080
               │  nginx.example.com/web  ──→  web-svc:80
               │  admin.example.com      ──→  dashboard:443
               │                                  │
               └────────────┬────────────────────┘
                            │
               ┌────────────▼────────────────────┐
               │  MetalLB LoadBalancer Service    │
               │  External IP: 192.168.3.200:80   │
               └─────────────────────────────────┘
```

- **MetalLB**：保证入口 IP 永远不变
- **Ingress**：用同一个 IP 承载 N 个域名/路径

这是生产环境的事实标准架构。

---

## 七、总结

| 层次 | 组件 | 解决的问题 |
|------|------|-----------|
| 固定入口 | MetalLB | "Pod 随便飘，外部永远访问同一个 IP" |
| 域名路由 | Ingress | "一个 IP 承载多个域名，按 Host/Path 分发" |
| 流量转发 | kube-proxy | "VIP 的流量最终到达哪个 Pod" |
| Pod 网络 | Calico | "跨节点的 Pod 之间怎么通信" |

MetalLB 卡在最外层——它不碰流量转发，只负责**把流量引到集群里**。进来以后的事，K8s 自己全包了。
