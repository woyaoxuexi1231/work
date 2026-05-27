# Docker Redis Cluster（集群）配置指南

## 一、环境说明

| 项目 | 值 |
|------|-----|
| Redis 版本 | 7.2.5 |
| 密码 | 123456 |
| 节点数 | 6（3 主 + 3 从） |
| 节点端口 | 7000 / 7001 / 7002 / 7003 / 7004 / 7005 |
| 总线端口 | 17000 / 17001 / 17002 / 17003 / 17004 / 17005 |
| 槽位分配 | 16384 slots，每个主节点 ~5461 slots |

---

## 二、快速启动

```bash
# 全新安装
sudo bash redis_cluster_start.sh

# 环境变量覆盖
sudo REDIS_VERSION=7.2.5 REDIS_PASSWORD=mypass bash redis_cluster_start.sh
```

---

## 三、验证集群状态

```bash
# 集群信息
redis-cli -h localhost -p 7000 -a 123456 cluster info

# 节点列表
redis-cli -h localhost -p 7000 -a 123456 cluster nodes

# 槽位分配
redis-cli -h localhost -p 7000 -a 123456 cluster slots
```

正常输出应包含 `cluster_state:ok` 和 `cluster_slots_assigned:16384`。

---

## 四、验证数据分片

### 4.1 写入数据（注意 -c 集群模式）

```bash
redis-cli -h localhost -p 7000 -a 123456 -c

# 在集群模式下写入，会自动重定向到正确节点
> set user:1 "Alice"
> set user:2 "Bob"
> set product:100 "Phone"
> set product:200 "Laptop"
> set order:001 "Active"
> set order:002 "Shipped"
```

### 4.2 在各节点查看数据分布

```bash
# 每个节点只存储部分数据
redis-cli -h localhost -p 7000 -a 123456 keys "*"
redis-cli -h localhost -p 7001 -a 123456 keys "*"
redis-cli -h localhost -p 7002 -a 123456 keys "*"
# keys 数量各不相同
```

### 4.3 查看 key 所在槽位

```bash
redis-cli -h localhost -p 7000 -a 123456 cluster keyslot user:1
# 输出槽位编号
```

---

## 五、故障转移测试

### 5.1 停止一个主节点

```bash
# 先找出主节点
redis-cli -h localhost -p 7000 -a 123456 cluster nodes | grep master

# 停止某个主节点
docker stop node-7000
```

### 5.2 观察自动切换

```bash
# 查看集群状态
redis-cli -h localhost -p 7001 -a 123456 cluster nodes
# 7000 的从节点会自动提升为主节点
```

### 5.3 恢复节点

```bash
docker start node-7000
# 恢复后会成为从节点
redis-cli -h localhost -p 7000 -a 123456 role
```

---

## 六、手动扩容/缩容

### 6.1 添加新节点（扩容）

```bash
# 首先启动新节点容器（例如 7006）
docker run -d --name node-7006 --network redis-cluster-net \
  --restart=always -p 7006:7006 -p 17006:17006 \
  -v /root/redis-cluster-docker/node-7006/data:/data \
  redis:7.2.5 redis-server --port 7006 --cluster-enabled yes \
  --requirepass 123456 --masterauth 123456 --protected-mode no

# 将新节点加入集群
docker exec node-7000 redis-cli -a 123456 --cluster add-node \
  $(docker inspect -f '{{.NetworkSettings.Networks.redis-cluster-net.IPAddress}}' node-7006):7006 \
  $(docker inspect -f '{{.NetworkSettings.Networks.redis-cluster-net.IPAddress}}' node-7000):7000

# 为新主节点分配槽位
docker exec node-7000 redis-cli -a 123456 --cluster reshard \
  $(docker inspect -f '{{.NetworkSettings.Networks.redis-cluster-net.IPAddress}}' node-7000):7000
# 交互式输入：要移动的槽位数 -> 接收节点 ID -> source: all
```

### 6.2 删除节点（缩容）

```bash
# 先迁移槽位到其他节点
docker exec node-7000 redis-cli -a 123456 --cluster reshard \
  $(docker inspect -f '{{.NetworkSettings.Networks.redis-cluster-net.IPAddress}}' node-7000):7000

# 删除节点
docker exec node-7000 redis-cli -a 123456 --cluster del-node \
  $(docker inspect -f '{{.NetworkSettings.Networks.redis-cluster-net.IPAddress}}' node-7000):7000 \
  <node-id>
```

---

## 七、客户端连接方式

### Spring Boot (Lettuce)

```yaml
spring:
  data:
    redis:
      cluster:
        nodes:
          - localhost:7000
          - localhost:7001
          - localhost:7002
          - localhost:7003
          - localhost:7004
          - localhost:7005
      password: 123456
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
```

### Jedis

```java
Set<HostAndPort> nodes = new HashSet<>();
nodes.add(new HostAndPort("localhost", 7000));
nodes.add(new HostAndPort("localhost", 7001));
nodes.add(new HostAndPort("localhost", 7002));

JedisCluster jedisCluster = new JedisCluster(nodes, 
    "default", "123456", 
    new GenericObjectPoolConfig<>());
```

---

## 八、常用管理命令

```bash
# 查看集群信息
redis-cli -h localhost -p 7000 -a 123456 cluster info

# 查看节点
redis-cli -h localhost -p 7000 -a 123456 cluster nodes

# 查看槽位分配
redis-cli -h localhost -p 7000 -a 123456 cluster slots

# 手动故障转移
redis-cli -h localhost -p 7003 -a 123456 cluster failover

# 重新分片
redis-cli -h localhost -p 7000 -a 123456 --cluster reshard localhost:7000

# 修复集群
redis-cli -h localhost -p 7000 -a 123456 --cluster fix localhost:7000

# 检查集群
redis-cli -h localhost -p 7000 -a 123456 --cluster check localhost:7000
```

---

## 九、故障排除

### 问题1：集群创建失败

```bash
# 检查节点配置文件是否有残留
docker exec node-7000 ls /data/
# 如果有旧的 nodes.conf，删除后重启
docker exec node-7000 rm -f /data/nodes.conf
docker restart node-7000
```

### 问题2：集群状态 fail

```bash
# 尝试修复
redis-cli -h localhost -p 7000 -a 123456 --cluster fix localhost:7000
```

### 问题3：节点无法连接

```bash
# 检查每个节点
for port in 7000 7001 7002 7003 7004 7005; do
  echo "=== ${port} ==="
  redis-cli -h localhost -p "${port}" -a 123456 ping 2>/dev/null || echo "FAIL"
done
```

### 完全重置

```bash
docker stop node-7000 node-7001 node-7002 node-7003 node-7004 node-7005
docker rm node-7000 node-7001 node-7002 node-7003 node-7004 node-7005
rm -rf /root/redis-cluster-docker
sudo bash redis_cluster_start.sh
```
