package work.N1javabasic.old.day2.code.cglib;

import net.sf.cglib.core.DebuggingClassWriter;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.CallbackFilter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import net.sf.cglib.proxy.NoOp;
import work.N1javabasic.old.day2.code.MySQLService;
import work.N1javabasic.old.day2.code.MySQLServiceImpl;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CallbackFilter 详细演示
 * <p>
 * 【CallbackFilter 是什么？】
 * CallbackFilter 是 CGLIB 的回调过滤器，可以根据不同的方法选择不同的 Callback。
 * <p>
 * 【为什么需要 CallbackFilter？】
 * 有时候我们只想代理某些方法，而不是所有方法。
 * 例如：只想拦截 update() 方法，但不想拦截 toString()、hashCode() 等方法。
 * <p>
 * 【工作流程】
 * 1. 定义多个 Callback（拦截器）
 * 2. 创建 CallbackFilter，根据方法名返回 Callback 的索引
 * 3. Enhancer 根据 Filter 的返回值选择对应的 Callback
 * <p>
 * 【运行后查看】
 * 查看生成的字节码，理解 CGLIB 如何实现方法级别的过滤
 *
 * @author hulei
 * @since 2026/4/22
 */
public class CallbackFilterDemo {

    private static final String OUTPUT_DIR = "target/callback-filter-demo";

    public static void main(String[] args) throws Exception {
        System.out.println("========== CallbackFilter 演示 ==========");

        // 创建输出目录
        Path outputPath = Paths.get(OUTPUT_DIR);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        // 开启调试模式
        System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, OUTPUT_DIR);

        // 创建目标对象
        MySQLServiceImpl target = new MySQLServiceImpl();

        System.out.println("\n========== 创建 Callback ==========");
        // Callback 0: 拦截器 - 用于 update 方法
        MethodInterceptor updateInterceptor = new MethodInterceptor() {
            @Override
            public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
                System.out.println("  🔍 [Update拦截器] 拦截到 update 方法");
                System.out.println("  🔍 [Update拦截器] 参数: " + args[0]);
                Object result = methodProxy.invokeSuper(o, objects);
                System.out.println("  ✅ [Update拦截器] update 方法完成");
                return result;
            }
        };

        // Callback 1: NoOp - 什么都不做（直接调用原方法）
        Callback noopCallback = NoOp.INSTANCE;

        System.out.println("✅ Callback[0]: UpdateInterceptor（拦截 update 方法）");
        System.out.println("✅ Callback[1]: NoOp（不拦截，直接调用原方法）");

        System.out.println("\n========== 创建 CallbackFilter ==========");
        CallbackFilter filter = new CallbackFilter() {
            @Override
            public int accept(Method method) {
                String methodName = method.getName();
                System.out.println("  📋 CallbackFilter 检查方法: " + methodName);

                // 如果是 update 方法，使用 Callback[0]（拦截器）
                if ("update".equals(methodName)) {
                    System.out.println("  → 返回索引 0（使用 UpdateInterceptor）");
                    return 0;
                }
                
                // 其他方法使用 Callback[1]（NoOp）
                System.out.println("  → 返回索引 1（使用 NoOp）");
                return 1;
            }
        };

        System.out.println("\n========== 创建代理对象 ==========");
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(MySQLServiceImpl.class);
        enhancer.setCallbacks(new Callback[]{updateInterceptor, noopCallback});
        enhancer.setCallbackFilter(filter);

        MySQLService proxy = (MySQLService) enhancer.create();
        System.out.println("✅ 代理对象创建成功");
        System.out.println("✅ 代理类名: " + proxy.getClass().getName());

        System.out.println("\n========== 测试不同方法 ==========");
        
        System.out.println("\n【测试1】调用 update 方法（应该被拦截）:");
        proxy.update("测试数据-001");

        System.out.println("\n【测试2】调用 toString 方法（不应该被拦截）:");
        String str = proxy.toString();
        System.out.println("toString 返回值: " + str);

        System.out.println("\n【测试3】调用 hashCode 方法（不应该被拦截）:");
        int hash = proxy.hashCode();
        System.out.println("hashCode 返回值: " + hash);

        System.out.println("\n========== 生成的字节码文件 ==========");
        File outputDir = new File(OUTPUT_DIR);
        File[] classFiles = outputDir.listFiles((dir, name) -> name.endsWith(".class"));

        if (classFiles != null && classFiles.length > 0) {
            System.out.println("共生成 " + classFiles.length + " 个 .class 文件：\n");
            for (int i = 0; i < classFiles.length; i++) {
                File file = classFiles[i];
                System.out.println((i + 1) + ". " + file.getName());
                System.out.println("   大小: " + file.length() + " bytes");
            }
        }

        System.out.println("\n========== 关键知识点 ==========");
        System.out.println("1. CallbackFilter 的 accept() 方法返回 Callback 数组的索引");
        System.out.println("2. 返回 0 → 使用 Callbacks[0]（UpdateInterceptor）");
        System.out.println("3. 返回 1 → 使用 Callbacks[1]（NoOp）");
        System.out.println("4. 这样可以精确控制哪些方法需要拦截，哪些不需要");
        System.out.println("5. Spring AOP 中的 @Pointcut 就是类似的原理");

        System.out.println("\n打开目录查看: " + outputPath.toAbsolutePath());
        System.out.println("========================================");
    }
}
