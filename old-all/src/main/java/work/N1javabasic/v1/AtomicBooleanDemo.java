package work.N1javabasic.v1;

import java.util.concurrent.atomic.AtomicBoolean;

public class AtomicBooleanDemo {

    // 一次性初始化标记
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    // 服务启停开关
    private static final AtomicBoolean running = new AtomicBoolean(true);

    public static void main(String[] args) throws InterruptedException {
        // 1. 只执行一次的逻辑（如初始化配置）
        for (int i = 0; i < 5; i++) {
            initOnce();
        }

        // 2. 线程启停控制
        Thread worker = new Thread(() -> {
            while (running.get()) {
                System.out.println("工作线程运行中...");
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println("工作线程已安全退出");
        });
        worker.start();

        Thread.sleep(1000);
        running.set(false);   // 通知线程停止
        worker.join();

        // 3. CAS 实现安全的状态切换
        boolean switched = running.compareAndSet(false, true);
        System.out.println("重新启动成功? " + switched + "，当前状态: " + running.get());
    }

    private static void initOnce() {
        // compareAndSet(false, true) 只有在 false -> true 时才成功，保证只执行一次
        if (initialized.compareAndSet(false, true)) {
            System.out.println("执行一次性初始化...");
        } else {
            System.out.println("已经初始化过，跳过");
        }
    }
}