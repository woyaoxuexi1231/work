package work.N1javabasic.v1.jvm;

public class ClassLoaderHierarchy {
    public static void main(String[] args) {
        // 1. 当前线程的上下文类加载器
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        System.out.println("线程上下文类加载器: " + contextLoader);

        // 2. 系统类加载器（加载用户类路径）
        ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
        System.out.println("系统类加载器: " + systemLoader);

        // 3. 扩展类加载器（JDK 9+ 为平台类加载器，概念类似）
        ClassLoader extLoader = systemLoader.getParent();
        System.out.println("扩展/平台类加载器: " + extLoader);

        // 4. 启动类加载器
        ClassLoader bootstrapLoader = extLoader.getParent();
        System.out.println("启动类加载器: " + bootstrapLoader); // 输出 null

        // 5. 核心类 String 由哪个加载器加载？
        ClassLoader stringLoader = String.class.getClassLoader();
        System.out.println("String 类加载器: " + stringLoader); // null，即 Bootstrap

        // 6. 自定义类由哪个加载器加载？
        ClassLoader myLoader = ClassLoaderHierarchy.class.getClassLoader();
        System.out.println("当前类的类加载器: " + myLoader);
        // 通常为系统类加载器，除非以自定义加载器运行
    }
}