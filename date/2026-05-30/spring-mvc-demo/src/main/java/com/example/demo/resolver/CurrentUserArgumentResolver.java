package com.example.demo.resolver;

import com.example.demo.annotation.CurrentUser;
import com.example.demo.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.HttpServletRequest;

/**
 * 自定义参数解析器 —— 注入 @CurrentUser 标注的参数
 *
 * <p>这是 MVC 流程中的第 ③ 环（参数解析）的自定义扩展点。
 *
 * <h3>MVC 流程回顾（一个请求进来后）</h3>
 * <ol>
 *   <li><b>HandlerMapping</b>  —— 根据 URL 找到对应的 Controller 方法</li>
 *   <li><b>HandlerAdapter</b>   —— 决定用哪种适配器调用（@Controller → RequestMappingHandlerAdapter）</li>
 *   <li><b>ArgumentResolver</b> —— 👈 当前组件！把 HTTP 请求参数转换成 Java 方法参数</li>
 *   <li><b>ReturnValueHandler</b> + <b>HttpMessageConverter</b> —— 把返回值序列化成 HTTP 响应体</li>
 * </ol>
 *
 * <h3>为什么你的自定义解析器可能不生效？</h3>
 * <ol>
 *   <li>❌ 只加了 {@code @Component}，没在 {@code WebMvcConfigurer.addArgumentResolvers} 里注册</li>
 *   <li>❌ {@code supportsParameter} 逻辑有误，返回了 false</li>
 *   <li>❌ 解析器的优先级低于 Spring 内置的解析器，被抢了</li>
 * </ol>
 *
 * <p>正确做法：本类标注 {@code @Component} <b>并</b> 在 WebMvcConfig 中注册。
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserArgumentResolver.class);

    /**
     * 判断当前参数是否由本解析器处理。
     * Spring 会遍历所有注册的 ArgumentResolver，找到第一个返回 true 的。
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // 参数上有 @CurrentUser 注解 → 我来处理
        boolean hasAnnotation = parameter.hasParameterAnnotation(CurrentUser.class);
        // 参数类型是 User → 我来处理
        boolean isUserType = User.class.isAssignableFrom(parameter.getParameterType());
        boolean supported = hasAnnotation && isUserType;

        if (supported) {
            log.debug("CurrentUserArgumentResolver 接管参数: {}", parameter.getParameterName());
        }
        return supported;
    }

    /**
     * 实际解析参数 —— 从 HTTP 请求中提取数据，构造 Java 对象。
     *
     * <p>这里模拟从请求头 "X-User-Id" 中读取用户 ID，
     * 实际项目中可能从 JWT token、Session 中解析。
     */
    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {

        // 从 NativeWebRequest 拿到原生 HttpServletRequest
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);

        if (request == null) {
            log.warn("无法获取 HttpServletRequest，返回默认用户");
            return new User(0L, "匿名用户", "anonymous@example.com");
        }

        String userIdHeader = request.getHeader("X-User-Id");

        if (userIdHeader == null || userIdHeader.isEmpty()) {
            log.warn("请求头 X-User-Id 缺失，返回默认用户");
            return new User(0L, "匿名用户", "anonymous@example.com");
        }

        log.info("✅ CurrentUserArgumentResolver 解析到用户 ID: {}", userIdHeader);

        // 模拟从数据库加载（实际项目应注入 UserService）
        return new User(
                Long.parseLong(userIdHeader),
                "用户_" + userIdHeader,
                "user" + userIdHeader + "@example.com"
        );
    }
}
