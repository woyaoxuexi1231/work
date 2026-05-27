# Docker RabbitMQ 镜像队列集群配置指南

## 一、环境说明

| 项目 | 值 |
|------|-----|
| RabbitMQ 版本 | 3.12-management |
| 管理用户 | admin |
| 密码 | 123456 |
| 节点 1 | AMQP: 5672 / 管理界面: 15672 |
| 节点 2 | AMQP: 5673 / 管理界面: 15673 |
| 节点 3 | AMQP: 5674 / 管理界面: 15674 |
| ha-all 策略 | `^` 匹配所有队列，镜像到全部 3 个节点 |
| ha-two 策略 | `\.two\.` 匹配含 `.two.` 的队列，镜像到 2 个节点 |

---

## 二、快速启动

```bash
# 全新安装
sudo bash rabbitmq_mirrored_queue_start.sh

# 自定义配置
sudo RABBITMQ_DEFAULT_USER=myuser RABBITMQ_DEFAULT_PASS=mypass \
  bash rabbitmq_mirrored_queue_start.sh
```

---

## 三、管理界面

启动后在浏览器访问：
- **http://localhost:15672** （节点 1）
- **http://localhost:15673** （节点 2）
- **http://localhost:15674** （节点 3）

使用 `admin / 123456` 登录。

---

## 四、验证集群状态

### 4.1 命令行验证

```bash
# 查看集群状态
docker exec rabbitmq-node1 rabbitmqctl cluster_status

# 查看镜像队列策略
docker exec rabbitmq-node1 rabbitmqctl list_policies

# 查看节点
docker exec rabbitmq-node1 rabbitmqctl list_nodes
```

正常输出应包含：
```
Running Nodes:
rabbit@rabbitmq-node1
rabbit@rabbitmq-node2
rabbit@rabbitmq-node3
```

### 4.2 验证镜像队列

```bash
# 创建测试队列
docker exec rabbitmq-node1 rabbitmqadmin declare queue name=test.queue durable=true

# 查看队列的镜像信息
docker exec rabbitmq-node1 rabbitmqctl list_queues name node slave_pids synchronised_slave_pids
```

---

## 五、Spring Boot 连接配置

### 完整集群地址（推荐）

```yaml
spring:
  rabbitmq:
    addresses: localhost:5672,localhost:5673,localhost:5674
    username: admin
    password: 123456
    virtual-host: /
    # 发布确认
    publisher-confirm-type: correlated
    publisher-returns: true
    # 消费者
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 1
        concurrency: 3
        max-concurrency: 10
```

### 单节点连接（故障时需切换）

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: admin
    password: 123456
```

---

## 六、镜像队列策略详解

### 6.1 ha-all（默认策略，已配置）

```bash
# 所有队列镜像到全部节点
rabbitmqctl set_policy ha-all "^" \
  '{"ha-mode":"all","ha-sync-mode":"automatic"}'
  
# 优先级: 1（最低，作为默认）
# 适用：所有队列
```

### 6.2 ha-two（按需策略，已配置）

```bash
# 名称含 .two. 的队列镜像到 2 个节点
rabbitmqctl set_policy ha-two "\.two\." \
  '{"ha-mode":"exactly","ha-params":2,"ha-sync-mode":"automatic"}'

# 优先级: 10（高于 ha-all）
# 适用：队列名含 .two. 的，例如 order.two.queue
```

### 6.3 手动为特定队列设置

```bash
# 通过管理界面：Queues -> 选择队列 -> 展开 -> Policies -> Add policy

# 或命令行
rabbitmqctl set_policy ha-specific "^my-special-queue$" \
  '{"ha-mode":"all","ha-sync-mode":"automatic"}'
```

---

## 七、高可用测试

### 7.1 停止一个节点

```bash
# 停止节点 1
docker stop rabbitmq-node1
```

### 7.2 观察行为

- 生产者和消费者可以通过节点 2（5673）或节点 3（5674）继续工作
- 镜像队列在剩余节点上仍有完整数据
- 管理界面访问其他节点: http://localhost:15673

### 7.3 恢复节点

```bash
docker start rabbitmq-node1
# 等待节点重新加入集群，镜像自动同步
docker exec rabbitmq-node1 rabbitmqctl cluster_status
```

---

## 八、常用管理命令

```bash
# 查看集群状态
docker exec rabbitmq-node1 rabbitmqctl cluster_status

# 查看所有队列
docker exec rabbitmq-node1 rabbitmqctl list_queues name messages consumers

# 查看所有交换机
docker exec rabbitmq-node1 rabbitmqctl list_exchanges

# 查看所有绑定
docker exec rabbitmq-node1 rabbitmqctl list_bindings

# 查看策略
docker exec rabbitmq-node1 rabbitmqctl list_policies

# 删除策略
docker exec rabbitmq-node1 rabbitmqctl clear_policy ha-all

# 查看用户
docker exec rabbitmq-node1 rabbitmqctl list_users

# 创建 vhost
docker exec rabbitmq-node1 rabbitmqctl add_vhost /myapp

# 设置 vhost 权限
docker exec rabbitmq-node1 rabbitmqctl set_permissions -p /myapp admin ".*" ".*" ".*"

# 查看连接
docker exec rabbitmq-node1 rabbitmqctl list_connections

# 全局统计
docker exec rabbitmq-node1 rabbitmqctl status
```

---

## 九、从集群中移除/添加节点

### 9.1 移除节点

```bash
# 在要移除的节点上执行
docker exec rabbitmq-node3 rabbitmqctl stop_app
docker exec rabbitmq-node3 rabbitmqctl reset
# 此时节点 3 已脱离集群

# 可选：在集群主节点忘记该节点
docker exec rabbitmq-node1 rabbitmqctl forget_cluster_node rabbit@rabbitmq-node3
```

### 9.2 重新加入节点

```bash
docker exec rabbitmq-node3 bash -c "
  rabbitmqctl stop_app && \
  rabbitmqctl reset && \
  rabbitmqctl join_cluster rabbit@rabbitmq-node1 && \
  rabbitmqctl start_app
"
```

---

## 十、故障排除

### 问题1：节点无法加入集群

```bash
# 检查 Erlang Cookie 是否一致
docker exec rabbitmq-node1 cat /var/lib/rabbitmq/.erlang.cookie
docker exec rabbitmq-node2 cat /var/lib/rabbitmq/.erlang.cookie
# 必须完全一致

# 检查网络
docker exec rabbitmq-node2 ping rabbitmq-node1
```

### 问题2：管理插件未启用

```bash
docker exec rabbitmq-node1 rabbitmq-plugins enable rabbitmq_management
```

### 问题3：镜像同步慢

```bash
# 查看同步状态
docker exec rabbitmq-node1 rabbitmqctl list_queues name synchronised_slave_pids

# 手动触发同步
docker exec rabbitmq-node1 rabbitmqctl sync_queue <queue-name>
```

### 完全重置

```bash
docker stop rabbitmq-node1 rabbitmq-node2 rabbitmq-node3
docker rm rabbitmq-node1 rabbitmq-node2 rabbitmq-node3
rm -rf /root/rabbitmq-mirror-docker
sudo bash rabbitmq_mirrored_queue_start.sh
```
