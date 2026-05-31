package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring MVC 学习项目 —— 启动类
 *
 * <p>启动后观察控制台日志，可以看到 DispatcherServlet 初始化时注册的关键组件：
 * <ul>
 *   <li>HandlerMapping   —— 请求路径 → 控制器方法的映射</li>
 *   <li>HandlerAdapter   —— 根据控制器类型选择合适的适配器去调用</li>
 *   <li>HandlerExceptionResolver —— 异常解析器（@ControllerAdvice 依赖它）</li>
 *   <li>HttpMessageConverter   —— 请求体/响应体的序列化转换</li>
 * </ul>
 *
 * <p>启动日志示例：
 * <pre>
 *   RequestMappingHandlerMapping   : Mapped "{[/users],methods=[GET]}" onto ...
 *   RequestMappingHandlerAdapter   : ...
 *   HttpMessageConverter           : ...
 * </pre>
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
