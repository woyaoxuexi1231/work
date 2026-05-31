package com.example.demo.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义参数注解 —— 标注在 Controller 方法参数上，自动注入"当前登录用户"
 *
 * <p>用法示例：
 * <pre>
 *   @GetMapping("/profile")
 *   public User profile(@CurrentUser User currentUser) {
 *       return currentUser;  // 无需手动从 session/header 中解析
 *   }
 * </pre>
 *
 * <p>工作原理：自定义的 {@code CurrentUserArgumentResolver} 扫描到带此注解的参数后，
 * 从请求头 "X-User-Id" 中取出用户 ID，构造 User 对象注入。
 *
 * <p>这就是你聊的 "参数解析器" 的实战用法 ——
 * 需要两步：① 写解析器  ② 通过 WebMvcConfigurer.addArgumentResolvers 注册
 */
@Target(ElementType.PARAMETER)          // 只能用在方法参数上
@Retention(RetentionPolicy.RUNTIME)     // 运行时保留，Spring 才能通过反射读到
@Documented
public @interface CurrentUser {
}
