package work.N1javabasic.old.day13;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ThreadLocal 深度实战与原理演示
 *
 * 【核心内存模型】
 * Thread (线程对象)
 *   └── threadLocals (类型: ThreadLocalMap)
 *         └── table (Entry[] 数组)
 *               └── Entry (继承自 WeakReference<ThreadLocal<?>>)
 *                     ├── key   (弱引用，指向 ThreadLocal 对象)
 *                     └── value (强引用，指向存储的数据)
 *
 * 核心知识点：
 * 1. 线程隔离：数据存在 Thread 自己的 map 里，互不干扰。
 * 2. 弱引用设计：解决 ThreadLocal 对象本身的生命周期问题，防止 Key 内存泄漏。
 * 3. 内存泄漏陷阱：Value 是强引用，Key 被回收后 Value 可能残留，必须手动 remove。
 * 4. 线程池污染：由于线程复用，ThreadLocalMap 也会复用，不清理会导致数据错乱。
 */
public class ThreadLocalMasterTest {

    // 建议：static 修饰防止多实例浪费空间，final 修饰防止引用被修改
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    public static void main(String[] args) throws InterruptedException {
        printHeader("1. 演示线程隔离性 (Thread Isolation)");
        testIsolation();

        printHeader("2. 什么是弱引用 (WeakReference)？");
        testWeakReference();

        printHeader("3. 为什么 ThreadLocal 使用弱引用？(原理推演)");
        explainWhyWeakReference();

        printHeader("4. 演示 InheritableThreadLocal (父子线程传递)");
        testInheritable();

        printHeader("5. 演示线程池环境下的【数据污染】(生产事故高发点)");
        testThreadPoolLeak();
    }

    private static void printHeader(String title) {
        System.out.println("\n" + "=".repeat(10) + " " + title + " " + "=".repeat(10));
    }

    /**
     * 演示隔离性：
     * 哪怕是同一个 CONTEXT 对象，在不同线程 set 的值也是互相看不见的。
     */
    private static void testIsolation() throws InterruptedException {
        CONTEXT.set("主线程的数据");

        Thread t1 = new Thread(() -> {
            CONTEXT.set("线程-1 的私有数据");
            System.out.println("[Thread-1] 获取: " + CONTEXT.get());
            CONTEXT.remove(); // 规范：用完即删
        }, "Thread-1");

        Thread t2 = new Thread(() -> {
            // 注意：t2 没 set 过，它拿不到主线程或 t1 的数据
            System.out.println("[Thread-2] 初始获取: " + CONTEXT.get());
            CONTEXT.set("线程-2 的私有数据");
            System.out.println("[Thread-2] set 后获取: " + CONTEXT.get());
            CONTEXT.remove();
        }, "Thread-2");

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("[Main] 最终获取: " + CONTEXT.get());
        CONTEXT.remove();
    }

    /**
     * 弱引用 (WeakReference) 扫盲：
     * 只要触发 GC，且该对象没有强引用指向，弱引用就会被回收。
     */
    private static void testWeakReference() {
        // 强引用存在时
        Object data = new Object();
        WeakReference<Object> weakRef = new WeakReference<>(data);
        System.out.println("1. 强引用存在，获取弱引用: " + weakRef.get());

        // 断开强引用
        data = null;
        System.out.println("2. 断开强引用，尝试触发 GC...");
        System.gc(); // 提醒 JVM 回收

        // 弱引用被回收
        System.out.println("3. GC 后，获取弱引用: " + weakRef.get());
    }

    /**
     * 深度解析：ThreadLocalMap.Entry 为什么要继承 WeakReference？
     */
    private static void explainWhyWeakReference() {
        System.out.println("【逻辑推演】:");
        System.out.println("1. 如果 Entry 的 Key 是强引用：");
        System.out.println("   当你在业务代码里执行 `CONTEXT = null` 后，本意是想让这个 ThreadLocal 对象被回收。");
        System.out.println("   但是！线程的 ThreadLocalMap 依然持有着这个 Key 的强引用。");
        System.out.println("   结果：ThreadLocal 对象永远无法被回收，直到线程结束。这叫【Key 的内存泄漏】。");
        System.out.println("\n2. 如果 Entry 的 Key 是弱引用 (Java 的做法)：");
        System.out.println("   当你执行 `CONTEXT = null` 后，这个 ThreadLocal 对象就只剩下 Entry 里的弱引用了。");
        System.out.println("   下次 GC 时，ThreadLocal 对象会被顺利回收，Key 变为 null。");
        System.out.println("   结果：解决了 ThreadLocal 对象的泄露问题。");
        System.out.println("\n【终极警示】：虽然 Key 解决了，但 Value 是强引用！Key 变成 null 后，Value 还在 Entry 里。");
        System.out.println("   如果不调用 remove()，Value 依然会泄露。这就是为什么阿里规约强制要求 remove()。");
    }

    /**
     * 演示父子线程传递：
     * 很多时候我们需要在异步线程里拿到主线程的 UserContext。
     */
    private static void testInheritable() throws InterruptedException {
        InheritableThreadLocal<String> itl = new InheritableThreadLocal<>();
        itl.set("父线程的 TraceID: 88888");

        Thread child = new Thread(() -> {
            System.out.println("[子线程] 读取到的数据: " + itl.get());
            // 修改子线程的值，不会影响父线程
            itl.set("子线程修改后的值");
            System.out.println("[子线程] 修改后读取: " + itl.get());
        });

        child.start();
        child.join();

        System.out.println("[主线程] 最终读取: " + itl.get());
        itl.remove();
    }

    /**
     * 演示线程池污染：
     * 极其重要！这是最容易在面试中被问倒的实战场景。
     */
    private static void testThreadPoolLeak() throws InterruptedException {
        // 固定 1 个线程，方便观察复用
        ExecutorService pool = Executors.newFixedThreadPool(1);

        // 模拟任务 A 设置了数据没清理
        pool.execute(() -> {
            CONTEXT.set("用户-张三的权限");
            System.out.println(Thread.currentThread().getName() + " [任务A] 设置了张三的权限，但忘了 remove!");
        });

        TimeUnit.MILLISECONDS.sleep(200);

        // 模拟任务 B 执行，它本来应该是没有任何权限的
        pool.execute(() -> {
            String val = CONTEXT.get();
            if (val != null) {
                System.err.println("【事故】" + Thread.currentThread().getName() + " [任务B] 居然拿到了: " + val);
                System.err.println("       原因：线程被复用了，上个任务的 ThreadLocal 残留了下来！");
            } else {
                System.out.println("[任务B] 干净的环境");
            }
            CONTEXT.remove();
        });

        pool.shutdown();
    }
}
