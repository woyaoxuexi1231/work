package com.example.springqa.Q04_FactoryBean;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * <h1>Q4：FactoryBean — 不是 BeanFactory</h1>
 *
 * <h2>面试点</h2>
 * <ul>
 *   <li>FactoryBean 和 BeanFactory 是一回事吗？</li>
 *   <li>MyBatis 的 Mapper 接口是怎么通过 FactoryBean 生成代理的？</li>
 *   <li>getObject() 返回的对象能否被 AOP 增强？</li>
 * </ul>
 *
 * <h2>FactoryBean vs BeanFactory</h2>
 * <pre>
 * BeanFactory   — 是 Spring IOC 容器的顶层接口，负责管理 Bean 的创建和获取。
 *                 它是"工厂的工厂"——生产所有 Bean。
 *
 * FactoryBean   — 是一个特殊的 Bean，它自己是一个工厂，用于定制某个 Bean 的创建逻辑。
 *                 当 getBean("xxx") 时，返回的不是 FactoryBean 本身，
 *                 而是 FactoryBean.getObject() 的返回值。
 *
 * 记法：BeanFactory 以 Factory 结尾（它是工厂），
 *       FactoryBean 以 Bean 结尾（它是一个 Bean，只不过是一个工厂 Bean）。
 * </pre>
 *
 * <h2>MyBatis 怎么用 FactoryBean？</h2>
 * <p>MyBatis-Spring 的 {@code MapperFactoryBean} 就是典型应用：</p>
 * <pre>
 * // 你定义了一个接口
 * public interface UserMapper {
 *     &#64;Select("SELECT * FROM users WHERE id = #{id}")
 *     User findById(Long id);
 * }
 *
 * // MyBatis 为每个 Mapper 接口注册一个 MapperFactoryBean
 * // getObject() 返回的是 JDK 动态代理（MapperProxy）
 * // 调用 mapper.findById(1L) → 代理拦截 → 执行 SQL → 返回结果
 * </pre>
 *
 * <h2>getObject() 返回的对象能否被 AOP 增强？</h2>
 * <p><b>能。</b>Spring 对 FactoryBean 有特殊处理：</p>
 * <ol>
 *   <li>getBean("&myFactoryBean") → 返回 FactoryBean 本身</li>
 *   <li>getBean("myFactoryBean")  → 返回 getObject() 的产物</li>
 *   <li>产物会经过完整的 BeanPostProcessor 链，包括 AOP 代理生成</li>
 * </ol>
 * <p>Spring 这样设计是因为 99% 的场景下用户关心的是 getObject() 的产物，
 * 而不是 FactoryBean 本身。但保留了 {@code &} 前缀作为"逃生舱"。</p>
 *
 * @author Spring Interview QA
 */
public class FactoryBeanDemo {

    public static void main(String[] args) {
        System.out.println("========== Q4: FactoryBean Demo ==========\n");

        AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(DemoConfig.class);

        // ===== 重点 =====
        // getBean("userMapper") 返回的是 getObject() 产物（JDK 代理），不是 MapperFactoryBean
        UserMapper mapper = ctx.getBean("userMapper", UserMapper.class);
        System.out.println(">>> getBean(\"userMapper\") 返回类型: " + mapper.getClass().getName());
        System.out.println(">>> mapper.findById(1): " + mapper.findById(1L));

        // getBean("&userMapper") 返回 FactoryBean 本身
        Object factoryBean = ctx.getBean("&userMapper");
        System.out.println("\n>>> getBean(\"&userMapper\") 返回类型: " + factoryBean.getClass().getName());

        ctx.close();
        System.out.println("\n========== Demo 结束 ==========");
    }

    // ================================================================
    // 模拟 MyBatis 的 Mapper 接口
    // ================================================================

    interface UserMapper {
        String findById(Long id);
        int insert(String user);
    }

    // ================================================================
    // MapperFactoryBean — 模拟 MyBatis-Spring 的核心机制
    // ================================================================
    static class MapperFactoryBean<T> implements FactoryBean<T> {

        private final Class<T> mapperInterface;

        public MapperFactoryBean(Class<T> mapperInterface) {
            this.mapperInterface = mapperInterface;
        }

        /**
         * 返回的是 JDK 动态代理对象，不是 FactoryBean 自身。
         *
         * 【Spring 设计意图】
         * 为什么 FactoryBean 是一个接口而不是抽象类？
         * 因为 Spring 想让任何 Bean 都能成为"工厂"——
         * 只要实现这个接口，就能完全控制 Bean 的创建逻辑。
         * 这比 XML 的 factory-method 属性灵活得多。
         */
        @Override
        @SuppressWarnings("unchecked")
        public T getObject() {
            /*
             * 这一步对应 MyBatis 的 MapperProxy：
             * 为 Mapper 接口生成 JDK 动态代理，拦截所有方法调用，
             * 将方法名 + 参数转换为 SQL 执行。
             *
             * 为什么用 JDK 动态代理而不是 CGLIB？
             * 因为 Mapper 是接口，没有实现类——JDK 动态代理天然适合接口代理。
             * CGLIB 基于继承，需要有一个实现类才能生成子类代理。
             */
            return (T) Proxy.newProxyInstance(
                    mapperInterface.getClassLoader(),
                    new Class<?>[]{mapperInterface},
                    new MapperProxy()
            );
        }

        @Override
        public Class<?> getObjectType() {
            return mapperInterface;
        }

        /**
         * 单例：整个容器中只有一个 Mapper 代理。
         * MyBatis 的 Mapper 默认是单例的——因为代理对象是无状态的，
         * 所有状态（SqlSession）由 Spring 的事务管理来保证线程安全。
         *
         * 【为什么 Mapper 是单例？】
         * 代理对象本身只是一个"转发器"，不持有数据。
         * 真正的数据库连接由 SqlSessionTemplate 管理，它保证每个线程
         * 拿到自己的 SqlSession。这样 Mapper 代理就可以安全地共享。
         */
        @Override
        public boolean isSingleton() {
            return true;
        }
    }

    /**
     * 模拟 MyBatis 的 MapperProxy —— JDK 动态代理的 InvocationHandler
     */
    static class MapperProxy implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            // 模拟 SQL 执行
            if ("findById".equals(method.getName())) {
                return "User{id=" + args[0] + ", name='模拟用户'}";
            }
            if ("insert".equals(method.getName())) {
                return 1; // affected rows
            }
            // Object 方法（toString, hashCode, equals）走正常逻辑
            return method.invoke(this, args);
        }
    }

    // ================================================================
    @Configuration
    static class DemoConfig {

        /**
         * 注意：这里返回的是 MapperFactoryBean，但注册到容器时，
         * Spring 会检测到它是 FactoryBean 类型，做特殊处理——
         * 把 getObjectType() 的返回值作为 Bean 的真实类型。
         */
        @Bean
        public MapperFactoryBean<UserMapper> userMapper() {
            System.out.println("  注册 MapperFactoryBean<UserMapper>");
            return new MapperFactoryBean<>(UserMapper.class);
        }
    }
}
