# Docker RabbitMQ 集群配置指南（经典集群模式）

## 一、环境说明

| 项目 | 值 |
|------|-----|
| RabbitMQ 版本 | 3.12-management |
| 管理用户 | admin |
| 密码 | 123456 |
| 节点 1 | AMQP: 5672 / 管理界面: 15672 |
| 节点 2 | AMQP: 5673 / 管理界面: 15673 |
| 节点 3 | AMQP: 5674 / 管理界面: 15674 |

> **重要**：经典集群模式只共享元数据（交换机、队列定义、绑定关系），**队列消息内容不会自动复制**到其他节点。请根据需求选择高可用方案。

---

## 二、经典集群 vs 镜像队列 vs Quorum Queue

| 特性 | 经典集群 | 镜像队列 | Quorum Queue（推荐） |
|------|---------|---------|---------------------|
| 元数据共享 | ✅ | ✅ | ✅ |
| 消息数据复制 | ❌ | ✅ 手动策略 | ✅ 自动 Raft |
| 故障自动切换 | ❌ | ✅ 需要策略 | ✅ 自动选举 |
| 性能 | 高 | 较低 | 中等 |
| 数据一致性 | — | 最终一致 | 强一致（Raft） |
| RabbitMQ 3.8+ | ✅ | ✅ | ✅ |

---

## 三、快速启动

```bash
sudo bash rabbitmq_cluster_start.sh
```

---

## 四、高可用方案选择

### 方案 1：Quorum Queue（推荐，RabbitMQ 3.8+）

声明队列时指定类型：

**Spring Boot:**
```java
@Bean
public Queue quorumQueue() {
    return QueueBuilder.durable("my.queue")
        .quorum()  // 关键：指定 quorum 类型
        .build();
}
```

**纯命令行:**
```bash
docker exec rabbitmq-c1 rabbitmqadmin declare queue \
  name=my.queue durable=true \
  arguments='{"x-queue-type":"quorum"}'
```

**Quorum Queue 特性：**
- 基于 Raft 共识算法，数据强一致
- 自动 Leader 选举，故障时自动切换
- 推荐集群规模 3 或 5 节点
- 写入需要超过半数节点确认

### 方案 2：镜像队列（传统方案）

需要额外配置策略，见 `rabbitmq_mirrored_queue_start.sh`：
```bash
rabbitmqctl set_policy ha-all "^" \
  '{"ha-mode":"all","ha-sync-mode":"automatic"}'
```

### 方案 3：应用层处理

- 生产者发送消息到多个队列
- 使用 publisher confirm + 消息持久化
- 消费者做幂等处理

---

## 五、Spring Boot 配置

```yaml
spring:
  rabbitmq:
    addresses: localhost:5672,localhost:5673,localhost:5674
    username: admin
    password: 123456
    virtual-host: /
    publisher-confirm-type: correlated
    publisher-returns: true
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 1
    template:
      mandatory: true
```

---

## 六、验证集群

```bash
# 查看集群状态
docker exec rabbitmq-c1 rabbitmqctl cluster_status

# 查看节点
docker exec rabbitmq-c1 rabbitmqctl list_nodes

# 管理界面
# http://localhost:15672
```

---

## 七、集群节点管理

### 7.1 查看节点状态

```bash
# 磁盘告警阈值
docker exec rabbitmq-c1 rabbitmqctl status | grep -A 5 "Disk"

# 内存告警阈值
docker exec rabbitmq-c1 rabbitmqctl status | grep -A 5 "Memory"

# 修改磁盘空闲告警阈值
docker exec rabbitmq-c1 rabbitmqctl set_disk_free_limit "1GB"

# 修改内存告警阈值
docker exec rabbitmq-c1 rabbitmqctl set_vm_memory_high_watermark 0.4
```

### 7.2 暂停/恢复节点

```bash
# 暂停节点（维护模式）
docker exec rabbitmq-c2 rabbitmqctl suspend_listeners

# 恢复节点
docker exec rabbitmq-c2 rabbitmqctl resume_listeners
```

### 7.3 从集群移除节点

```bash
# 在要移除的节点上
docker exec rabbitmq-c3 rabbitmqctl stop_app
docker exec rabbitmq-c3 rabbitmqctl reset

# 在主节点上忘记该节点
docker exec rabbitmq-c1 rabbitmqctl forget_cluster_node rabbit@rabbitmq-c3
```

### 7.4 重新加入集群

```bash
docker exec rabbitmq-c3 bash -c "
  rabbitmqctl stop_app && \
  rabbitmqctl reset && \
  rabbitmqctl join_cluster rabbit@rabbitmq-c1 && \
  rabbitmqctl start_app
"
```

---

## 八、常用管理命令

```bash
# ===== 集群相关 =====
docker exec rabbitmq-c1 rabbitmqctl cluster_status           # 集群状态
docker exec rabbitmq-c1 rabbitmqctl list_nodes               # 节点列表

# ===== 队列相关 =====
docker exec rabbitmq-c1 rabbitmqctl list_queues               # 队列列表
docker exec rabbitmq-c1 rabbitmqctl list_queues name messages consumers  # 详细信息
docker exec rabbitmq-c1 rabbitmqctl purge_queue <queue-name>  # 清空队列

# ===== 交换机相关 =====
docker exec rabbitmq-c1 rabbitmqctl list_exchanges            # 交换机列表

# ===== 绑定相关 =====
docker exec rabbitmq-c1 rabbitmqctl list_bindings             # 绑定列表

# ===== 连接和通道 =====
docker exec rabbitmq-c1 rabbitmqctl list_connections          # 连接列表
docker exec rabbitmq-c1 rabbitmqctl list_channels             # 通道列表
docker exec rabbitmq-c1 rabbitmqctl close_connection "<conn>" # 关闭连接

# ===== 用户管理 =====
docker exec rabbitmq-c1 rabbitmqctl list_users                # 用户列表
docker exec rabbitmq-c1 rabbitmqctl add_user app app123       # 添加用户
docker exec rabbitmq-c1 rabbitmqctl set_user_tags app administrator  # 设置管理员
docker exec rabbitmq-c1 rabbitmqctl set_permissions -p / app ".*" ".*" ".*"  # 设置权限

# ===== vhost 管理 =====
docker exec rabbitmq-c1 rabbitmqctl list_vhosts               # vhost 列表
docker exec rabbitmq-c1 rabbitmqctl add_vhost /myapp          # 创建 vhost
docker exec rabbitmq-c1 rabbitmqctl delete_vhost /myapp       # 删除 vhost

# ===== 策略管理 =====
docker exec rabbitmq-c1 rabbitmqctl list_policies             # 策略列表
docker exec rabbitmq-c1 rabbitmqctl clear_policy <policy>     # 删除策略

# ===== 插件管理 =====
docker exec rabbitmq-c1 rabbitmq-plugins list                 # 插件列表
docker exec rabbitmq-c1 rabbitmq-plugins enable rabbitmq_shovel  # 启用插件
```

---

## 九、监控与告警

```bash
# 队列消息堆积监控
docker exec rabbitmq-c1 rabbitmqctl list_queues name messages messages_ready messages_unacknowledged

# 查看所有连接
docker exec rabbitmq-c1 rabbitmqctl list_connections name user state channels

# 查看资源使用
docker exec rabbitmq-c1 rabbitmqctl status | grep -E "disk_free|memory"

# Prometheus 监控（可选）
docker exec rabbitmq-c1 rabbitmq-plugins enable rabbitmq_prometheus
# 之后访问 http://localhost:15672/metrics
```

---

## 十、故障排除

### 问题1：节点启动后未加入集群

```bash
# 检查 Erlang Cookie
docker exec rabbitmq-c1 cat /var/lib/rabbitmq/.erlang.cookie > /tmp/c1
docker exec rabbitmq-c2 cat /var/lib/rabbitmq/.erlang.cookie > /tmp/c2
diff /tmp/c1 /tmp/c2  # 应该一致

# 检查网络
docker exec rabbitmq-c2 ping rabbitmq-c1
```

### 问题2：磁盘空间不足告警

```bash
# 降低磁盘告警阈值
docker exec rabbitmq-c1 rabbitmqctl set_disk_free_limit "500MB"
```

### 问题3：节点数据不一致

```bash
# 彻底重置某节点
docker exec rabbitmq-c3 rabbitmqctl stop_app
docker exec rabbitmq-c3 rabbitmqctl reset
docker exec rabbitmq-c3 rabbitmqctl start_app
# 然后重新加入集群
```

### 完全重置

```bash
docker stop rabbitmq-c1 rabbitmq-c2 rabbitmq-c3
docker rm rabbitmq-c1 rabbitmq-c2 rabbitmq-c3
docker network rm rabbitmq-cluster-net
rm -rf /root/rabbitmq-cluster-docker
sudo bash rabbitmq_cluster_start.sh
```

---

## 十一、与镜像队列方案对比

如果你需要队列消息自动跨节点复制，请使用镜像队列方案：

```bash
# 回到 redis 目录旁边的 rabbitmq 目录
cd /home/hulei/work/src/main/java/work/N3rabbitmq

# 使用镜像队列脚本替代
sudo bash rabbitmq_mirrored_queue_start.sh
```

两者区别：
- **经典集群**：元数据共享，消息不复制，性能最好，适合对高可用要求不高的场景
- **镜像队列**：消息自动复制到其他节点，高可用但性能损耗约 30-50%
- **Quorum Queue**：新方案，比镜像队列更安全，推荐 RabbitMQ 3.8+ 使用
