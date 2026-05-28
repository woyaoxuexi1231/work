package com.example.springqa.Q04_FactoryBean;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * <h1>Q4：FactoryBean — 不是 BeanFactory</h1>
 *
 * <h2>区别</h2>
 * <pre>
 * BeanFactory  — Spring IOC 容器顶层接口，生产所有 Bean（"工厂的工厂"）
 * FactoryBean  — 一个特殊的 Bean，定制某个 Bean 的创建逻辑
 *                 getBean("xxx") 返回的是 getObject() 的产物，不是 FactoryBean 本身
 *                 getBean("&xxx") 返回 FactoryBean 本身
 * </pre>
 *
 * <h2>MyBatis 怎么用？</h2>
 * <p>MapperFactoryBean 为每个 Mapper 接口生成 JDK 动态代理，
 * getObject() 返回 MapperProxy，拦截方法调用 → 执行 SQL。</p>
 *
 * <h2>getObject() 产物能被 AOP 增强吗？</h2>
 * <p>能。产物经过完整的 BeanPostProcessor 链。</p>
 */
@Component
public class FactoryBeanDemo {

    private final ApplicationContext ctx;

    public FactoryBeanDemo(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public String runDemo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Q04: FactoryBean ===\n\n");

        // getBean("userMapper") → getObject() 产物（JDK 代理）
        UserMapper mapper = ctx.getBean("q04_userMapper", UserMapper.class);
        sb.append("getBean(\"q04_userMapper\"): ").append(mapper.getClass().getName()).append("\n");
        sb.append("mapper.findById(1): ").append(mapper.findById(1L)).append("\n\n");

        // getBean("&userMapper") → FactoryBean 本身
        Object fb = ctx.getBean("&q04_userMapper");
        sb.append("getBean(\"&q04_userMapper\"): ").append(fb.getClass().getName()).append("\n\n");

        sb.append("【MyBatis 集成原理】\n");
        sb.append("1. @MapperScan 为每个 Mapper 接口注册 MapperFactoryBean\n");
        sb.append("2. getObject() 返回 JDK 动态代理（MapperProxy）\n");
        sb.append("3. 调用 mapper.findById(1L) → 代理拦截 → 执行 SQL → 返回结果\n");
        sb.append("4. 为什么用 JDK 代理？Mapper 是接口，没有实现类，天然适合接口代理\n\n");

        sb.append("【面试关键】\n");
        sb.append("FactoryBean 以 Bean 结尾（它是一个 Bean），\n");
        sb.append("BeanFactory 以 Factory 结尾（它是工厂）。\n");

        return sb.toString();
    }

    // ================================================================
    interface UserMapper {
        String findById(Long id);
        int insert(String user);
    }

    @Configuration
    static class DemoConfig {
        @Bean
        public MapperFactoryBean<UserMapper> q04_userMapper() {
            return new MapperFactoryBean<>(UserMapper.class);
        }
    }

    static class MapperFactoryBean<T> implements FactoryBean<T> {
        private final Class<T> mapperInterface;

        MapperFactoryBean(Class<T> mapperInterface) { this.mapperInterface = mapperInterface; }

        @Override @SuppressWarnings("unchecked")
        public T getObject() {
            return (T) Proxy.newProxyInstance(
                    mapperInterface.getClassLoader(),
                    new Class<?>[]{mapperInterface},
                    new MapperProxy());
        }

        @Override public Class<?> getObjectType() { return mapperInterface; }
        @Override public boolean isSingleton() { return true; }
    }

    static class MapperProxy implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("findById".equals(method.getName())) {
                return "User{id=" + args[0] + ", name='模拟用户'}";
            }
            if ("insert".equals(method.getName())) return 1;
            try { return method.invoke(this, args); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
    }
}
