package com.example.dubbo.demo.consumer.controller;

import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.service.GenericService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 泛化调用（Generic Service）演示。
 *
 * <p><b>什么是泛化调用？</b></p>
 * <ul>
 *   <li>Consumer <b>不需要引用服务接口的 jar 包</b>，直接通过服务名 + 方法名 + 参数类型 + 参数值 来调用</li>
 *   <li>返回值以 {@link Map} 形式返回（Dubbo 自动将 POJO 转成 Map）</li>
 *   <li>Consumer 端不需要编译 {@code UserService} 接口，只要知道接口全限定名和方法签名就能调</li>
 * </ul>
 *
 * <p><b>适用场景：</b></p>
 * <table border="1">
 *   <tr><th>场景</th><th>为什么用泛化</th></tr>
 *   <tr><td>API 网关</td><td>网关不可能为每个后端服务都引入接口 jar，泛化调用通过配置动态路由</td></tr>
 *   <tr><td>测试平台</td><td>在线测试任意服务的方法，不需要重启应用</td></tr>
 *   <tr><td>脚本/工具类</td><td>Python 或命令行工具通过泛化调用 Java 服务</td></tr>
 *   <tr><td>服务治理</td><td>监控平台定时调用各服务的 healthCheck 方法</td></tr>
 * </ul>
 *
 * <p><b>与普通调用的对比：</b></p>
 * <pre>
 * 普通调用：
 *   UserService userService          ← 需要引入 API jar，编译时就有接口
 *   User user = userService.getUserById(1L)
 *
 * 泛化调用：
 *   GenericService userService        ← 不需要 API jar，运行时动态解析
 *   Object result = userService.$invoke("getUserById", new String[]{"long"}, new Object[]{1L})
 *   Map user = (Map) result           ← 返回值是 Map，不是 User 对象（Consumer 端没有 User 类）
 * </pre>
 */
@RestController
public class GenericDemoController {

    /**
     * 泛化调用引用。
     *
     * <p>{@code generic = true} 告诉 Dubbo 这是一个泛化引用，注入 {@link GenericService} 类型。<br>
     * {@code interfaceName} 指定目标服务的全限定接口名，Dubbo 用这个名字去注册中心查找。<br>
     * <b>不能</b>用 {@code interfaceClass}，因为泛化的目的就是 Consumer 不持有接口 Class。</p>
     */
    @DubboReference(
            version = "1.0.0",
            group = "demo",
            check = false,
            generic = true,
            interfaceName = "com.example.dubbo.demo.api.service.UserService"
    )
    private GenericService genericService;

    // ════════════════════════════════════════════════════════════
    // ① 基础泛化调用 — 单个参数
    // ════════════════════════════════════════════════════════════
    @GetMapping("/generic/{id}")
    public Object getUserById(@PathVariable Long id) {

        // $invoke(方法名, 参数类型数组, 参数值数组)
        Object result = genericService.$invoke(
                "getUserById",          // 方法名
                new String[]{"long"},   // 参数类型（全限定名：java.lang.Long 或简称 long）
                new Object[]{id}        // 参数值
        );

        // 返回值是 Map（Dubbo 自动将 User POJO 转成了 Map）
        return result;
    }

    // ════════════════════════════════════════════════════════════
    // ② 对比：普通调用 vs 泛化调用返回值的区别
    // ════════════════════════════════════════════════════════════
    @GetMapping("/generic/compare/{id}")
    public Map<String, Object> compare(@PathVariable Long id) {

        // 泛化调用 — 返回值是 Map
        Map<String, Object> genericResult = (Map<String, Object>) genericService.$invoke(
                "getUserById",
                new String[]{"long"},
                new Object[]{id}
        );

        Map<String, Object> resp = new HashMap<>();
        resp.put("genericResult", genericResult);
        resp.put("genericResult类型", genericResult.getClass().getName());
        resp.put("说明", "泛化调用的返回值是 HashMap，不是 User 对象。"
                + "Consumer 端不需要 UserService 接口 jar 也能调用");

        // 从 Map 中取字段
        if (genericResult != null) {
            resp.put("用户名", genericResult.get("username"));
            resp.put("邮箱", genericResult.get("email"));
        }

        return resp;
    }

    // ════════════════════════════════════════════════════════════
    // ③ 泛化调用快速了解
    // ════════════════════════════════════════════════════════════
    @GetMapping("/generic/help")
    public Map<String, Object> help() {
        Map<String, Object> m = new HashMap<>();
        m.put("说明", "泛化调用不需要引入 API jar 包，运行时根据接口名+方法名+参数动态调用");
        m.put("适用场景", "API 网关、测试平台、脚本工具、服务治理监控");
        m.put("返回类型", "POJO 返回 Map，基本类型返回原值，List 返回 List of Map");
        m.put("示例接口", "GET /generic/1          — 调用 UserService.getUserById(1)");
        m.put("示例接口", "GET /generic/compare/1  — 泛化 vs 普通返回值对比");
        return m;
    }
}
