package com.example.demo.config;

import com.example.demo.converter.CsvHttpMessageConverter;
import com.example.demo.resolver.CurrentUserArgumentResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Spring MVC 自定义配置
 *
 * <p>这是 MVC 扩展的核心入口！你聊的那几个问题的答案都在这：
 *
 * <h3>① 自定义参数解析器为什么不生效？</h3>
 * <p>光加 {@code @Component} 不够！必须在这里调用 {@code addArgumentResolvers}
 * 把它注册到 Spring MVC 的解析器链中。Spring Boot 的自动配置发现不了自定义解析器。</p>
 *
 * <h3>② {@code @ResponseBody} 和 HttpMessageConverter 在哪绑定？</h3>
 * <p>{@code @ResponseBody} 由 {@code RequestResponseBodyMethodProcessor}（一个 ReturnValueHandler）
 * 处理，它遍历所有已注册的 {@code HttpMessageConverter}，找到第一个能写该类型的 converter。
 * 调用 {@code addHttpMessageConverters} 可以插入自定义 converter。</p>
 *
 * <h3>③ DispatcherServlet 初始化时注册了哪些组件？</h3>
 * <pre>
 *   DispatcherServlet.initStrategies() 会初始化：
 *   ├── HandlerMapping        (RequestMappingHandlerMapping)
 *   ├── HandlerAdapter        (RequestMappingHandlerAdapter)
 *   ├── HandlerExceptionResolver
 *   ├── ViewResolver
 *   ├── HandlerMethodArgumentResolver   ← 本类 addArgumentResolvers
 *   ├── HandlerMethodReturnValueHandler
 *   └── HttpMessageConverter           ← 本类 extendMessageConverters
 * </pre>
 */
@Configuration  // Spring 会自动扫描并应用此配置
public class WebMvcConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebMvcConfig.class);

    // ==================== 自定义参数解析器 ====================

    /**
     * 注册自定义的 HandlerMethodArgumentResolver。
     *
     * <p>添加顺序决定优先级 —— 先添加的优先匹配。
     * 如果自定义解析器不生效，排查清单：
     * <ol>
     *   <li>✅ 是否在此方法中注册了？</li>
     *   <li>✅ supportsParameter 是否返回 true？</li>
     *   <li>✅ 是否被内置解析器（如 RequestParamMethodArgumentResolver）提前抢走了？</li>
     * </ol>
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        log.info("注册自定义参数解析器: CurrentUserArgumentResolver");
        // 用 add(0, ...) 放在列表最前面，确保优先级最高
        resolvers.add(0, new CurrentUserArgumentResolver());
    }

    // ==================== 自定义 HttpMessageConverter ====================

    /**
     * 扩展 HttpMessageConverter 列表。
     *
     * <p>Spring Boot 默认注册的 Converter（按顺序）：
     * <ol>
     *   <li>ByteArrayHttpMessageConverter    —— byte[]</li>
     *   <li>StringHttpMessageConverter       —— String（text/plain）</li>
     *   <li>MappingJackson2HttpMessageConverter —— JSON（application/json）👈 最常用</li>
     * </ol>
     *
     * <p>当 Controller 方法上标注了 {@code @ResponseBody}：
     * <pre>
     *   RequestResponseBodyMethodProcessor 遍历此列表 →
     *     converter.canWrite(returnType, mediaType) →
     *       第一个返回 true 的 converter.write(...)
     * </pre>
     *
     * <p>我们在此方法中插入自定义的 CsvHttpMessageConverter，
     * 让客户端可以通过 {@code Accept: text/csv} 头获取 CSV 格式响应。
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        log.info("注册自定义 HttpMessageConverter: CsvHttpMessageConverter (支持 text/csv)");
        // 注册 CSV 转换器，支持 Accept: text/csv
        converters.add(new CsvHttpMessageConverter(MediaType.valueOf("text/csv")));
    }
}
