package work.N1javabasic.old.day2.code.cglib;

import net.sf.cglib.core.DebuggingClassWriter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import work.N1javabasic.old.day2.code.MySQLService;
import work.N1javabasic.old.day2.code.MySQLServiceImpl;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CGLIB 代理完整演示 - 字节码生成与反编译分析
 * <p>
 * 【这个类的作用】
 * 1. 展示 CGLIB 如何动态生成代理类的字节码
 * 2. 将生成的 .class 文件保存到本地
 * 3. 让你可以反编译查看 CGLIB 生成的代码
 * <p>
 * 【运行步骤】
 * 1. 运行 main 方法
 * 2. 查看控制台输出
 * 3. 打开生成的 .class 文件所在目录
 * 4. 使用 JD-GUI 或 IDEA 反编译查看
 * <p>
 * 【输出位置】
 * 生成的字节码文件会保存在：target/cglib-proxy-classes/ 目录
 *
 * @author hulei
 * @since 2026/4/22
 */
public class CglibBytecodeDemo {

    /**
     * 字节码保存路径
     */
    private static final String OUTPUT_DIR = "target/cglib-proxy-classes";

    public static void main(String[] args) throws Exception {
        System.out.println("========== CGLIB 代理字节码生成演示 ==========");

        // 步骤1：创建保存目录
        Path outputPath = Paths.get(OUTPUT_DIR);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }
        System.out.println("✅ 字节码保存目录: " + outputPath.toAbsolutePath());

        // 步骤2：设置 CGLIB 调试模式 - 这会让 CGLIB 保存生成的字节码
        System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, outputPath.toString());
        System.out.println("✅ 已开启 CGLIB 调试模式，字节码将保存到: " + outputPath);

        // 步骤3：创建目标对象
        MySQLServiceImpl target = new MySQLServiceImpl();
        System.out.println("✅ 创建目标对象: " + target.getClass().getName());

        // 步骤4：使用 Enhancer 生成代理
        System.out.println("⏳ 开始生成 CGLIB 代理...");
        Enhancer enhancer = new Enhancer();

        // 设置父类（CGLIB 通过继承实现代理）
        enhancer.setSuperclass(MySQLServiceImpl.class);
        System.out.println("✅ 设置父类: " + MySQLServiceImpl.class.getName());

        // 设置回调（拦截器）
        enhancer.setCallback(new SimpleMethodInterceptor());
        System.out.println("✅ 设置回调拦截器");

        // 创建代理对象
        MySQLService proxy = (MySQLService) enhancer.create();
        System.out.println("✅ CGLIB 代理对象生成成功！");
        System.out.println("✅ 代理类名: " + proxy.getClass().getName());
        System.out.println("✅ 代理类父类: " + proxy.getClass().getSuperclass().getName());

        System.out.println("\n========== 生成的字节码文件 ==========");
        // 步骤5：列出所有生成的 .class 文件
        File outputDir = new File(OUTPUT_DIR);
        File[] classFiles = outputDir.listFiles((dir, name) -> name.endsWith(".class"));

        if (classFiles != null && classFiles.length > 0) {
            System.out.println("共生成 " + classFiles.length + " 个 .class 文件：\n");
            for (int i = 0; i < classFiles.length; i++) {
                File file = classFiles[i];
                System.out.println((i + 1) + ". " + file.getName());
                System.out.println("   路径: " + file.getAbsolutePath());
                System.out.println("   大小: " + file.length() + " bytes");
            }
        }

        System.out.println("\n========== 代理对象方法调用演示 ==========");
        // 步骤6：调用代理方法
        System.out.println("📢 调用代理对象的 update 方法...");
        proxy.update("测试数据-001");

        System.out.println("\n========== 关键知识点 ==========");
        System.out.println("1. CGLIB 通过继承实现代理（不是实现接口）");
        System.out.println("2. 代理类继承 MySQLServiceImpl，所以可以直接代理没有接口的类");
        System.out.println("3. 生成的代理类名格式: MySQLServiceImpl$$EnhancerByCGLIB$$<hash>");
        System.out.println("4. 代理类重写了所有非 final 方法，加入拦截逻辑");
        System.out.println("5. 你可以在 IDEA 中打开生成的 .class 文件查看完整代码");

        System.out.println("\n========== 如何反编译查看 ==========");
        System.out.println("方式1：直接用 IDEA 打开 .class 文件（IDEA 会自动反编译）");
        System.out.println("方式2：使用 JD-GUI 工具（下载地址: https://github.com/java-decompiler/jd-gui）");
        System.out.println("方式3：使用命令行 javap -c <class文件>");
        System.out.println("\n打开目录: " + outputPath.toAbsolutePath());
        System.out.println("========================================");
    }

    /**
     * 简单的方法拦截器
     */
    static class SimpleMethodInterceptor implements MethodInterceptor {

        @Override
        public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
            System.out.println("🔍 [拦截前] 方法: " + method.getName() + ", 参数: " + java.util.Arrays.toString(args));

            // 调用原始方法
            Object result = proxy.invokeSuper(obj, args);

            System.out.println("✅ [拦截后] 方法执行完成");
            return result;
        }
    }
}
