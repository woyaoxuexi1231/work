package com.example.dubbo.demo.consumer.controller;

import com.example.dubbo.demo.api.model.User;
import com.example.dubbo.demo.api.service.UserService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义 Filter 演示。
 *
 * <p>Filter（过滤器）是 Dubbo 的 SPI 扩展点，可以在 RPC 调用的<b>前后</b>插入自定义逻辑。
 * 类似 Java Web 的 Servlet Filter 或 Spring 的 Interceptor。</p>
 *
 * <p><b>Filter 能做什么？</b></p>
 * <ul>
 *   <li>记录每次 RPC 调用的耗时、参数（已实现 → 看 Provider 控制台输出）</li>
 *   <li>IP 白名单校验 — 只允许特定 IP 的 Consumer 调用</li>
 *   <li>QPS 限流 — 统计每秒调用次数，超过阈值拒绝</li>
 *   <li>traceId 注入 — 分布式链路追踪</li>
 * </ul>
 *
 * <p><b>本示例的 Filter 已在 Provider 端注册，</b>
 * 每次 Consumer 调用 Provider 时，Filter 会自动执行。
 * 观察 Provider 控制台日志即可看到效果。</p>
 */
@RestController
public class FilterDemoController {

    @DubboReference(version = "1.0.0", group = "demo", check = false)
    private UserService userService;

    /**
     * 调用 UserService 触发自定义 Filter 执行。
     * <p>Provider 控制台会输出 Filter 的前置和后置日志。</p>
     */
    @GetMapping("/filter/call")
    public Map<String, Object> call() {
        long start = System.currentTimeMillis();

        User user1 = userService.getUserById(1L);
        User user2 = userService.getUserById(2L);

        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> m = new HashMap<>();
        m.put("结果", "调用了 2 次，请观察 Provider 控制台日志");
        m.put("user1", user1);
        m.put("user2", user2);
        m.put("总耗时", elapsed + "ms");
        return m;
    }

    /**
     * 说明文档。
     */
    @GetMapping("/filter")
    public Map<String, Object> help() {
        Map<String, Object> m = new HashMap<>();
        m.put("说明", "自定义 Filter 演示 — 观察 Provider 控制台日志");
        m.put("端点", "GET /filter/call  — 调 2 次 UserService，触发 Filter");
        m.put("Provider 日志效果", new String[]{
                "【Filter-前置】Consumer=192.168.x.x | 服务=UserService | 方法=getUserById | 参数=[1]",
                "【Filter-后置】方法=getUserById | 耗时=2ms | 结果类型=User",
                "两次调用各打印一次前置+后置，共 4 行日志"
        });
        m.put("源码位置", new String[]{
                "Provider:  CustomProviderFilter.java（Filter 实现）",
                "SPI 注册:  META-INF/dubbo/org.apache.dubbo.rpc.Filter"
        });
        m.put("可以扩展", "IP 白名单、QPS 限流、traceId 透传、异常统一处理");
        return m;
    }
}
