package com.example.springqa.Q18_InterceptorFilter.test;

import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试接口——Filter 和 Interceptor 只对这个路径下的请求生效。
 */
@RestController
@RequestMapping("/q18-test")
public class TestController {

    @GetMapping("/hello")
    public Map<String, Object> hello(@RequestParam(defaultValue = "World") String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", "Hello, " + name + "!");
        m.put("note", "这个请求经过了 Filter → DispatcherServlet → Interceptor.preHandle → Controller → Interceptor.postHandle → Interceptor.afterCompletion → Filter 后置");
        return m;
    }

    @GetMapping("/admin")
    public Map<String, Object> admin() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", "这是管理员接口");
        m.put("note", "Interceptor 里的 preHandle 会检查 X-Token header");
        return m;
    }
}
