package com.example.dubbo.consumer;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Dubbo 服务消费者启动类。
 *
 * =====================================================================
 * 【面试核心②：服务挂机兜底 — 提供者注册后瞬间宕机，消费者怎么办？】
 * =====================================================================
 *
 * Dubbo 的五层兜底机制：
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ 第 1 层 — Nacos 心跳健康检查                                    │
 * │                                                                   │
 * │   提供者每 5s 向 Nacos 发心跳                                    │
 * │   15s 无心跳 → 标记"不健康"                                      │
 * │   30s 无心跳 → 从列表剔除 + 推送变更通知给所有消费者              │
 * │                                                                   │
 * │   ⚠️ 最坏 30s 窗口内消费者可能拿到死地址 → 需要下面几层兜底     │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ 第 2 层 — 消费者本地缓存                                        │
 * │                                                                   │
 * │   Dubbo 把注册中心的服务列表缓存到本地文件：                     │
 * │   ~/.dubbo/dubbo-registry-<appName>.cache                        │
 * │                                                                   │
 * │   注册中心挂了 → 用本地缓存继续调                                │
 * │   服务重启 → 不用等推送，本地缓存的地址直接连                    │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ 第 3 层 — Dubbo 集群容错策略（Cluster）                         │
 * │                                                                   │
 * │   Failover   失败切换其他节点重试（默认）   读操作、幂等写       │
 * │   Failfast   立即抛异常，不重试             非幂等写              │
 * │   Failsafe   忽略失败返回 null              日志、监控            │
 * │   Failback   失败后后台定时重试             消息通知              │
 * │   Forking    并行调多台取第一个成功         实时性要求极高        │
 * │   Broadcast  广播所有提供者                 通知、配置刷新        │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ 第 4 层 — Mock 降级                                              │
 * │                                                                   │
 * │   RPC 失败（超时/无可用节点/网络不通）→ 自动走 Mock 返回兜底数据 │
 * │   配置: @DubboReference(mock = "true")                           │
 * │   Dubbo 自动找「接口名 + Mock」类：UserService → UserServiceMock │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ 第 5 层 — Sentinel / Hystrix 熔断限流                           │
 * │                                                                   │
 * │   连续失败 N 次 → 熔断，不再发起真实调用                         │
 * │   QPS 超阈值 → 限流，保护提供者                                  │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * 总结：服务注册后瞬间挂了 →
 *   ① Nacos 心跳 30s 内标记下线
 *   ② Failover 自动切换到其他节点
 *   ③ 所有节点都挂 → Mock 降级兜底
 *   ④ 消费者不会直接报错给用户 ✅
 */
@SpringBootApplication
@EnableDubbo
public class ConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
        System.out.println("\n============================================");
        System.out.println("  Dubbo Consumer 启动成功！端口 8080");
        System.out.println("  GET /user/1              → 基本 RPC 调用");
        System.out.println("  GET /user/all            → 全部用户");
        System.out.println("  GET /user/complex        → Hessian2 泛型验证");
        System.out.println("  GET /user/slow/1/500     → 正常（500ms < 2s 超时）");
        System.out.println("  GET /user/slow/1/3000    → 超时→重试→Mock 降级");
        System.out.println("============================================\n");
    }
}
