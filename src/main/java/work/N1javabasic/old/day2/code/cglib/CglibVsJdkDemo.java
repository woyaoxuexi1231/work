package work.N1javabasic.old.day2.code.cglib;

import net.sf.cglib.core.DebuggingClassWriter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import work.N1javabasic.old.day2.code.MySQLService;
import work.N1javabasic.old.day2.code.MySQLServiceImpl;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CGLIB vs JDK 动态代理对比演示
 * <p>
 * 【核心区别】
 * 1. JDK 代理：基于接口（只能代理实现了接口的类）
 * 2. CGLIB 代理：基于继承（可以代理任何类，包括没有接口的类）
 * <p>
 * 【运行后查看】
 * - JDK 代理类：$Proxy0.class（实现接口）
 * - CGLIB 代理类：MySQLServiceImpl$$EnhancerByCGLIB$$xxx.class（继承类）
 *
 * @author hulei
 * @since 2026/4/22
 */
public class CglibVsJdkDemo {

    private static final String OUTPUT_DIR = "target/proxy-comparison";

    public static void main(String[] args) throws Exception {
        System.out.println("========== JDK vs CGLIB 代理对比演示 ==========");

        // 创建输出目录
        Path outputPath = Paths.get(OUTPUT_DIR);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        MySQLServiceImpl target = new MySQLServiceImpl();

        System.out.println("\n========== 1. JDK 动态代理 ==========");
        MySQLService jdkProxy = createJdkProxy(target);
        System.out.println("代理类名: " + jdkProxy.getClass().getName());
        System.out.println("代理类父类: " + jdkProxy.getClass().getSuperclass().getName());
        System.out.println("实现的接口: " + java.util.Arrays.toString(jdkProxy.getClass().getInterfaces()));
        
        System.out.println("\n调用 JDK 代理方法:");
        jdkProxy.update("JDK代理测试");

        System.out.println("\n========== 2. CGLIB 代理 ==========");
        // 设置 CGLIB 保存生成的字节码
        System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, OUTPUT_DIR);
        
        MySQLService cglibProxy = createCglibProxy(target);
        System.out.println("代理类名: " + cglibProxy.getClass().getName());
        System.out.println("代理类父类: " + cglibProxy.getClass().getSuperclass().getName());
        System.out.println("实现的接口: " + java.util.Arrays.toString(cglibProxy.getClass().getInterfaces()));

        System.out.println("\n调用 CGLIB 代理方法:");
        cglibProxy.update("CGLIB代理测试");

        System.out.println("\n========== 3. 生成的字节码文件 ==========");
        File outputDir = new File(OUTPUT_DIR);
        File[] classFiles = outputDir.listFiles((dir, name) -> name.endsWith(".class"));

        if (classFiles != null && classFiles.length > 0) {
            System.out.println("共生成 " + classFiles.length + " 个 .class 文件：\n");
            for (int i = 0; i < classFiles.length; i++) {
                File file = classFiles[i];
                String type = file.getName().startsWith("$Proxy") ? "【JDK代理】" : "【CGLIB代理】";
                System.out.println((i + 1) + ". " + type + " " + file.getName());
                System.out.println("   大小: " + file.length() + " bytes");
            }
        }

        System.out.println("\n========== 核心区别总结 ==========");
        System.out.println("【JDK 代理】");
        System.out.println("  - 代理类名: $Proxy0");
        System.out.println("  - 继承: java.lang.reflect.Proxy");
        System.out.println("  - 实现: MySQLService 接口");
        System.out.println("  - 原理: 实现接口，转发方法调用到 InvocationHandler");
        System.out.println("  - 限制: 只能代理实现了接口的类");
        
        System.out.println("\n【CGLIB 代理】");
        System.out.println("  - 代理类名: MySQLServiceImpl$$EnhancerByCGLIB$$<hash>");
        System.out.println("  - 继承: MySQLServiceImpl（目标类）");
        System.out.println("  - 实现: Factory 接口（CGLIB 内部接口）");
        System.out.println("  - 原理: 继承目标类，重写所有非 final 方法");
        System.out.println("  - 优势: 可以代理没有接口的类");

        System.out.println("\n打开目录查看: " + outputPath.toAbsolutePath());
        System.out.println("========================================");
    }

    /**
     * 创建 JDK 动态代理
     */
    private static MySQLService createJdkProxy(MySQLService target) {
        return (MySQLService) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        System.out.println("  [JDK拦截] 方法: " + method.getName());
                        Object result = method.invoke(target, args);
                        System.out.println("  [JDK拦截] 完成");
                        return result;
                    }
                }
        );
    }

    /**
     * 创建 CGLIB 代理
     */
    private static MySQLService createCglibProxy(MySQLServiceImpl target) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(target.getClass());
        enhancer.setCallback(new MethodInterceptor() {
            @Override
            public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
                System.out.println("  [CGLIB拦截] 方法: " + method.getName());
                Object result = proxy.invokeSuper(obj, args);
                System.out.println("  [CGLIB拦截] 完成");
                return result;
            }
        });
        return (MySQLService) enhancer.create();
    }
}
