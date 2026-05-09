# Docker & Kubernetes 30天面试突击指南 (☁️ 云原生基础设施专家版)

> **总纲**：本指南深度聚焦于 **Docker & Kubernetes (K8s)**。在云原生时代，容器化与编排能力是高级开发的标配。我们将从 Docker 的隔离原理出发，全面攻克 K8s 的核心组件、控制器、网络模型（CNI）、存储卷（CSI）、安全机制（RBAC）、调度策略、Helm 包管理以及生产环境下的故障排查与性能调优。
> 
> **学习路径**：
> 1. **Week 1 (1-7天)**：Docker 核心原理：隔离、镜像、存储与网络。
> 2. **Week 2 (8-14天)**：K8s 架构与核心组件：Pod、Service、控制器与资源管理。
> 3. **Week 3 (15-21天)**：K8s 高级资源：Ingress、ConfigMap/Secret、存储（PV/PVC）与网络深度解析。
> 4. **Week 4 (22-26天)**：K8s 运维与安全：调度策略、HPA、RBAC 安全、监控与日志（Prometheus/EFK）。
> 5. **Week 5 (27-30天)**：云原生实战与架构演进：Helm、Operator、CI/CD 集成与地狱级故障排查。

---

## 📅 第一阶段：Docker 核心原理 (筑基)

### 第1天：容器与虚拟机的区别及 Docker 架构
#### ### 面试题
1. **基础**：请简述容器（Container）与虚拟机（VM）的本质区别。
2. **中级**：Docker 的核心架构组件有哪些？（Client, Daemon, Image, Container, Registry）。
3. **高级**：Docker 是如何实现“一次构建，到处运行”的？（联合文件系统与运行时隔离）。
4. **源码**：Docker Daemon (dockerd) 与 containerd、runC 之间的调用关系是什么？
5. **地狱级**：如果容器内的进程以 root 权限运行，它与宿主机的 root 有什么区别？如何通过 User Namespace 实现进一步隔离？

#### ### 编程题
编写一个简单的 Shell 脚本，模拟 Docker 的“容器运行”过程（利用 `chroot` 和 `unshare` 命令实现简单的文件系统和命名空间隔离）。

---

### 第2天：Docker 隔离机制：Namespace 与 Cgroups
#### ### 面试题
1. **基础**：什么是 Namespace？Docker 使用了哪些 Namespace？（PID, NET, IPC, UTS, MNT, USER）。
2. **中级**：什么是 Cgroups（Control Groups）？它在 Docker 中起到什么作用？
3. **高级**：如果一个容器消耗了过多的 CPU，如何通过 Cgroups 进行限制？
4. **源码**：在 Linux 内核中，Namespace 是如何通过 `clone()` 系统调用实现的？
5. **地狱级**：如何解决“容器逃逸”问题？（提到内核漏洞利用、不安全挂载如 `/var/run/docker.sock`）。

#### ### 编程题
使用 `docker run` 的参数（--cpu-shares, --memory）限制一个容器的资源，并使用 `cgexec` 或查看 `/sys/fs/cgroup` 验证限制是否生效。

---

### 第3天：Docker 镜像原理：UnionFS 与 Layer
#### ### 面试题
1. **基础**：Docker 镜像是什么？它和容器的关系是什么？
2. **中级**：什么是联合文件系统（UnionFS）？常用的存储驱动有哪些？（Overlay2, AUFS）。
3. **高级**：为什么 Docker 镜像采用分层结构？（分层下载、写时复制 CoW）。
4. **源码**：当你在容器中修改一个文件时，Overlay2 驱动是如何处理“写”操作的？（Upper/Lower 目录）。
5. **地狱级**：如何实现镜像瘦身？请列举至少 5 种优化 Dockerfile 的方案。

#### ### 编程题
编写一个多阶段构建（Multi-stage Build）的 Dockerfile，将一个 Java 应用（Spring Boot）从源码编译到最终极小镜像（如使用 Alpine 或 Distroless）。

---

### 第4天：Docker 网络模型深度解析
#### ### 面试题
1. **基础**：Docker 默认的四种网络模式是什么？（Bridge, Host, Container, None）。
2. **中级**：Docker Bridge 模式下，容器是如何与外界通信的？（veth pair + docker0 网桥 + NAT）。
3. **高级**：什么是 Overlay 网络？它在多机通信中是如何实现的？（VXLAN 封装）。
4. **源码**：Libnetwork 插件化架构的设计思路。
5. **地狱级**：如何解决 Docker 容器重启后 IP 变化导致的通信问题？（内置 DNS 发现机制）。

#### ### 编程题
手动创建一个 Docker 自定义 Bridge 网络，并启动两个容器，验证它们是否可以通过容器名互相 ping 通。

---

### 第5天：Docker 存储：Volume 与 Bind Mount
#### ### 面试题
1. **基础**：Docker 存储持久化的三种方式。
2. **中级**：Volume 和 Bind Mount 的区别。
3. **高级**：什么是“匿名卷”和“具名卷”？它们在 `docker-compose` 中如何定义？
4. **源码**：Docker 是如何将宿主机目录挂载到容器内部的？（Mount Namespace 的传播）。
5. **地狱级**：在生产环境下，如何实现容器存储的高可用（如集成 NFS, Ceph 或云盘）？

#### ### 编程题
编写一个 `docker-compose.yml` 文件，部署一个包含 MySQL 和 Redis 的 Web 应用，并确保数据库数据在容器重启后不丢失。

---

### 第6天：Dockerfile 最佳实践与安全加固
#### ### 面试题
1. **基础**：常用的 Dockerfile 指令有哪些？（FROM, RUN, CMD, ENTRYPOINT, COPY, ADD）。
2. **中级**：CMD 和 ENTRYPOINT 的区别与组合使用场景。
3. **高级**：为什么不建议在 Dockerfile 中使用 `ADD` 远程 URL？为什么 `COPY` 更好？
4. **地狱级**：如何扫描 Docker 镜像中的安全漏洞？（提到 Trivy 或 Clair）。

#### ### 编程题
重写一个不安全的 Dockerfile（如：root 运行、包含敏感信息、镜像层过多），将其重构为符合生产安全标准的版本。

---

### 第7天：第一周复盘：Docker 深度总结
#### ### 面试题
1. **综合**：请描述 `docker run hello-world` 命令执行后的完整链路（从 Client 到 Kernel）。
2. **架构**：如何设计一个高可用的私有镜像仓库（Harbor）架构？
3. **地狱级**：如果容器启动报错 `no space left on device`，但宿主机磁盘充足，你应该排查哪些地方？（Inode 耗尽、overlay2 占用过大等）。

#### ### 编程题
总结一份 Docker 常用排障命令清单（logs, inspect, stats, top, exec, history）。

---

## 📅 第二阶段：Kubernetes 核心与控制器 (进阶)

### 第8天：K8s 架构：控制平面与工作节点
#### ### 面试题
1. **基础**：K8s 的主要组件有哪些？（Etcd, API Server, Scheduler, Controller Manager, Kubelet, Kube-proxy）。
2. **中级**：API Server 在集群中扮演什么角色？为什么它是唯一的入口？
3. **高级**：Etcd 的一致性算法（Raft）对 K8s 集群规模有什么限制？
4. **源码**：Kubelet 是如何通过声明式 API (Watch 机制) 发现并创建 Pod 的？
5. **地狱级**：如果 API Server 挂了，正在运行的容器会受到影响吗？如果 Kubelet 挂了呢？

#### ### 编程题
使用 `kubectl get nodes -o json` 命令解析集群节点状态，并编写一段 Python 脚本统计所有节点的 CPU/内存使用总量。

---

### 第9天：Pod 的生命周期与调度基础
#### ### 面试题
1. **基础**：什么是 Pod？为什么它是 K8s 的最小调度单位？
2. **中级**：Pod 内的多个容器是如何共享网络和存储的？（Pause 容器）。
3. **高级**：请描述 Pod 的生命周期状态转换（Pending, Running, Succeeded, Failed, Unknown）。
4. **源码**：Init 容器和 Ephemeral 容器的作用与区别。
5. **地狱级**：什么是“静态 Pod（Static Pod）”？它是如何绕过 API Server 直接运行的？

#### ### 编程题
编写一个包含 `initContainers` 的 Pod 定义文件，要求 Init 容器检查数据库是否在线，在线后才启动主业务容器。

---

### 第10天：Service：服务发现与负载均衡
#### ### 面试题
1. **基础**：Service 的三种主要类型（ClusterIP, NodePort, LoadBalancer）。
2. **中级**：Kube-proxy 的三种模式（Userspace, Iptables, IPVS）的区别与优劣。
3. **高级**：Service 是如何通过 Label Selector 找到 Pod 的？（Endpoints/EndpointSlice）。
4. **源码**：Headless Service 的作用是什么？在什么场景下使用？
5. **地狱级**：在高并发场景下，Iptables 模式为什么会出现性能瓶颈？IPVS 是如何优化的？

#### ### 编程题
创建一个 NodePort 类型的 Service，并使用 `iptables -t nat -L` 命令在节点上观察生成的转发规则。

---

### 第11天：无状态控制器：Deployment 与 ReplicaSet
#### ### 面试题
1. **基础**：Deployment 和 ReplicaSet 的关系。
2. **中级**：Deployment 是如何实现“滚动更新（Rolling Update）”的？
3. **高级**：如何实现版本回滚（Rollback）？K8s 是如何保留历史版本的？
4. **源码**：Deployment 控制器是如何通过 `ownerReference` 级联管理资源的？
5. **地狱级**：在滚动更新过程中，如何保证业务零停机（Zero Downtime）？（提到 Readiness Probe）。

#### ### 编程题
演示一次 Deployment 的滚动更新过程，并设置 `maxSurge` 和 `maxUnavailable` 参数来控制更新速率。

---

### 第12天：有状态控制器：StatefulSet 原理
#### ### 面试题
1. **基础**：StatefulSet 与 Deployment 的核心区别。
2. **中级**：StatefulSet 是如何保证 Pod 的网络标识（Hostname）不变的？
3. **高级**：StatefulSet 在更新时遵循什么顺序？（序号从大到小）。
4. **源码**：什么是“级联删除”与“非级联删除”在 StatefulSet 中的应用？
5. **地狱级**：如何使用 StatefulSet 部署一个高可用的 Redis 或 MySQL 集群？

#### ### 编程题
部署一个包含 3 个副本的 StatefulSet，观察 Pod 的名称（pod-0, pod-1, pod-2）及其挂载卷的绑定情况。

---

### 第13天：其他控制器：DaemonSet 与 Job/CronJob
#### ### 面试题
1. **基础**：DaemonSet 的应用场景有哪些？（日志收集、监控代理）。
2. **中级**：Job 和 CronJob 的区别。
3. **高级**：如何确保 DaemonSet 在所有节点（包括有污点的节点）上运行？
4. **源码**：Job 控制器是如何通过 `backoffLimit` 处理任务失败重试的？
5. **地狱级**：如果一个 Job 运行时间过长，如何设置自动清理策略？（TTL 控制器）。

#### ### 编程题
编写一个 CronJob，每分钟运行一个 Busybox 容器输出当前时间，并保留最后 3 次成功的历史记录。

---

### 第14天：第二周复盘：K8s 核心组件总结
#### ### 面试题
1. **综合**：请完整描述一个 Pod 从 `kubectl apply` 到在节点上运行的整个“调度与创建”链路。
2. **架构**：如何实现 K8s 控制平面的高可用架构？
3. **地狱级**：如果 Etcd 数据损坏，你该如何进行灾难恢复？

#### ### 编程题
总结一份 `kubectl` 常用高级命令手册（如：jsonpath, custom-columns, patch, label, annotate）。

---

## 📅 第三阶段：存储、网络与配置 (深度进阶)

### 第15天：K8s 存储架构：PV, PVC 与 StorageClass
#### ### 面试题
1. **基础**：PV 和 PVC 的关系（类似于接口与实现）。
2. **中级**：什么是动态存储卷供应（Dynamic Provisioning）？StorageClass 的作用。
3. **高级**：PV 的回收策略（Retain, Delete, Recycle）的区别。
4. **源码**：CSI (Container Storage Interface) 是如何让 K8s 支持多种存储后端而无需修改核心代码的？
5. **地狱级**：当 Pod 被删除后，PVC 还存在吗？如果重新创建一个同名的 PVC，它能挂载回原来的 PV 吗？

#### ### 编程题
配置一个基于本地目录（Local Persistent Volume）的存储实验，手动创建 PV、PVC 并挂载到 Pod 中。

---

### 第16天：K8s 网络模型：CNI 与 Pod 间通信
#### ### 面试题
1. **基础**：K8s 网络的三大原则（Pod 间直连、Node 到 Pod 直连、Pod 看到自己的 IP 是一致的）。
2. **中级**：常见的 CNI 插件有哪些？（Flannel, Calico, Cilium）。
3. **高级**：Flannel 的 UDP 模式与 Host-GW 模式的区别。
4. **源码**：Calico 是如何通过 BGP 协议实现跨主机通信的？
5. **地狱级**：什么是“网络策略（Network Policy）”？它是如何实现租户隔离和微服务防火墙的？

#### ### 编程题
安装并配置一个简单的网络策略，禁止特定命名空间以外的所有 Pod 访问当前命名空间的数据库服务。

---

### 第17天：Ingress：七层负载均衡与反向代理
#### ### 面试题
1. **基础**：Ingress 和 Service (NodePort/LoadBalancer) 的关系。
2. **中级**：常用的 Ingress Controller 有哪些？（Nginx Ingress, Traefik, Istio Ingress）。
3. **高级**：如何实现基于路径（Path）和域名（Host）的路由转发？
4. **源码**：Nginx Ingress Controller 是如何将 Ingress 资源转化为 nginx.conf 配置并热更新的？
5. **地狱级**：如何在 Ingress 中配置 SSL 证书自动续期（利用 Cert-Manager）？

#### ### 编程题
部署一个 Nginx Ingress Controller，并配置两个域名（a.com, b.com）分别指向后端不同的服务。

---

### 第18天：配置管理：ConfigMap 与 Secret
#### ### 面试题
1. **基础**：ConfigMap 和 Secret 的区别与用法。
2. **中级**：Secret 真的安全吗？（Base64 编码与静态加密）。
3. **高级**：Pod 挂载 ConfigMap 后，修改 ConfigMap 内容，容器内的文件会同步更新吗？
4. **源码**：如何通过环境变量和卷挂载两种方式引用 ConfigMap？
5. **地狱级**：在大厂环境下，如何集成外部密钥管理系统（如 HashiCorp Vault）到 K8s？

#### ### 编程题
创建一个包含 Redis 配置的 ConfigMap，并以文件挂载的方式映射到 Redis 容器的 `/etc/redis/redis.conf`。

---

### 第19天：探针机制：健康检查与自愈
#### ### 面试题
1. **基础**：K8s 的三类探针（Liveness, Readiness, Startup）。
2. **中级**：探针支持哪三种检查方式？（HTTPGet, TCPSocket, Exec）。
3. **高级**：Readiness 失败和 Liveness 失败后，K8s 的行为有何不同？
4. **地狱级**：如何针对 Java 应用的“假死”或“启动慢”配置合理的探针参数？

#### ### 编程题
编写一个 Pod 定义，模拟主容器在启动 30 秒后进入“Ready”状态，并在 60 秒后“Liveness”失败导致重启。

---

### 第20天：资源限制与 QoS 等级
#### ### 面试题
1. **基础**：什么是 Requests 和 Limits？
2. **中级**：K8s 的三类 QoS 等级（Guaranteed, Burstable, BestEffort）是如何划分的？
3. **高级**：当节点内存不足时，K8s 会优先驱逐哪类 QoS 的 Pod？
4. **地狱级**：什么是“OOM Kill”？为什么 Limits 设置得太小会导致容器频繁重启？

#### ### 编程题
给 Pod 设置资源限制，并使用压测工具（如 `stress`）故意超出 Limits，观察容器的终止状态（OOMKilled）。

---

### 第21天：第三周复盘：存储网络配置总结
#### ### 面试题
1. **综合**：请描述一个请求从互联网到达 Pod 的全路径（Ingress -> Service -> Endpoints -> Pod）。
2. **架构**：如何设计一个跨地域（Multi-Region）的 K8s 网络方案？
3. **地狱级**：如果 PVC 挂载超时，你的排查步骤是什么？

#### ### 编程题
总结一份 K8s 资源配置清单（YAML）编写规范指南。

---

## 📅 第四阶段：运维、安全与调度 (进阶核心)

### 第22天：K8s 调度策略：亲和性与污点
#### ### 面试题
1. **基础**：NodeSelector 的用法。
2. **中级**：节点亲和性（NodeAffinity）与 Pod 亲和性（PodAffinity/AntiAffinity）的区别。
3. **高级**：什么是 Taints（污点）和 Tolerations（容忍度）？
4. **源码**：K8s 调度器（Scheduler）的两阶段流程：预选（Predicates）与优选（Priorities）。
5. **地狱级**：如何实现“Pod 尽量分布在不同的可用区”？（TopologySpreadConstraints）。

#### ### 编程题
给特定节点打上污点，并部署一个 Pod，通过设置容忍度确保 Pod 能够调度到该节点上。

---

### 第23天：自动扩缩容：HPA 与 VPA
#### ### 面试题
1. **基础**：什么是 HPA (Horizontal Pod Autoscaler)？
2. **中级**：HPA 是基于什么指标进行扩缩容的？（CPU, 内存, 自定义指标）。
3. **高级**：HPA 的扩容算法是什么？（期望副本数计算公式）。
4. **源码**：Metrics Server 在扩缩容中起什么作用？
5. **地狱级**：什么是 VPA (Vertical Pod Autoscaler)？为什么它在生产环境中使用较少？

#### ### 编程题
为一个 Deployment 配置 HPA，利用 `ab` 工具模拟流量冲击，观察副本数从 1 自动扩展到 10 的过程。

---

### 第24天：RBAC 安全权限管理
#### ### 面试题
1. **基础**：什么是 RBAC？它的四个核心资源（Role, ClusterRole, RoleBinding, ClusterRoleBinding）。
2. **中级**：Role 和 ClusterRole 的区别。
3. **高级**：什么是 ServiceAccount？它和 User 的区别。
4. **地狱级**：如何限制一个特定用户只能在特定命名空间内查看 Pod 列表，但不能删除？

#### ### 编程题
创建一个 ServiceAccount，并绑定一个只读权限的 Role，然后尝试使用该 SA 的 Token 调用 API Server。

---

### 第25天：监控与日志：Prometheus 与 EFK
#### ### 面试题
1. **基础**：Prometheus 监控 K8s 的核心架构（Pull 模式）。
2. **中级**：EFK (Elasticsearch, Fluentd, Kibana) 是如何收集容器日志的？
3. **高级**：什么是 Node Exporter 和 Kube-state-metrics？
4. **地狱级**：如何配置 Prometheus 自动发现集群中的所有 Service？

#### ### 编程题
部署一个简单的 Prometheus 实例（或使用 Prometheus Operator），并展示一个 Grafana 面板监控 Pod 的内存指标。

---

### 第26天：第四周复盘：运维安全总结
#### ### 面试题
1. **综合**：总结提升 K8s 集群安全性（Security Hardening）的 5 个关键措施。
2. **架构**：如何设计一个支持灰度发布（Canary Release）的流量切换方案？
3. **地狱级**：描述一次真实的 K8s 集群大规模崩溃后的恢复过程（Post-mortem）。

---

## 📅 第五阶段：实战演练与地狱难题 (终极)

### 第27天：Helm：K8s 的包管理器
#### ### 面试题
1. **基础**：为什么需要 Helm？
2. **中级**：Helm 的三大核心概念（Chart, Repo, Release）。
3. **高级**：Helm 3 相比 Helm 2 的重大改进（去除了 Tiller）。
4. **地狱级**：如何编写一个自定义 Chart？如何使用 Go Template 处理复杂的配置文件？

#### ### 编程题
使用 Helm 部署一个标准的 Redis 集群，并演示如何使用 `helm upgrade` 进行平滑配置更新。

---

### 第28天：Operator 与 CRD：扩展 K8s 能力
#### ### 面试题
1. **基础**：什么是 CRD (Custom Resource Definition)？
2. **中级**：什么是 Operator 模式？它解决了什么问题？（运维知识代码化）。
3. **高级**：常用的 Operator SDK 或框架（Kubebuilder, Operator SDK）。
4. **地狱级**：描述一个 Operator 处理资源的“控制循环（Reconciliation Loop）”过程。

#### ### 编程题
查看集群中已安装的 CRD，并尝试理解一个成熟 Operator（如 Prometheus Operator）定义的资源结构。

---

### 第29天：K8s 故障排查：地狱级场景复现
#### ### 面试题
1. **场景**：Pod 一直处于 `CrashLoopBackOff`，该如何排查？
2. **场景**：Pod 无法被调度，提示 `MatchNodeSelector` 失败。
3. **场景**：Service 访问不通，但是 Pod 运行正常，IP 可达。
4. **场景**：节点状态为 `NotReady`，DiskPressure。
5. **地狱级**：集群出现网络抖动，怀疑是 CNI 插件性能问题，你如何抓包分析？（利用 `nsenter` 进入网络命名空间）。

#### ### 编程题
模拟一个 Pod 启动失败（容器退出码 137 或 1），并使用 `kubectl describe` 和 `kubectl logs --previous` 找到原因。

---

### 第30天：终极实战：从零设计一个金融级高可用 K8s 集群架构
#### ### 任务描述
1. **综合设计**：设计一个跨 3 个可用区的 K8s 集群，包含：多 Master 高可用、外部存储集成、双 Ingress 隔离、全链路链路追踪、自动备份与审计。要求画出详细的架构拓扑图。
2. **总教官面试**：回答终极难题：“云原生（Cloud Native）的本质是什么？如果你的公司还没有上云，你如何说服 CTO 采用 K8s 而不是传统的虚拟机部署模式？”

---

**总教官寄语**：恭喜你，云原生勇士！30 天的时间，你已经从只会 `docker run` 的小白，蜕变成了能够驾驭 K8s 复杂编排体系的架构师。容器化是通往未来的必经之路，掌握了 K8s，你就掌握了分布式系统的操作系统。去吧，在云端的浪潮中，书写你的高可用传奇！
