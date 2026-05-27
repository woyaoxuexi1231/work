# Docker Redis Sentinel（哨兵）配置指南

## 一、环境说明

| 项目 | 值 |
|------|-----|
| Redis 版本 | 7.2.5 |
| 密码 | 123456 |
| Redis 主库 | 宿主机端口 6379 |
| Redis 从库 1 | 宿主机端口 6380 |
| Redis 从库 2 | 宿主机端口 6381 |
| Sentinel 1 | 宿主机端口 26379 |
| Sentinel 2 | 宿主机端口 26380 |
| Sentinel 3 | 宿主机端口 26381 |
| Sentinel Quorum | 2（至少 2 个 Sentinel 同意才切换） |

---

## 二、快速启动

```bash
# 全新安装
sudo bash redis_sentinel_start.sh

# 环境变量可覆盖默认值
sudo REDIS_VERSION=7.2.5 REDIS_PASSWORD=mypass DATA_ROOT=/data/redis-sentinel bash redis_sentinel_start.sh
```

---

## 三、验证 Sentinel 状态

```bash
# 查看 Sentinel 监控的主库信息
redis-cli -h localhost -p 26379 sentinel master mymaster

# 查看从库列表
redis-cli -h localhost -p 26379 sentinel slaves mymaster

# 查看所有 Sentinel 实例
redis-cli -h localhost -p 26379 sentinel sentinels mymaster

# 查看主从复制状态
redis-cli -h localhost -p 6379 -a 123456 info replication
redis-cli -h localhost -p 6380 -a 123456 info replication
```

---

## 四、验证主从同步

### 4.1 在主库写入数据

```bash
redis-cli -h localhost -p 6379 -a 123456 set test_key "hello sentinel"
redis-cli -h localhost -p 6379 -a 123456 set counter 100
```

### 4.2 在从库读取数据

```bash
redis-cli -h localhost -p 6380 -a 123456 get test_key
# 输出: "hello sentinel"

redis-cli -h localhost -p 6381 -a 123456 get counter
# 输出: "100"
```

---

## 五、故障转移测试

### 5.1 停止主库模拟故障

```bash
docker stop redis-master
```

### 5.2 观察 Sentinel 自动切换

```bash
# 查看 Sentinel 日志
docker logs sentinel-1 -f

# 等待 5-10 秒后，查看新主库
redis-cli -h localhost -p 26379 sentinel master mymaster | grep -E "ip|port"
```

### 5.3 恢复旧主库

```bash
docker start redis-master
# 旧主库恢复后会变成新主库的从库
redis-cli -h localhost -p 6379 -a 123456 info replication | grep role
# 输出: role:slave
```

---

## 六、Sentinel 常用命令

```bash
# 查看主库 IP/端口
redis-cli -h localhost -p 26379 sentinel get-master-addr-by-name mymaster

# 手动触发故障转移
redis-cli -h localhost -p 26379 sentinel failover mymaster

# 重置 Sentinel 状态
redis-cli -h localhost -p 26379 sentinel reset mymaster

# 监控 Sentinel 事件
redis-cli -h localhost -p 26379 subscribe +switch-master
```

---

## 七、客户端连接方式

### Spring Boot (Lettuce)

```yaml
spring:
  data:
    redis:
      sentinel:
        master: mymaster
        nodes:
          - localhost:26379
          - localhost:26380
          - localhost:26381
      password: 123456
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
```

### Jedis

```java
Set<String> sentinels = new HashSet<>();
sentinels.add("localhost:26379");
sentinels.add("localhost:26380");
sentinels.add("localhost:26381");

JedisSentinelPool pool = new JedisSentinelPool("mymaster", sentinels,
    "default", "123456");
```

---

## 八、故障排除

### 问题1：Sentinel 无法检测到主库

```bash
# 检查网络连通性
docker exec sentinel-1 ping redis-master
```

### 问题2：主从同步失败

```bash
# 在从库检查
redis-cli -h localhost -p 6380 -a 123456 info replication | grep master_link_status
# 应该是 up

# 如果不是，手动重连
docker exec redis-slave-1 redis-cli -a 123456 REPLICAOF redis-master 6379
```

### 问题3：Sentinel 配置不一致

```bash
# 停止所有 sentinel
docker stop sentinel-1 sentinel-2 sentinel-3

# 删除 sentinel 数据
rm -rf /root/redis-sentinel-docker/sentinel-1/*
rm -rf /root/redis-sentinel-docker/sentinel-2/*
rm -rf /root/redis-sentinel-docker/sentinel-3/*

# 重新运行脚本
sudo bash redis_sentinel_start.sh
```

---

## 九、常用管理命令

```bash
# 查看所有容器
docker ps | grep redis

# 查看指定容器日志
docker logs redis-master
docker logs sentinel-1

# 进入容器
docker exec -it redis-master bash

# 停止所有服务
docker stop redis-master redis-slave-1 redis-slave-2 sentinel-1 sentinel-2 sentinel-3

# 删除所有（含数据）
docker stop redis-master redis-slave-1 redis-slave-2 sentinel-1 sentinel-2 sentinel-3
docker rm redis-master redis-slave-1 redis-slave-2 sentinel-1 sentinel-2 sentinel-3
docker network rm redis-sentinel-net
```
