# Shovel 插件实战演练

> **核心问题**：如何将一个集群中特定 Queue 的消息，精确、可靠地搬运到另一个集群的 Exchange？如果搬运途中目标不可达，如何保证消息不丢？

---

## 实战类型：操作文档

**为什么选这种方式**：Shovel 是参数配置 + 消息搬运验证，必须亲手发消息、删目标、观察堆积、重建恢复——系统行为，不是代码能替代的。

---

## 操作背景（苦难时代）

你有一个日志收集系统，需要把 10 个机房的操作日志汇聚到中心机房。用 Federation？每个上游自动创建内部队列，几百个队列把磁盘打爆。用集群跨机房？日志量一大，WAN 带宽吃满，P99 从 3ms 飙到 120ms。

你需要的是一个**精确的搬运工**——只搬一个队列，不创建额外队列，目标 confirm 后才 ack 源。这就是 Shovel。

---

## 操作目标

配置 Shovel 将 `upstream/orders.source` 的消息精确搬运到 `downstream/ex.order → warehouse.inbound`。`ack-mode: on-confirm` 保证端到端不丢。

---

## 环境准备

| 资源 | 说明 |
|---|---|
| 集群 | `sudo bash scripts/01-cluster-setup.sh` |
| Shovel 脚本 | `sudo bash scripts/05-shovel-setup.sh` |
| 管理界面 | http://192.168.3.100:15672 |

---

## 详细操作步骤

### 步骤 1：运行 Shovel 脚本

```bash
sudo bash scripts/05-shovel-setup.sh
```

脚本做了 4 件事：启用插件 → 创建源队列 + 目标 Exchange → 创建目标队列并绑定 → 配置 Shovel 参数。

### 步骤 2：验证 Shovel 链路状态

打开 `http://192.168.3.100:15672` → **Admin** → **Shovel Status**。

应该看到 `orders-shovel` 状态为 `running`。

> **【原理验证点 1】** Shovel 是一个独立的 Erlang 进程。它在源端表现为一个消费者，在目标端表现为一个生产者。状态 `running` 表示两端连接都正常。

### 步骤 3：往源队列发消息 → 验证搬运

```bash
# 发消息到源队列
docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 \
  -V upstream publish routing_key=order.new \
  exchange=ex.order \
  payload='{"orderId":100,"amount":9999}'

# 从目标队列消费
docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 \
  -V downstream get queue=warehouse.inbound ackmode=ack_requeue_false
```

**预期**：取到 `{"orderId":100,"amount":9999}`。

> **【原理验证点 2】** Shovel 自动从 `orders.source` 消费，发布到 `ex.order`（routing_key=order.new），消息最终到达 `warehouse.inbound`。整个过程对源端和目标端完全透明。

### 步骤 4：模拟目标不可达 → 观察堆积

```bash
# 1. 删除目标队列（模拟下游宕机）
docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 \
  -V downstream delete queue name=warehouse.inbound

# 2. 连续往源队列发 10 条消息
for i in $(seq 1 10); do
  docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 \
    -V upstream publish routing_key=order.new exchange=ex.order \
    payload="{\"orderId\":${i}}"
done

# 3. 查看源队列消息堆积
docker exec rabbitmq-node1 rabbitmqctl list_queues -p upstream name messages 2>&1 \
  | grep orders
```

**预期**：`orders.source` 的 messages 数 > 0。但因为 `ack-mode: on-confirm`，Shovel 无法发布到目标 → 不 ack 源 → 消息在源队列中保持 `unacked`。

> **【原理验证点 3】** `on-confirm` 模式下，Shovel 必须等目标 Broker confirm 后才 ack 源。目标是"仓库"——仓库关门，货在码头堆着，不丢。

### 步骤 5：重建目标 → 观察恢复

```bash
# 重建目标队列 + 绑定
docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 \
  -V downstream declare queue name=warehouse.inbound durable=true

docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 \
  -V downstream declare binding source=ex.order \
  destination=warehouse.inbound routing_key=order.new

# 等 5 秒，Shovel 自动重连并 drain
sleep 5

# 消费验证 → 10 条全部到位
for i in $(seq 1 10); do
  docker exec rabbitmq-node1 rabbitmqadmin -u admin -p admin123 \
    -V downstream get queue=warehouse.inbound ackmode=ack_requeue_false
done
```

**预期**：10 条消息全部取到。

> **【原理验证点 4】** `reconnect-delay: 5` — 每 5 秒重试。目标恢复后 Shovel 自动重连，积压消息全部 drain。端到端零丢失。

---

## 结果对比

| 维度 | Federation | Shovel |
|---|---|---|
| 粒度 | Exchange/Queue 级别 | 单 Queue → Exchange |
| 上游影响 | 自动创建内部队列（磁盘开销） | 无额外队列，直接消费源 Queue |
| 确认控制 | 固定（类似 on-confirm） | 灵活：on-confirm / on-publish / no-ack |
| 消息转换 | 不支持 | 支持 routing key 重写 |
| 适合场景 | 按主题批量同步 | 精准单队列搬运 |

---

## 原理点睛

> "Shovel 的 ack-mode: on-confirm 实现了端到端可靠传递。消息只有目标 confirm 后才 ack 源。如果目标不可达，消息永远留在源队列 unacked。"

你步骤 4 中删除目标后源队列堆积不放，步骤 5 重建后全部到位——这就是 `on-confirm` 的"确认链"。

> "Shovel 是精确搬运工。每一条跨机房链路都是一个独立 Shovel 进程，可独立配置 ack-mode 和重连策略。"

你现在只有一条链路 `orders-shovel`。如果要加第二条（比如死信队列搬运），再配一个 `set_parameter shovel ...` 即可，互不影响。

---

## 面试要诀

> "我实际配置过 Shovel：`ack-mode: on-confirm`，`reconnect-delay: 5`。删除目标队列后发 10 条消息，源队列堆积不丢；重建目标后 Shovel 自动重连，10 条全部 drain。我还对比过 Federation——Federation 适合按 Topic 批量同步，Shovel 适合精准单队列搬运。"

面试官听到你对比了"批量同步 vs 精准搬运"，知道你不是跟风用插件，而是有选型判断力。
