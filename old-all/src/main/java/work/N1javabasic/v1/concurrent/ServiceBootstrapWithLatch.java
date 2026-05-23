package work.N1javabasic.v1.concurrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 业务场景：微服务启动，需等待数据库、缓存、第三方服务就绪。
 */
public class ServiceBootstrapWithLatch {

    private static final int DEPENDENCY_COUNT = 3;
    private final CountDownLatch latch = new CountDownLatch(DEPENDENCY_COUNT);

    // 模拟初始化数据库连接
    public void initDatabase() {
        String threadName = Thread.currentThread().getName();
        System.out.println(threadName + " 正在初始化数据库连接...");
        try {
            TimeUnit.SECONDS.sleep(20000); // 模拟耗时操作
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println(threadName + " 数据库就绪！");
        latch.countDown();
    }

    // 模拟缓存预热
    public void preheatCache() {
        String threadName = Thread.currentThread().getName();
        System.out.println(threadName + " 正在预热缓存...");
        try {
            TimeUnit.SECONDS.sleep(30000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println(threadName + " 缓存就绪！");
        latch.countDown();
    }

    // 模拟第三方服务授权
    public void authorizeThirdParty() {
        String threadName = Thread.currentThread().getName();
        System.out.println(threadName + " 正在请求第三方授权...");
        try {
            TimeUnit.SECONDS.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println(threadName + " 第三方服务授权成功！");
        latch.countDown();
    }

    public void startServer() throws InterruptedException {
        System.out.println("主线程等待所有依赖就绪...");
        latch.await(); // 阻塞，直到计数归零
        System.out.println("所有依赖已就绪，开始监听 HTTP 请求 -> 0.0.0.0:8080");
    }

    public static void main(String[] args) throws InterruptedException {
        ServiceBootstrapWithLatch bootstrap = new ServiceBootstrapWithLatch();
        ExecutorService executor = Executors.newFixedThreadPool(3);

        // 并行初始化三个依赖
        executor.submit(bootstrap::initDatabase);
        executor.submit(bootstrap::preheatCache);
        executor.submit(bootstrap::authorizeThirdParty);

        bootstrap.startServer();

        executor.shutdown();
    }
}