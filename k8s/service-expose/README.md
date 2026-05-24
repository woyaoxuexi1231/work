# Kubernetes 服务外部暴露方案

## 核心问题

Kubernetes Pod 是短命的、可漂移的——Pod 随时可能被调度到另一个 Node 上。如果外部用户直接访问 `http://192.168.3.101:30080`，一旦 Pod 换到 k8s-node2，这条链路就断了。

**你的需求**：一个**固定的 IP 或域名**，无论 Pod 在后端哪个节点上，用户都能访问到。

---

## 业界方案对比

| 方案 | 适用场景 | 复杂度 | 外部 IP |
|------|----------|--------|---------|
| **LoadBalancer** | 云环境 (AWS/阿里云/腾讯云) | ⭐ | 云商分配公网 IP |
| **MetalLB** | **裸金属机房** ← 你的场景 | ⭐⭐⭐ | 从 IP 池分配 VIP |
| **NodePort + 外部 LB** | 已有硬件 LB/HAProxy | ⭐⭐ | 外部 LB 的 IP |
| **NodePort + Keepalived** | 小规模裸金属 | ⭐⭐ | 虚拟浮动 IP |
| **Ingress Controller** | 7 层 HTTP 路由 | ⭐⭐ | 配合上面任一方案 |

---

## 1. LoadBalancer（云环境标准方案）

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-nginx
spec:
  type: LoadBalancer     # 云商会自动创建公网 LB
  selector:
    run: my-nginx
  ports:
  - port: 80
```

**原理**：云平台检测到 `type: LoadBalancer`，自动创建一个云 LB实例，分配公网 IP 转发到所有 Node 的 NodePort。

**优点**：零配置，一个 YAML 搞定。
**缺点**：裸金属/自建机房不可用，除非装 MetalLB。

---

## 2. MetalLB（裸金属标准方案）★ 推荐

MetalLB 是裸金属 Kubernetes 的 **事实标准**。它在 L2 模式下接管一个 IP 地址段，对外暴露一个虚拟 IP（VIP）。

```
                   ┌─────────────────────────────────┐
  curl 192.168.3.200:80 ───→│  192.168.3.200 (VIP)              │
                   │  MetalLB 通告该 IP                          │
                   │  ┌───────────────────────────┐              │
                   │  │ Node1    │ Node2    │ Node3│              │
                   │  │ Pod ✓    │          │      │              │
                   │  └───────────────────────────┘              │
                   └─────────────────────────────────┘
```

- VIP `192.168.3.200` 永久绑定在负载所在节点上
- 该节点宕机后，VIP 自动漂移到另一个健康节点
- **对外永远用同一个 IP**，无论 Pod 在哪个节点

### 安装（你的集群）

```bash
sudo bash setup-metallb.sh
```

装完后的效果：

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-nginx
spec:
  type: LoadBalancer    # MetalLB 会接管，分配一个 VIP
  selector:
    run: my-nginx
  ports:
  - port: 80
```

```bash
kubectl get svc my-nginx
# NAME       TYPE           CLUSTER-IP     EXTERNAL-IP      PORT(S)
# my-nginx   LoadBalancer   10.96.x.x      192.168.3.200    80:xxxxx/TCP
```

用户访问 `http://192.168.3.200`，永远不用关心 Pod 在哪台机器上。

**优点**：标准 K8s API，YAML 跟云环境写法一模一样，可无缝迁移。
**缺点**：多占用几个 IP，需要配置 ARP 网络。

---

## 3. Ingress Controller（HTTP 路由层）

MetalLB 解决的是 IP 问题，**Ingress** 解决的是域名/路径分发问题：

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: my-ingress
spec:
  rules:
  - host: nginx.mycluster.local      # 域名路由
    http:
      paths:
      - path: /api                    # 路径路由
        pathType: Prefix
        backend:
          service:
            name: my-api
            port:
              number: 8080
      - path: /
        pathType: Prefix
        backend:
          service:
            name: my-nginx
            port:
              number: 80
```

**Ingress 本身也需要对外暴露**（通过 MetalLB 或 NodePort）。

典型架构：

```
用户 → DNS(nginx.mycluster.local) → MetalLB VIP → Ingress Controller → Service → Pod
```

---

## 4. NodePort + 外部 HAProxy/Keepalived

如果不想引入 MetalLB，可以在集群前面放一台 HAProxy：

```
                    ┌─────────────┐
  用户 ──────→│  HAProxy    │ ← 固定 IP，如 192.168.3.99
                    └──┬───┬───┬──┘
                       │   │   │
           Node1:30080 Node2:30080 Node3:30080
```

HAProxy 配置片段：

```
frontend k8s
    bind 192.168.3.99:80
    default_backend k8s_nodes

backend k8s_nodes
    server node1 192.168.3.100:30080 check
    server node2 192.168.3.101:30080 check
    server node3 192.168.3.102:30080 check
```

---

## 5. 方案选型决策

```
你用的是云吗？
  ├── 是 → LoadBalancer，最简单
  └── 否（裸金属/虚拟机）
      ├── 能接受多占用几个 IP？
      │   ├── 是 → MetalLB ★ 推荐
      │   └── 否 → NodePort + 外部 LB / Keepalived
      └── 需要域名/路径路由？
          └── 在 MetalLB 基础上加 Ingress Controller
```

---

## 你的集群推荐方案

```
MetalLB (Layer2) + Ingress Controller
```

1. **MetalLB** 提供固定 VIP
2. **Ingress** 提供域名路由
3. 用户永远访问 `http://nginx.mycluster.local`（域名解析到 MetalLB VIP）
4. 无论 Pod 漂到哪个节点，VIP 都会跟着走

---

## 相关文件

| 文件 | 作用 |
|------|------|
| `setup-metallb.sh` | 一键安装 MetalLB + 配置 IP 池 |
| `test-lb-service.yaml` | 测试 LoadBalancer 的示例 Service |
