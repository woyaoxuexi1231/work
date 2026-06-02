# Federation 联邦插件实战演练

> **核心问题**：两个机房各有一个独立 RabbitMQ 集群，如何让下游机房透明获得上游 Exchange 的消息，同时避免跨机房集群的脑裂风险？

---

## 实战类型：操作文档

**为什么选这种方式**：Federation 是插件启用 + 参数配置 + 行为验证，必须通过命令操作和消息收发来亲眼确认"上游发、下游收"——不是靠代码能展示的。

---

## 操作背景（苦难时代）

你用原生集群跨了两个机房。一天光纤被挖断，集群出现网络分区，`pause_minority` 让少数派节点停服，上游消息堆积，下游消费者全部断连。你凌晨三点对着满屏的分区告警怀疑人生。

更根本的痛苦：你只是想把消息从 A 机房传到 B 机房，却背上了整个集群状态同步的包袱。**为了送一封信，非要修一条跨国高铁。**

---

## 操作目标

用 Federation 在两个 vhost（模拟两个机房）之间建立 Exchange 级别的消息联邦：上游 `upstream/fed.order` 的消息自动到达下游 `downstream/fed.order`。

---

## 环境准备

| 资源 | 说明 |
|---|---|
| 集群 | `sudo bash scripts/01-cluster-setup.sh` |
| Federation 脚本 | `sudo bash scripts/04-federation-setup.sh` |
| 管理界面 | http://192.168.3.100:15672 |

---

## 详细操作步骤

### 步骤 1：运行 Federation 脚本

```bash
sudo bash scripts/04-federation-setup.sh
```

脚本做了 6 件事：启用插件 → 创建两个 vhost → 上游建 Exchange → 定义 Federation 上游 → 设策略 → 下游建同名 Exchange。

### 步骤 2：验证 Federation 链路状态

打开 `http://192.168.3.100:15672` → **Federation** 标签 → **Status**。

应该看到一条 link，状态为 `running`。

```bash
# 或命令行查看
docker exec rabbitmq-node1 rabbitmqctl eval 'rabbit_federation_status:status().' 2>&1
```

> **【原理验证点 1】** `running` 表示 Federation 连接已建立。这个连接本质上是一个 AMQP 消费者，消费上游自动创建的内部队列 `federation: ... → fed.order`。

### 步骤 3：往上游发消息

```bash
docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 \
  -V upstream publish routing_key=order.new \
  exchange=fed.order \
  payload='{"orderId":1,"amount":5000}'
```

### 步骤 4：在下游消费，验证消息自动到达

```bash
# 在下游创建临时队列 + 绑定
docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 \
  -V downstream declare queue name=fed.verify durable=false

docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 \
  -V downstream declare binding source=fed.order \
  destination=fed.verify routing_key=order.new

# 消费
docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 \
  -V downstream get queue=fed.verify ackmode=ack_requeue_false
```

**预期**：取到 `{"orderId":1,"amount":5000}`。

> **【原理验证点 2】** 你往 `upstream/fed.order` 发的消息，自动出现在 `downstream/fed.order`。下游消费者只是从本地 Exchange 消费，完全不知道 Federation 的存在。

### 步骤 5：模拟"专线断开"→ 观察堆积 → 恢复

```bash
# 1. 删除下游 Exchange（模拟下游宕机）
docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 \
  -V downstream delete exchange name=fed.order

# 2. 连续往上游发 10 条消息
for i in $(seq 1 10); do
  docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 \
    -V upstream publish routing_key=order.new exchange=fed.order \
    payload="{\"orderId\":${i}}"
done

# 3. 查看上游内部队列堆积
docker exec rabbitmq-node1 rabbitmqctl list_queues -p upstream name messages 2>&1 \
  | grep federation
```

**预期**：`federation: ...` 队列的 messages 列 > 0，消息在堆积。

```bash
# 4. 重建下游 Exchange
docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 \
  -V downstream declare exchange name=fed.order type=topic durable=true

# 5. 等 5 秒后消费 → 断连期间的 10 条消息全部到了
sleep 5
docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 \
  -V downstream get queue=fed.verify ackmode=ack_requeue_false
```

> **【原理验证点 3】** "分区即断连，断连即堆积，恢复即 drain"。Federation 断开时消息在上游内部队列堆积，恢复后自动 drain。**消息零丢失，应用层零感知。**

---

## 结果对比

| 维度 | 原生集群跨机房 | Federation |
|---|---|---|
| 连接模型 | 全连通图（Mnesia 同步） | 单向 AMQP 链接 |
| 分区时 | 脑裂、`pause_minority`、少数派停服 | 连接断开，消息积压上游 |
| 元数据 | 共享（Exchange/Queue 变更需全集群确认） | 独立（各自管理各自） |
| 延迟 | 受 WAN 影响（镜像同步等 mirror） | 不受 WAN 影响（本地 Exchange 操作） |
| 恢复 | 需手动处理分区、可能丢数据 | 自动重连 + drain |

---

## 原理点睛

> "Federation 把紧耦合的集群，变成通过 AMQP 协议本身松耦合连接的独立集群。上游断开时，内部队列继续存在，消息堆积。"

你步骤 5 中看到 `federation:` 队列的消息数增长，就是内部队列在堆积。这证明了 Federation 不是"把两个集群合并成一个"，而是"用 AMQP 连接作为桥梁"。

> "Federation 是异步、单向的。分区即断连，断连即堆积，恢复即 drain，零丢失。"

你步骤 5 中重建 Exchange 后 10 条消息全部到位，就是对这句话的验证。

---

## 面试要诀

> "我实际搭建过 Federation：用两个 vhost 模拟跨机房，配置 `federation-upstream` + policy，验证了上游发消息 → 下游自动收到。还模拟了'专线断开'——删除下游 Exchange 后消息在上游内部队列堆积，重建后自动 drain，一条没丢。"

面试官听到"删除下游 Exchange→堆积→重建→drain→一条没丢"，知道你真的做过。
