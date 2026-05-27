package com.example.springqa.Q17_ParameterBinding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.util.List;

/**
 * <h1>Q17：参数绑定与转换 — HttpMessageConverter</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>@RequestParam / @RequestBody / @PathVariable 的底层实现有何不同？</li>
 *   <li>HttpMessageConverter 负责什么？</li>
 *   <li>FormHttpMessageConverter 和 MappingJackson2HttpMessageConverter 何时工作？</li>
 * </ul>
 *
 * <h2>关键区别</h2>
 * <p>@RequestParam 和 @PathVariable 不使用 HttpMessageConverter，
 * 直接从 request 获取字符串然后用 TypeConverter 做类型转换。</p>
 * <p>只有 @RequestBody 才走 HttpMessageConverter——
 * 根据 Content-Type 选择合适的 Converter 解析 HTTP Body 字节流。</p>
 *
 * <h2>Spring 为什么这样设计？</h2>
 * <p>HttpMessageConverter 是"策略模式"的经典应用。
 * 通过 Converter 链，新增数据格式只需实现一个 Converter——
 * Controller 代码完全不用改。</p>
 *
 * @author Spring Interview QA
 */
public class ParameterBindingDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== Q17: 参数绑定 Demo ==========\n");
        demoHttpMessageConverter();
        System.out.println("\n--- 参数绑定对比 ---");
        demoParameterComparison();
        System.out.println("\n========== Demo 结束 ==========");
    }

    static void demoHttpMessageConverter() throws IOException {
        System.out.println("--- HttpMessageConverter 序列化/反序列化 ---");

        ObjectMapper objectMapper = new ObjectMapper();

        // JSON 转换器
        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter();
        System.out.println("MappingJackson2HttpMessageConverter:");
        System.out.println("  支持的 MediaType: " + jsonConverter.getSupportedMediaTypes());
        System.out.println("  可读写 User.class: "
                + jsonConverter.canRead(User.class, MediaType.APPLICATION_JSON));

        // 序列化：Java → JSON
        User user = new User("Alice", 25);
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpOutputMessage outputMessage = new ServletServerHttpResponse(response);
        jsonConverter.write(user, MediaType.APPLICATION_JSON, outputMessage);
        System.out.println("  序列化结果: " + response.getContentAsString());

        // 反序列化：JSON → Java
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(response.getContentAsByteArray());
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        HttpInputMessage inputMessage = new ServletServerHttpRequest(request);
        User parsed = (User) jsonConverter.read(User.class, inputMessage);
        System.out.println("  反序列化结果: " + parsed);

        // Form 转换器
        FormHttpMessageConverter formConverter = new FormHttpMessageConverter();
        System.out.println("\nFormHttpMessageConverter:");
        System.out.println("  支持的 MediaType: " + formConverter.getSupportedMediaTypes());
        System.out.println("  可读写 MultiValueMap: "
                + formConverter.canRead(MultiValueMap.class, MediaType.APPLICATION_FORM_URLENCODED));

        // String 转换器
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter();
        System.out.println("\nStringHttpMessageConverter:");
        System.out.println("  支持的 MediaType: " + stringConverter.getSupportedMediaTypes());

        /*
         * Converter 选择逻辑（策略链模式）：
         * for (HttpMessageConverter<?> converter : converters) {
         *     if (converter.canRead(targetClass, contentType)) {
         *         return converter.read(targetClass, inputMessage);
         *     }
         * }
         * 第一个 canRead() 返回 true 的 Converter 被用来反序列化。
         */
    }

    static void demoParameterComparison() {
        System.out.println("@RequestParam 底层流程:");
        System.out.println("  request.getParameter(\"name\") → \"Alice\" (String)");
        System.out.println("  → TypeConverter.convertIfNecessary(\"25\", Integer.class) → 25");
        System.out.println();
        System.out.println("@PathVariable 底层流程:");
        System.out.println("  URL 模板 /users/{id} → 匹配 \"/users/42\"");
        System.out.println("  → 从 URL 提取 \"42\" → TypeConverter → 42 (Long)");
        System.out.println();
        System.out.println("@RequestBody 底层流程:");
        System.out.println("  request.getInputStream() → 字节流");
        System.out.println("  → Content-Type: application/json");
        System.out.println("  → 遍历 HttpMessageConverters 链:");
        System.out.println("    1. StringHttpMessageConverter.canRead(User, json)? → false");
        System.out.println("    2. FormHttpMessageConverter.canRead(User, json)? → false");
        System.out.println("    3. MappingJackson2HttpMessageConverter.canRead(User, json)? → true!");
        System.out.println("  → objectMapper.readValue(inputStream, User.class)");
        System.out.println("  → User{name=\"Alice\", age=25}");
        System.out.println();
        System.out.println("关键区别:");
        System.out.println("  @RequestParam / @PathVariable: 字符串 → 简单类型，用 TypeConverter");
        System.out.println("  @RequestBody: HTTP Body 字节流 → 复杂对象，用 HttpMessageConverter");
    }

    static class User {
        public String name;
        public int age;
        public User() {}
        public User(String name, int age) { this.name = name; this.age = age; }
        @Override
        public String toString() { return "User{name='" + name + "', age=" + age + "}"; }
    }
}
