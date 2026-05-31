package com.example.springqa.Q16_MvcRequestFlow.era.era4_rest;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * <h1>第四代：Spring Boot + @RestController（2014-至今）</h1>
 *
 * <pre>
 * ┌────────────────────────────────────────────────────┐
 * │  涉及的 MVC 组件：                                   │
 * │                                                    │
 * │  HandlerMapping    ✅ RequestMappingHandlerMapping   │
 * │                    → URL "/era4/users/1" → HandlerMethod │
 * │                                                    │
 * │  HandlerAdapter    ✅ RequestMappingHandlerAdapter   │
 * │                    → 和 Era3 同一个 Adapter！        │
 * │                                                    │
 * │  参数解析           ✅ PathVariableMethodArgumentResolver │
 * │                      RequestResponseBodyMethodProcessor │
 * │                    → @RequestBody：读 body → HttpMessageConverter → 反序列化 │
 * │                                                    │
 * │  返回值处理         ✅ RequestResponseBodyMethodProcessor │
 * │                    → 返回 User → HttpMessageConverter → JSON │
 * │                                                    │
 * │  ViewResolver      ❌ 不参与！                        │
 * │                    → @ResponseBody 直接写 response body │
 * │                                                    │
 * │  HttpMessageConverter ✅ MappingJackson2HttpMessageConverter │
 * │                    → User → {"id":1,"name":"Alice"} │
 * └────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>访问: http://localhost:8080/era4/users/1</p>
 */
@RestController
@RequestMapping("/era4")
public class Era4RestController {

    private final Map<Long, User> store = new LinkedHashMap<>();
    { store.put(1L, new User(1L, "Alice")); store.put(2L, new User(2L, "Charlie")); }

    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        System.out.println("  [Era4] @RestController — ViewResolver 不参与，直接 JSON");
        System.out.println("    参数解析: PathVariableMethodArgumentResolver → id=" + id);
        System.out.println("    返回值处理: RequestResponseBodyMethodProcessor → MappingJackson2HttpMessageConverter → JSON");
        return store.getOrDefault(id, new User(id, "Unknown"));
    }

    @PostMapping("/users")
    public User createUser(@RequestBody User user) {
        System.out.println("  [Era4] POST — @RequestBody JSON → User 对象");
        System.out.println("    参数解析: RequestResponseBodyMethodProcessor → HttpMessageConverter 反序列化");
        store.put(user.getId(), user);
        return user;
    }

    public static class User {
        private Long id; private String name;
        public User() {}
        public User(Long id, String name) { this.id = id; this.name = name; }
        public Long getId() { return id; } public void setId(Long id) { this.id = id; }
        public String getName() { return name; } public void setName(String name) { this.name = name; }
    }
}
