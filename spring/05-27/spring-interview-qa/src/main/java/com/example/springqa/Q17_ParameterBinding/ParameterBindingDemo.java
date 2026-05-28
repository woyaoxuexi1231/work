package com.example.springqa.Q17_ParameterBinding;

import org.springframework.stereotype.Component;

/**
 * <h1>Q17：参数绑定与转换 — HttpMessageConverter</h1>
 */
@Component
public class ParameterBindingDemo {

    public String runDemo() {
        return "=== Q17: 参数绑定 ===\n\n" +
            "@RequestParam 底层流程:\n" +
            "  request.getParameter(\"name\") → \"Alice\" (String)\n" +
            "  → TypeConverter.convertIfNecessary(\"25\", Integer.class) → 25\n\n" +
            "@PathVariable 底层流程:\n" +
            "  URL /users/42 → 提取 \"42\" → TypeConverter → 42 (Long)\n\n" +
            "@RequestBody 底层流程:\n" +
            "  request.getInputStream() → 字节流\n" +
            "  → Content-Type: application/json\n" +
            "  → 遍历 HttpMessageConverters 链:\n" +
            "    1. StringHttpMessageConverter.canRead(User, json)? → false\n" +
            "    2. FormHttpMessageConverter.canRead(User, json)? → false\n" +
            "    3. MappingJackson2HttpMessageConverter.canRead(User, json)? → true!\n" +
            "  → objectMapper.readValue(inputStream, User.class)\n\n" +
            "关键区别:\n" +
            "  @RequestParam / @PathVariable: 字符串 → 简单类型，用 TypeConverter\n" +
            "  @RequestBody: HTTP Body 字节流 → 复杂对象，用 HttpMessageConverter\n\n" +
            "【Spring 为什么这样设计？】\n" +
            "HttpMessageConverter 是策略模式——根据不同 Content-Type 选择不同 Converter。\n" +
            "新增数据格式（如 Protobuf）只需实现一个 Converter，Controller 代码不用改。\n";
    }
}
