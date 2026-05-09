# 🚀 Java 后端 30 天大厂面试突击——全栈通关总纲 (Master Roadmap)

> **总教官寄语**：30 天时间非常紧迫，你无法像之前设计的 17 个模块那样每个都花 30 天（那需要一年半）。
> 为了让你在 30 天内通关，我将这 17 个模块进行了**深度压缩和优先级重排**。
> 本计划采用 **“二八原则”**：用 20% 的时间掌握 80% 的高频核心考点。

---

## 📅 30 天突击时间分配表

| 阶段 | 建议天数 | 对应模块 (已按优先级排序) | 核心任务 |
| :--- | :--- | :--- | :--- |
| **第一阶段：Java 筑基** | Day 1 - 3 | [01-JavaBasic](../01-JavaBasic/Java_Interview_Mastery_Plan.md) | 基础语法、集合、JVM 原理、反射机制 |
| **第二阶段：基础设施** | Day 4 | [02-Network](../02-Network/Network_Interview_30Day_Mastery.md) | TCP/IP、HTTP 1.1/2.0/3.0、HTTPS 安全 |
| **第三阶段：持久化层** | Day 5 - 7 | [03-MySQL](../03-MySQL/MySQL_Interview_30Day_Mastery.md) + [04-MyBatis](../04-MyBatis/MyBatis_Interview_30Day_Mastery.md) | 索引优化、MVCC、事务、MyBatis 插件/缓存 |
| **第四阶段：并发与缓存** | Day 8 - 11 | [05-JavaConcurrent](../05-JavaConcurrent/Distributed_Concurrent_Performance_Mastery.md) + [06-Redis](../06-Redis/Redis_Interview_30Day_Mastery.md) | 线程池、锁机制、Redis 高并发/分布式锁 |
| **第五阶段：核心框架** | Day 12 - 14 | [07-Spring](../07-Spring/Spring_Interview_30Day_Mastery.md) + [08-SpringBoot](../08-SpringBoot/SpringBoot_Interview_30Day_Mastery.md) | IoC/AOP 源码、循环依赖、自动装配原理 |
| **第六阶段：安全与接入** | Day 15 | [09-Nginx](../09-Nginx/Nginx_Interview_30Day_Mastery.md) + [10-SpringSecurity](../10-SpringSecurity/SpringSecurity_Interview_30Day_Mastery.md) | 反向代理、负载均衡、认证授权链路 |
| **第七阶段：微服务架构** | Day 16 - 19 | [11-SpringCloud](../11-SpringCloud/SpringCloud_Interview_30Day_Mastery.md) + [12-SpringGateway](../12-SpringGateway/SpringGateway_Interview_30Day_Mastery.md) + [13-SpringCloudAlibaba](../13-SpringCloudAlibaba/SpringCloudAlibaba_Interview_30Day_Mastery.md) | 服务治理、Sentinel 限流、分布式事务 (Seata) |
| **第八阶段：消息中间件** | Day 20 - 22 | [14-RabbitMQ](../14-RabbitMQ/RabbitMQ_Learning_Plan.md) + [15-RocketMQ](../15-RocketMQ/RocketMQ_Interview_30Day_Mastery.md) + [16-Kafka](../16-Kafka/Kafka_Interview_30Day_Mastery.md) | 消息可靠性、顺序消息、高吞吐原理 |
| **第九阶段：云原生运维** | Day 23 - 25 | [17-CloudNative](../17-CloudNative/CloudNative_Interview_30Day_Mastery.md) | Docker 隔离、K8s 组件、Pod 调度 |
| **第十阶段：算法与设计** | Day 26 - 28 | (待补充) | LeetCode 高频题、系统设计题 (秒杀、短链) |
| **第十一阶段：冲刺复盘** | Day 29 - 30 | (全量回顾) | 简历优化、模拟面试、错题重温 |

---

## 🛠️ 学习方法论：如何每天学完原本 30 天的内容？

1. **精选模式**：不要从 Day 1 看到 Day 30。每天只看对应指南中 **“Week 1” 的核心基础** 和 **“Week 5” 的地狱级难题**。
2. **源码驱动**：面试官不关心你会不会用，关心你懂不懂原理。优先看每个指南里的“源码”面试题。
3. **编程题必做**：每天必须亲手写完指南里的那 1 道编程题，保持手感。
4. **错题本**：记录你第一遍回答不上来的问题，在第 29-30 天重点复习。

---

## 🚩 排序逻辑说明

*   **从易到难**：先搞定 Java 语言本身（01），再搞定数据存储（03/04），最后搞定复杂的分布式系统（11/13）。
*   **依赖关系**：
    *   学 SpringCloud (11) 之前必须先懂 Spring (07)。
    *   学云原生 (17) 之前必须先懂网络 (02) 和 Nginx (09)。
    *   学分布式事务之前必须先懂 MySQL 事务原理 (03)。

**现在，请按照左侧文件夹的编号 `01` 到 `17` 的顺序开始你的征程吧！**
