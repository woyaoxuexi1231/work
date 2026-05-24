# Ingress Controller 深度解析：一个 IP，N 个服务

---

## 一、背景：MetalLB 解决了一半的问题

MetalLB 装好后，你兴奋地把 my-nginx 暴露出去：

```bash
kubectl expose pod my-nginx --type=LoadBalancer --port=80
kubectl get svc my-nginx
# EXTERNAL-IP: 192.168.3.2008 
```

太好了——固定的 IP，Pod 随便飘。

然后你又部署了一个 API 服务，又部署了一个管理后台。按照同样的方式：

```bash
kubectl expose deploy my-api --type=LoadBalancer --port=8080
kubectl expose deploy my-admin --type=LoadBalancer --port=80
```

你得到了三个 External IP：`192.168.3.200`、`192.168.3.201`、`192.168.3.202`。

### 1.1 新问题浮现

```
服务         External IP         用户怎么访问
─────────────────────────────────────────────
my-nginx     192.168.3.200       http://192.168.3.200
my-api       192.168.3.201       http://192.168.3.201:8080
my-admin     192.168.3.202       http://192.168.3.202
dashboard    192.168.3.203       https://192.168.3.203:30443
```

**问题清单**：

1. **IP 不够用**：IP 池配了 `192.168.3.200 - 192.168.3.210`，只有 11 个，服务多了就耗尽
2. **用户要记 IP**：没有人愿意记 `192.168.3.201`，更没人愿意记端口号 `:8080`
3. **证书问题**：一个 IP 只能绑一个 HTTPS 证书，10 个服务就得 10 个 IP + 10 张证书
4. **运维混乱**：每加一个服务都要新增 LoadBalancer、通知用户新地址

### 1.2 现实世界的做法

你在浏览器里访问 GitHub，可没写过 `http://140.82.113.3`，你写的是 `github.com`。GitHub 背后可能有几百个微服务，但对外只有一个域名。它是怎么做到的？

答案：**反向代理 + 域名/路径路由**。

在 K8s 里，这个反向代理叫 **Ingress Controller**。

---

## 二、Ingress Controller 要解决的问题

一句话：**让 N 个服务共用一个入口，通过域名或路径区分流量去向**。

| 问题 | 没有 Ingress | 有 Ingress |
|------|-------------|-----------|
| **IP 占用** | 10 个服务 = 10 个 External IP | 10 个服务 = 1 个 External IP |
| **用户体验** | 用户需要记 IP + 端口 | 用户记域名，如 `nginx.mycluster.com` |
| **HTTPS** | 每个 IP 一张证书，管理复杂 | 一道入口统一 TLS 终结，一张证书搞定 |
| **灰度发布** | 无原生支持 | 按 Header/Cookie 分流不同版本 |
| **访问控制** | 各自为政 | 统一入口做限流、认证、IP 白名单 |

### 你的集群前后对比

```
┌──────── 没有 Ingress（每个服务一个 External IP）────────┐
│                                                         │
│  用户 ──→ 192.168.3.200 ──→ nginx                       │
│  用户 ──→ 192.168.3.201 ──→ api                         │
│  用户 ──→ 192.168.3.202 ──→ admin                       │
│                                                         │
│  ❌ IP 池很快耗尽                                        │
│  ❌ 用户需要记各个 IP                                    │
└─────────────────────────────────────────────────────────┘

┌────────── 有 Ingress（一个 External IP + 域名路由）──────┐
│                                                         │
│                     ┌──────────────────┐                │
│  用户 ─────────────→│  Ingress         │                │
│  nginx.mycluster.com│  Controller      │                │
│  api.mycluster.com  │  (192.168.3.200) │                │
│  admin.mycluster.com│                  │                │
│                     └──┬────┬─────┬───┘                │
│                        │    │     │                     │
│                     nginx  api  admin                   │
│                                                         │
│  ✅ 永远只需要一个 External IP                           │
│  ✅ 用户只看域名，加服务只需加 DNS 记录                  │
└─────────────────────────────────────────────────────────┘
```

---

## 三、设计思想：把 HTTP 路由逻辑从应用层抽离

### 3.1 核心模型：两层抽象

Ingress Controller 是 K8s 对 HTTP(S) 路由的标准化抽象，它有两个层次：

```
┌────────────────────── 1. Ingress 资源（规则层） ──────────────────────┐
│                                                                      │
│  你写的 YAML（声明式）：                                              │
│                                                                      │
│  apiVersion: networking.k8s.io/v1                                    │
│  kind: Ingress                                                       │
│  spec:                                                               │
│    rules:                                                            │
│    - host: nginx.mycluster.com     ← 这个域名走到这里                  │
│      http:                                                            │
│        paths:                                                         │
│        - path: /                   ← 匹配根路径                       │
│          backend:                  ← 转发到哪个 Service               │
│            service: my-nginx                                           │
│            port: 80                                                   │
│    - host: api.mycluster.com       ← 这个域名走到这里                  │
│      ...                                                              │
└──────────────────────────────────────────────────────────────────────┘
                            │
                            │  Ingress Controller 把规则翻译成
                            │  nginx.conf / haproxy.cfg / envoy.json
                            ▼
┌────────────────────── 2. Ingress Controller（执行层） ───────────────┐
│                                                                      │
│  运行在集群里的反向代理（nginx / HAProxy / Envoy / Traefik）           │
│  监听到 Ingress 资源变化 → 动态更新自己的路由配置                      │
│  不需要重启，不需要 reload，hot-reload                                │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

**设计精髓**：你写声明式 YAML（我想让 `nginx.mycluster.com` 到 my-nginx），Controller 自动把它翻译成 Nginx 配置并热加载。你跟 nginx.conf 彻底绝缘。

### 3.2 为什么不直接用 Nginx Pod？

```
❌ 自己跑 Nginx Pod：
  - 手动写 nginx.conf
  - 加服务 → 改 nginx.conf → 发 ConfigMap → reload nginx
  - 删服务 → 改 nginx.conf → 再次 reload
  - 证书过期 → 手动更新文件 → reload
  - 版本多了 → nginx.conf 变成几千行的"祖传配置"

✅ Ingress Controller：
  - kubectl apply -f ingress.yaml
  - Controller 自动生成配置、自动热加载
  - 删服务：kubectl delete，自动清除对应路由
  - 证书：一个 Secret 挂上去，自动续期（配合 cert-manager）
  - 永远不需要看 nginx.conf
```

**思想**：声明式 > 命令式。你**描述想要什么**，Controller **负责怎么做到**。这是 K8s 一切控制器的核心哲学，Ingress Controller 是最典型的体现。

### 3.3 主要实现对比

K8s 只定义了 Ingress 这个**资源类型**（API），不提供**实现**。实现由社区来做：

| Controller | 底层引擎 | 特点 |
|-----------|---------|------|
| **ingress-nginx** ★ | Nginx | K8s 社区维护，最流行，功能最全 |
| **traefik** | Traefik | 自动服务发现，微服务友好 |
| **haproxy-ingress** | HAProxy | 性能极致，适合高并发 |
| **kong** | Nginx+Lua | 网关功能丰富（限流、认证、插件化） |
| **istio** | Envoy | Service Mesh 级别，太重了 |

**你的场景推荐 `ingress-nginx`**：功能最全、中文资料最多、社区最活跃。

---

## 四、一个请求的完整流转

配置如下：

- MetalLB VIP：`192.168.3.200`
- Ingress Controller：通过 LoadBalancer Service 暴露在 `192.168.3.200:80`
- 两个后端 Service：`my-nginx`（80端口）、`my-api`（8080端口）
- Ingress 规则：`nginx.mycluster.com` → my-nginx，`api.mycluster.com` → my-api

### 4.1 请求流转全景

```
 ① 浏览器输入 http://nginx.mycluster.com
         │
         ▼
 ② DNS 查询：nginx.mycluster.com → 192.168.3.200
    （你在 /etc/hosts 或内网 DNS 配的）
         │
         ▼
 ③ TCP 连接建立：Windows ──TCP──→ 192.168.3.200:80
         │
         ▼
 ④ MetalLB 把 VIP 的流量引到持有这个 VIP 的节点
    （比如 k8s-node3）
         │
         ▼
 ⑤ kube-proxy 转发：192.168.3.200:80 → Ingress Controller Pod 的 80 端口
    （DNAT，iptables/IPVS 规则）
         │
         ▼
 ⑥ Ingress Controller Pod（nginx 进程）收到 HTTP 请求：
    
    HTTP Request:
      GET / HTTP/1.1
      Host: nginx.mycluster.com        ← 关键！从 HTTP Header 里读域名
    
         │
         ▼
 ⑦ Nginx 查自己的路由表（由 Ingress 规则自动生成）：

    server {
        server_name nginx.mycluster.com;    ← 匹配 Host
        location / {
            proxy_pass http://my-nginx:80;   ← 转发到 Service
        }
    }

    server {
        server_name api.mycluster.com;
        location / {
            proxy_pass http://my-api:8080;
        }
    }

    Host: nginx.mycluster.com → 命中第一个 server 块
    Path: /                    → 命中 location /
    Backend: http://my-nginx:80
    
         │
         ▼
 ⑧ Nginx 作为反向代理，向 my-nginx Service 发起新连接：
    
    my-nginx 是一个 ClusterIP Service (10.96.x.x:80)
    kube-proxy 的规则再次工作：10.96.x.x:80 → Pod IP:80
    
         │
         ▼
 ⑨ 请求到达 my-nginx Pod，nginx 处理并响应
         │
         ▼
 ⑩ 响应逆序返回：
    Pod → kube-proxy → Ingress Controller → kube-proxy → MetalLB → 用户浏览器
         │
         ▼
 ⑪ 浏览器渲染页面，地址栏显示 http://nginx.mycluster.com
```

### 4.2 如果要访问 api 服务呢？

```
同样的 VIP (192.168.3.200)，同样的端口 (80)，完全不同的服务：

curl -H "Host: api.mycluster.com" http://192.168.3.200/api/users
                                      ↑
                      Ingress Controller 看 Host 头
                      "api.mycluster.com" → 转发到 my-api:8080

curl -H "Host: nginx.mycluster.com" http://192.168.3.200/
                                        ↑
                      Ingress Controller 看 Host 头  
                      "nginx.mycluster.com" → 转发到 my-nginx:80
```

**一个 IP + 一个端口，通过 HTTP Host 头部区分流量**——这是 Ingress 的核心能力。

### 4.3 加上 HTTPS 后的流转

```
 ① 浏览器输入 https://nginx.mycluster.com
         │
 ② DNS → 192.168.3.200
 ③ TCP 连接：192.168.3.200:443（HTTPS）
         │
 ④-⑤ 同上，进入 Ingress Controller Pod
         │
 ⑥ TLS 终结就在这一步：
    Ingress Controller 用你的证书解密 HTTPS
    → 读到真实的 HTTP 请求：Host: nginx.mycluster.com, GET /
         │
 ⑦-⑨ 后续跟 HTTP 完全一致（在集群内部走 HTTP）
         │
 ⑩ 响应加密返回给浏览器
```

**TLS 只在入口处处理一次**，内部流量全走 HTTP。这个模式叫 **TLS Termination**。证书只放在 Ingress 一个地方，不用每个后端服务都配。

---

## 五、你的集群实际操作

### 5.1 安装 ingress-nginx

```bash
# 在 k8s-master 上执行（会被 MetalLB 分配 External IP）
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.9.5/deploy/static/provider/cloud/deploy.yaml

# 把它的 Service 改成 LoadBalancer
kubectl patch svc ingress-nginx-controller -n ingress-nginx \
  -p '{"spec":{"type":"LoadBalancer"}}'

# 看分配的 External IP
kubectl get svc ingress-nginx-controller -n ingress-nginx
# EXTERNAL-IP: 192.168.3.201
```

### 5.2 创建 Ingress 规则

假设你已经有 `my-nginx` 和 `my-api` 两个 Service。

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: my-ingress
spec:
  ingressClassName: nginx
  rules:
  - host: nginx.mycluster.local
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: my-nginx
            port:
              number: 80
  - host: api.mycluster.local
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: my-api
            port:
              number: 8080
```

### 5.3 配置本地 DNS

在你的 Windows 上，编辑 `C:\Windows\System32\drivers\etc\hosts`（管理员权限）：

```
192.168.3.201  nginx.mycluster.local
192.168.3.201  api.mycluster.local
```

### 5.4 访问

```
浏览器打开 http://nginx.mycluster.local   → my-nginx   (80)
浏览器打开 http://api.mycluster.local     → my-api     (8080)
```

同一个 IP `192.168.3.201`，两个完全不同的后端服务。

### 5.5 再加一个服务？一条规则的事

```yaml
# 在同一个 Ingress 里加一个 host
  - host: admin.mycluster.local
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: my-admin
            port:
              number: 80
```

```bash
kubectl apply -f my-ingress.yaml   # 热更新，不需要重启任何东西
```

---

## 六、MetalLB + Ingress 的协作关系

```
                         外部用户
                            │
                            │ https://nginx.mycluster.com
                            ▼
              ┌──────────────────────────┐
              │     MetalLB VIP           │  ← 永远固定的 IP
              │     192.168.3.200         │     Pod 飘了它跟着飘
              └────────────┬─────────────┘
                           │
              ┌────────────▼─────────────┐
              │   Ingress Controller      │  ← 看 Host 头，分流转发
              │   (nginx / HAProxy / ...) │     一个 IP 承载 N 个域名
              └──┬─────────┬─────────┬───┘
                 │         │         │
           nginx.com   api.com   admin.com
              (Pod)      (Pod)     (Pod)
```

| 角色 | MetalLB | Ingress Controller |
|------|---------|-------------------|
| 解决的问题 | **IP 固定** | **域名路由** |
| 工作层 | L2/L3（ARP/BGP） | L7（HTTP Host/Path） |
| 依赖关系 | 不依赖 Ingress | **依赖** MetalLB 提供固定入口 |
| 一个就够了？ | 一个 MetalLB 服务整个集群 | 一个 Ingress 服务所有 HTTP 域名 |
| 如果没了 | 没有固定 IP，又回到 NodePort | 每个服务需要独立 IP，又回到 IP 不够用 |

**MetalLB 是地基，Ingress 是建筑物。** 地基保证入口稳定，建筑物负责内部调度。

---

## 七、你现有的 Dashboard 也可以走 Ingress

你的 Dashboard 目前走 NodePort `https://192.168.3.100:30443`。加上 Ingress 后：

```yaml
  - host: dashboard.k8s.local
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: kubernetes-dashboard
            port:
              number: 443
```

访问 `https://dashboard.k8s.local`，跟 nginx、api 共用同一个 IP，统一管理。

---

## 八、总结

```
问题层次              解决方案              你得到了什么
─────────────────────────────────────────────────────────
Pod 没固定 IP         K8s Service           内部稳定端点
没有外部固定入口      MetalLB               对外固定 VIP
N 个服务挤一个 IP     Ingress Controller    域名/路径路由
HTTPS 证书管理        cert-manager          自动签发/续期证书
                                      ↑
                                   还没装，但值得
```

从下到上，一层解决一个问题，一层都不多余。
