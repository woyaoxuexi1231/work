# Q1: Sentinel 故障转移——真实演练教程

> 用你已有的 `redis_sentinel_start.sh` 搭建 Sentinel 集群，然后手动关停 master，
> 盯 Sentinel 日志亲眼看到整个故障转移过程。

---

## 0. 环境准备

```bash
# 安装 Docker（如果没有）
# CentOS 7/8: yum install -y docker && systemctl start docker
# Ubuntu:    apt install -y docker.io && systemctl start docker

# 先拉起 Sentinel 集群
bash redis_sentinel_start.sh
```

验证一切正常：

```bash
# 确认主库身份
redis-cli -h localhost -p 6379 -a 123456 ROLE
# → "master"

# 确认从库跟随主库
redis-cli -h localhost -p 6380 -a 123456 ROLE
# → "slave", master_host: "redis-master", master_port: 6379

# 确认 Sentinel 看到了主库，查询名为 mymaster的主节点（Redis Master）的详细信息。
redis-cli -h localhost -p 26379 SENTINEL MASTER mymaster
# → 输出中应有 "flags: master", "num-slaves: 2", "num-other-sentinels: 2"
```

---

## 1. 先看懂 Sentinel 的关键配置

你脚本里的 sentinel.conf 有三行核心配置，先记住它们：

```bash
sentinel monitor mymaster 192.168.3.100 6379 2   # quorum=2
sentinel down-after-milliseconds mymaster 5000    # 5秒收不到PONG → SDOWN
sentinel failover-timeout mymaster 10000          # 故障转移总超时10秒
```

| 参数 | 值 | 含义 |
|------|-----|------|
| `quorum` | 2 | 至少 2 个 Sentinel 都说 master 挂了，才算 ODOWN |
| `down-after-milliseconds` | 5000 | 单个 Sentinel 连续 5 秒 PING 不通 → 标记 SDOWN |
| `failover-timeout` | 10000 | 故障转移超过 10 秒没完成 → 作废重来 |

---

## 2. 打开 4 个终端

| 终端 | 命令 | 用途 |
|------|------|------|
| **T1** | `docker logs -f sentinel-1` | 盯 Sentinel-1 日志 |
| **T2** | `docker logs -f sentinel-2` | 盯 Sentinel-2 日志 |
| **T3** | `docker logs -f sentinel-3` | 盯 Sentinel-3 日志 |
| **T4** | 执行操作 | 在这里敲命令 |

> 如果三个终端太多，至少开两个：一个盯 sentinel-1，一个操作。

---

## 3. 动手：KILL MASTER

**在 T4 执行：**

```bash
# 先看一眼当前拓扑——谁是主、谁是从
redis-cli -h localhost -p 26379 SENTINEL MASTER mymaster | grep -E "ip|port|flags|num-slaves"

# 
sudo docker ps -q | sudo xargs docker inspect -f '{{.Name}} -> {{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' | grep -E "redis|sentinel"


# 停掉主库！
docker stop redis-master
```

**回到 T1/T2/T3，盯日志——**

停止命令敲下去的那一刻，Sentinel 日志会立即变化。以下是你会看到的关键行，以及每一行的含义：

---

## 4. 日志解读：从 SDOWN 到新主上线

### 阶段 1：连接断开

```
# Connection with master lost.
* +fix-slave-config works? NO
```

Sentinel 发现自己连不上 master 了。

### 阶段 2：SDOWN（主观下线）—— 约 5 秒后

```
# +sdown master mymaster 192.168.3.100 6379
```

**含义**：这个 Sentinel 连续 5 秒（`down-after-milliseconds 5000`）没收到 PONG，自己判定 master 挂了。

**关键点**：这是"主观"的——只是这个 Sentinel 自己的判断，可能是网络抖动。此时还没有发生任何切换。

### 阶段 3：ODOWN（客观下线）

```
# +odown master mymaster 192.168.3.100 6379 #quorum 3/2
```

**含义**：这个 Sentinel 去问了其他两个 Sentinel——"你们也觉得 master 挂了？"——至少有 `quorum=2` 个确认。现在是 **客观** 下线了，故障转移正式开始。

**关键点**：`#quorum 3/2` 意思是 3 个 Sentinel 中有 2 个同意——满足 quorum。如果你的 quorum 设为 2 但只有 2 个 Sentinel，其中 1 个挂了就永远达不成 ODOWN。

### 阶段 4：纪元自增

```
# +new-epoch 1
```

**含义**：纪元（epoch）从 0 变成 1。每次故障转移 epoch 都会 +1，用来区分不同轮次的选举——这是 Raft 协议的核心概念。

### 阶段 5：投票

```
# +vote-for-leader abc123... 1
```

**含义**：这个 Sentinel 投票给某个候选者。每个 Sentinel 在一个 epoch 内只能投一次票。

### 阶段 6：Leader 选出

```
# +elected-leader master mymaster 192.168.3.100 6379
```

**含义**：Leader Sentinel 当选。得到半数以上票的 Sentinel 成为 Leader，由它来执行故障转移。

### 阶段 7：选择新主

```
# +failover-state-select-slave master mymaster 192.168.3.100 6379
```

**含义**：Leader 开始扫描所有 Slave，按优先级→复制偏移量排序，选出最优的一个。

### 阶段 8：发送 SLAVEOF NO ONE

```
# +failover-state-send-slaveof-noone slave 192.168.3.100:6380
```

**含义**：Leader 向选定的 Slave（6380）发送 `SLAVEOF NO ONE` 命令，把它提升为新的 Master。

### 阶段 9：切换完成

```
# +switch-master mymaster 192.168.3.100 6379 192.168.3.100 6380
```

**含义**：**切换完成！** 新主是 `192.168.3.100:6380`。Sentinel 会通过 Pub/Sub 通知所有订阅者（你的 Java 客户端如果是 Lettuce，此时会自动切到新主）。

### 阶段 10：重新配置其他 Slave

```
# +slave slave 192.168.3.100:6381 192.168.3.100 6381 @ mymaster 192.168.3.100 6380
```

**含义**：另一个 Slave（6381）被重新配置为追随新主（6380）。

---

## 5. 验证故障转移结果

```bash
# 新主是谁？
redis-cli -h localhost -p 26379 SENTINEL GET-MASTER-ADDR-BY-NAME mymaster
# → 192.168.3.100
# → 6380

# 新主确实是 master
redis-cli -h localhost -p 6380 -a 123456 ROLE
# → "master"

# 从库在追随新主
redis-cli -h localhost -p 6381 -a 123456 ROLE
# → "slave", master_port: 6380
```

---

## 6. 复活旧主——观察降级为 Slave

```bash
# 把旧主拉起来
docker start redis-master
```

**回到 T1/T2/T3 盯日志，你会看到：**

```
# +slave slave 192.168.3.100:6379 192.168.3.100 6379 @ mymaster 192.168.3.100 6380
```

**含义**：旧主（6379）重新上线了，但是——它以 **Slave 身份** 加入！Sentinel 自动让它 `SLAVEOF 192.168.3.100 6380`，从新主同步数据。

验证：

```bash
redis-cli -h localhost -p 6379 -a 123456 ROLE
# → "slave", master_port: 6380
```

旧主彻底变成了从库。**这就是 Sentinel 故障转移的完整闭环。**

---

## 7. 脑裂演示（选做）

脑裂发生的条件：旧主没有彻底挂，只是网络隔离了——Sentinel 看不到它，但它自己还在接收客户端写入。

**模拟方法：**

```bash
# 1. 把旧主从 Docker 网络中踢出去（模拟网络隔离）
docker network disconnect redis-sentinel-net redis-master

# 2. 等几秒——Sentinel 会检测到 master 不可达 → SDOWN → ODOWN → 选新主

# 3. 此时旧主还活着！它不知道外面已经选了新主
redis-cli -h localhost -p 6379 -a 123456 ROLE
# → "master"  ← 它还以为自己是主！

# 4. 如果此时客户端写入旧主 → 数据冲突
redis-cli -h localhost -p 6379 -a 123456 SET brain_test "来自旧主的数据"
# → OK ← 写入成功了！已经脑裂了！

# 5. 连回来——旧主被 Sentinel 重新配置为 Slave
docker network connect redis-sentinel-net redis-master

# 6. 过了一会儿
redis-cli -h localhost -p 6379 -a 123456 ROLE
# → "slave"  ← 降级了，但刚才写入的 "brain_test" 已经丢了
```

**如何防止脑裂？** 在旧主的 redis.conf 中加这两行：

```
min-slaves-to-write 1
min-slaves-max-lag 10
```

含义：如果旧主发现能联系到的 Slave 少于 1 个，或者 Slave 落后超过 10 秒，就**拒绝所有写入**。这样网络隔离时旧主自觉闭嘴，脑裂不发生。

---

## 8. 完整日志时间线速查表

按 Sentinel 日志中出现的顺序：

| 日志关键词 | 耗时（从 kill 开始） | 什么意思 |
|-----------|---------------------|---------|
| `Connection with master lost` | 0s | 连不上 master 了 |
| `+sdown` | ~5s | 主观下线（down-after-milliseconds） |
| `+odown` | ~5.5s | 客观下线（quorum 达成） |
| `+new-epoch` | ~5.5s | 纪元 +1，选举开始 |
| `+vote-for-leader` | ~5.5s | Sentinel 互相投票 |
| `+elected-leader` | ~5.6s | Leader 选出 |
| `+failover-state-select-slave` | ~5.6s | Leader 挑选新主 |
| `+failover-state-send-slaveof-noone` | ~5.7s | 向选中的 Slave 发 SLAVEOF NO ONE |
| `+switch-master` | ~5.8s | **切换完成**，新主诞生 |
| `+slave ...` | ~6s | 其他 Slave 重定向到新主 |

**总耗时约 6 秒**，其中 5 秒花在等待 `down-after-milliseconds` 上。

---

## 9. 面试时你可以这么说

> "我线上用 Docker 搭了一套 1 主 2 从 + 3 Sentinel 的环境。我手动 docker stop 了 master，开了三个终端 tail Sentinel 日志，亲眼看到：`+sdown`（主观下线，5 秒检测窗口）→ `+odown`（quorum=2 达成客观下线）→ `+new-epoch 1`（纪元递增，Raft 选举）→ `+elected-leader`（Leader 选出）→ `+switch-master`（切换完成，新主 6380）。整个过程约 6 秒。旧主 docker start 回来后，日志出现 `+slave`——它被 Sentinel 自动配置为 Slave，从新主同步数据。这就是故障转移的完整闭环。"

---

## 10. 扩展：调整参数看边界

修改 `redis_sentinel_start.sh` 中 sentinel.conf 的参数，重新跑一遍：

| 实验 | 改什么 | 预期现象 |
|------|--------|---------|
| 更快检测 | `down-after-milliseconds 1000` | SDOWN 1 秒就触发，但网络抖动可能误判 |
| 更慢检测 | `down-after-milliseconds 30000` | 要等 30 秒才 SDOWN，业务中断时间更长 |
| quorum 调高 | `sentinel monitor ... 3` | 需要 3 个 Sentinel 都确认，任何 1 个挂掉就转不了 |
| 只跑 2 个 Sentinel | 删掉 sentinel-3 | quorum=2 时，挂掉 1 个 Sentinel 就永远无法 ODOWN |

改完后重新运行 `bash redis_sentinel_start.sh` 即可重建环境。
