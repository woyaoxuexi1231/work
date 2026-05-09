# Day 02: AMQP 0-9-1 协议内核与 Connection 优化 🔌

第一天我们搞定了集群。今天我们要深入底层，看看代码是如何与 RabbitMQ 通信的。我们会跳过 Spring 的高度封装，直接使用官方 `amqp-client` 来理解连接的本质。

---

## 💡 核心理论补给站

### 1. AMQP 0-9-1 帧结构 (Frame Structure)
所有的通信都是通过“帧”进行的。每一条消息或指令都会被拆解为以下几种帧：
- **Method Frame**：携带命令（如 `Basic.Publish`, `Queue.Declare`）。
- **Content Header Frame**：携带消息属性（如 `delivery_mode`, `priority`, `headers`）。
- **Body Frame**：实际的消息体负载。
- **Heartbeat Frame**：心跳帧，用于保活。

### 2. Connection vs Channel (多路复用)
- **Connection**：物理连接（TCP）。建立连接非常耗时（涉及三次握手、SSL 握手、身份认证）。
- **Channel**：逻辑连接（多路复用）。在一个 Connection 上可以开几千个 Channel。
- **性能损耗边界**：Connection 太多会耗尽文件句柄；Channel 太多会增加 CPU 上下文切换压力。**严禁在多线程间共享同一个 Channel 实例**。

### 3. 心跳 (Heartbeat) 与 自动重连
- **Heartbeat**：如果客户端在指定时间内没有发送数据，RabbitMQ 会认为客户端已断线。默认通常是 60s，高频变动场景建议调小。
- **自动重连**：官方 Java 客户端支持 `AutomaticRecovery`，但需要正确配置。

---

## 🛠 地狱实战指南

### 第一步：封装 RabbitConnectionFactory
我们需要一个工具类，不仅能创建连接，还要能自定义：
1. **线程池**：用于处理消费回调，避免阻塞。
2. **心跳时间**：防止网络抖动导致的误判。
3. **自动恢复**：开启连接恢复和拓扑恢复（Queue/Exchange 自动声明）。

### 第二步：编写 SimpleApp.java
实现一个具备生产级韧性的消息发送者：
- 支持自动重连。
- 捕获 `ShutdownSignalException`。
- 实现 `Runtime.getRuntime().addShutdownHook` 进行优雅关闭。

---

## 📖 实验手册

### 1. 准备工作
我已经为你生成了 [pom.xml](file:///d:/project/demo/demo-java/example/rabbit/study/day02/pom.xml)，其中包含了 `amqp-client`。
如果你在 IDE 中，直接打开 `day02` 目录作为一个 Maven 项目即可。

### 2. 核心代码讲解
- **[RabbitConnectionFactory.java](file:///d:/project/demo/demo-java/example/rabbit/study/day02/RabbitConnectionFactory.java)**：展示了如何通过 `setAutomaticRecoveryEnabled(true)` 开启客户端侧的自动重连。
- **[SimpleApp.java](file:///d:/project/demo/demo-java/example/rabbit/study/day02/SimpleApp.java)**：演示了 `ShutdownHook` 的用法，确保 JVM 退出时能优雅释放连接。

---

## 🏆 验收标准
- [ ] [RabbitConnectionFactory.java](file:///d:/project/demo/demo-java/example/rabbit/study/day02/RabbitConnectionFactory.java) 封装了线程池和心跳配置。
- [ ] [SimpleApp.java](file:///d:/project/demo/demo-java/example/rabbit/study/day02/SimpleApp.java) 实现了优雅关闭逻辑。
- [ ] 手动断开网络/停止容器后，程序能打印出“重连中...”并最终恢复。

---

## 🔍 实战教程：WireShark/Tcpdump 抓包分析 AMQP 协议

### 一、环境准备

#### Windows 使用 WireShark
1. **下载 WireShark**：https://www.wireshark.org/download.html
2. **安装 WinPcap/Npcap**（安装时勾选 "Install Npcap in WinPcap API-compatible Mode"）
3. **以管理员身份运行** WireShark

#### Linux 使用 Tcpdump
```bash
# 安装 tcpdump
sudo apt-get install tcpdump  # Ubuntu/Debian
sudo yum install tcpdump      # CentOS/RHEL

# 安装 tshark（可选，用于分析）
sudo apt-get install wireshark-common
```

### 二、开始抓包

#### 方法 1：WireShark 图形界面（推荐新手）

**步骤 1：选择网卡**
- 打开 WireShark，选择你要监听的网卡
- 本地开发通常选择：`Loopback: lo` (Linux) 或 `Adapter for loopback` (Windows)
- 如果 RabbitMQ 在远程服务器，选择对应的物理网卡

**步骤 2：设置过滤规则**
在顶部过滤栏输入：
```
amqp || tcp.port == 5672
```

**步骤 3：开始抓包**
- 点击蓝色鲨鱼鳍图标开始抓包
- 运行 SimpleApp.java 发送消息
- 抓包 10-20 秒后点击红色方块停止

#### 方法 2：Tcpdump 命令行（适合服务器）

```bash
# 抓取 5672 端口的数据，保存到文件
sudo tcpdump -i any -w rabbitmq_capture.pcap port 5672

# 抓取指定数量的包（例如 1000 个）
sudo tcpdump -i any -c 1000 -w rabbitmq_capture.pcap port 5672

# 抓取后立即用 tshark 分析
sudo tcpdump -i any -w - port 5672 | tshark -r - -Y "amqp"
```

### 三、分析 Connection Open 过程

#### 完整 TCP 三次握手
```
No.  Time        Source          Destination     Protocol  Info
1    0.000000    192.168.1.100   192.168.1.200   TCP       54321 → 5672 [SYN]
2    0.000500    192.168.1.200   192.168.1.100   TCP       5672 → 54321 [SYN, ACK]
3    0.000800    192.168.1.100   192.168.1.200   TCP       54321 → 5672 [ACK]
```

#### AMQP 协议握手流程
```
4    0.001000    Client → Server   AMQP    Protocol Header: 'AMQP\x00\x00\x09\x01'
     - 客户端发送协议版本声明

5    0.001200    Server → Client   AMQP    Connection.Start
     - 服务器要求客户端认证
     - 包含机制：PLAIN, AMQPLAIN
     - 包含 server properties

6    0.001500    Client → Server   AMQP    Connection.Start-Ok
     - 客户端发送认证信息
     - 用户名：guest
     - 密码：guest (Base64 编码)

7    0.001800    Server → Client   AMQP    Connection.Tune
     - 服务器协商参数
     - channel-max: 2047
     - frame-max: 131072
     - heartbeat: 60

8    0.002000    Client → Server   AMQP    Connection.Tune-Ok
     - 客户端确认参数
     - 可能调整 heartbeat 值

9    0.002200    Client → Server   AMQP    Connection.Open
     - 客户端请求打开连接
     - virtual-host: /

10   0.002400    Server → Client   AMQP    Connection.Open-Ok
     - ✅ 连接建立成功！
```

#### WireShark 中查看方法
1. 右键任意 AMQP 包 → `Follow` → `TCP Stream`
2. 可以看到完整的十六进制和 ASCII 数据
3. 在包详情面板展开：`AMQP` → `Connection` → `Start/Tune/Open`

### 四、分析 Basic.Publish 过程

#### 发布消息的帧结构
```
11   0.005000    Client → Server   AMQP    Channel.Open
     - 打开逻辑 Channel (Channel ID: 1)

12   0.005200    Server → Client   AMQP    Channel.Open-Ok
     - ✅ Channel 打开成功

13   0.010000    Client → Server   AMQP    Basic.Publish
     - Method Frame:
       - exchange: "" (默认交换机)
       - routing-key: "day02_simple_queue"
       - mandatory: false
       - immediate: false

14   0.010100    Client → Server   AMQP    Content Header
     - Content Header Frame:
       - body size: 42 bytes
       - delivery-mode: 2 (persistent)
       - content-type: "application/octet-stream"

15   0.010200    Client → Server   AMQP    Body Frame
     - Body Frame:
       - "Day 02 消息序列: 0"
       - 实际消息内容
```

#### 重要字段解析

**Basic.Publish Method Frame：**
```
Frame Type: 1 (Method)
Channel: 1
Class: 60 (Basic)
Method: 40 (Publish)

Arguments:
  - exchange: "" (空字符串表示默认交换机)
  - routing-key: "day02_simple_queue"
  - mandatory: 0 (false)
  - immediate: 0 (false)
```

**Content Header Frame：**
```
Frame Type: 2 (Header)
Channel: 1
Class: 60 (Basic)
Weight: 0
Body Size: 42

Properties:
  - flags: 0x8000 (delivery-mode present)
  - delivery-mode: 2 (persistent - 持久化)
  - priority: 0
  - delivery-mode 2 = 消息会写入磁盘
```

**Body Frame：**
```
Frame Type: 3 (Body)
Channel: 1
Payload: "Day 02 消息序列: 0"
```

### 五、实战演练步骤

#### 实验 1：抓取完整的 Connection 建立过程
```bash
# 1. 先启动抓包
sudo tcpdump -i any -w connection_open.pcap port 5672

# 2. 打开新终端，运行 SimpleApp
java study.day02.SimpleApp

# 3. 等待 3 秒后 Ctrl+C 停止 tcpdump

# 4. 用 WireShark 打开分析
wireshark connection_open.pcap
```

#### 实验 2：抓取 Basic.Publish 过程
```bash
# 修改 SimpleApp.java，发送 5 条消息后退出
# 然后抓包
sudo tcpdump -i any -w publish_capture.pcap port 5672

# 运行程序
java study.day02.SimpleApp

# 分析
wireshark publish_capture.pcap
```

#### 实验 3：对比不同 delivery-mode
```bash
# 创建两个测试程序：
# Test1: delivery-mode = 1 (non-persistent)
# Test2: delivery-mode = 2 (persistent)

# 分别抓包，对比 Content Header Frame 的差异
sudo tcpdump -i any -w mode_comparison.pcap port 5672
```

### 六、WireShark 高级技巧

#### 1. 添加自定义列
- `Edit` → `Preferences` → `Columns`
- 添加列：`AMQP Method`，字段：`amqp.method`
- 添加列：`Channel ID`，字段：`amqp.channel`

#### 2. 颜色过滤规则
```bash
# 只显示 Connection 相关
amqp.class == 10

# 只显示 Basic.Publish
amqp.method == 40 && amqp.class == 60

# 只显示心跳包
amqp.type == 8
```

#### 3. 统计功能
- `Statistics` → `Conversations`：查看 TCP 连接统计
- `Statistics` → `IO Graphs`：流量图形化
- `Analyze` → `Expert Information`：查看警告和错误

### 七、关键抓包特征总结

| 阶段 | Frame Type | Class | Method | 特征 |
|------|-----------|-------|--------|------|
| 协议握手 | - | - | - | `AMQP\x00\x00\x09\x01` |
| Connection.Start | Method | 10 | 10 | 服务端发起认证 |
| Connection.Start-Ok | Method | 10 | 11 | 客户端认证响应 |
| Connection.Tune | Method | 10 | 30 | 参数协商 |
| Connection.Open | Method | 10 | 40 | 打开连接 |
| Channel.Open | Method | 20 | 10 | 打开逻辑通道 |
| Basic.Publish | Method | 60 | 40 | 发布消息 |
| Content Header | Header | 60 | - | 消息头属性 |
| Body Frame | Body | - | - | 消息体内容 |
| Heartbeat | Heartbeat | - | - | Type=8，空内容 |

### 八、常见问题排查

#### 问题 1：看不到 AMQP 协议解析
**原因**：WireShark 没有识别 AMQP 协议
**解决**：
1. 右键包 → `Decode As` → 选择 `AMQP`
2. 或设置：`Edit` → `Preferences` → `Protocols` → `AMQP` → 添加端口 `5672`

#### 问题 2：抓不到本地流量
**原因**：本地回环流量需要特殊设置
**解决**：
- Windows：选择 `Adapter for loopback traffic capture`
- Linux：`tcpdump -i lo`

#### 问题 3：数据包太多太乱
**解决**：使用过滤规则
```
# 只显示特定 Channel
amqp.channel == 1

# 只显示某个方向的流量
ip.src == 192.168.1.100

# 排除心跳包
amqp.type != 8
```

---

## 🎓 扩展阅读
- AMQP 0-9-1 官方规范：https://www.rabbitmq.com/protocol.html
- WireShark AMQP 解析器文档：https://wiki.wireshark.org/AMQP
- RabbitMQ 协议详解：https://www.rabbitmq.com/amqp-0-9-1-reference.html

**底层稳，架构才稳。开始今天的代码修行吧！**
