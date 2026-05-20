package work.N1javabasic.v1;

import io.reactivex.rxjava3.core.Completable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static java.lang.Thread.sleep;

/**
 * @author hulei
 * @since 2026/5/20 15:18
 */

public class CompletableFutureTest {
    public static void main(String[] args) {
        // 无返回值的异步任务
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            System.out.println("run in " + Thread.currentThread().getName());
        });

        // 有返回值的异步任务
        CompletableFuture<String> supplyFuture = CompletableFuture.supplyAsync(() -> {
            return "result";
        });


        // 默认使用 ForkJoinPool.commonPool()（守护线程），一般要传入自定义线程池：
        Executor pool = Executors.newFixedThreadPool(10);
        CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> "data", pool);

        /*
        2.1 一对一依赖
            thenApply(fn) — 接收上一步结果，返回新值（有返回）
            thenAccept(consumer) — 接收结果，不返回（消费）
            thenRun(runnable) — 不接收结果，也不返回（只执行动作）
        每个都有对应的 thenXxxAsync 可指定线程池，否则默认用上一步的线程。
         */
        CompletableFuture.supplyAsync(() -> "Hello")
                .thenApply(s -> s + " World")
                .thenApply(String::toUpperCase)
                .thenAccept(System.out::println);   // HELLO WORLD

        /*
        2.2 组合两个任务
            thenCombine(other, bifn) — 等待两者都完成，再合并结果
            thenAcceptBoth(other, biconsumer) — 消费两者结果
            runAfterBoth(other, runnable) — 两者都完成再执行动作
         */
        CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(() -> "Hello");
        CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> "World");
        cf1.thenCombine(cf2, (r1, r2) -> r1 + " " + r2);
        cf1.thenAcceptBoth(cf2, (r1, r2) -> System.out.println(r1 + " " + r2));
        cf1.runAfterBoth(cf2, () -> System.out.println("Done"));

        /*
        2.3 只要其中一个完成
            applyToEither(other, fn) — 先完成的那个结果用于 fn
            acceptEither(other, consumer)
            runAfterEither(other, runnable)
         */
        CompletableFuture<String> fast = CompletableFuture.supplyAsync(() -> {
            try {
                sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return "fast";
        });
        CompletableFuture<String> slow = CompletableFuture.supplyAsync(() -> {
            try {
                sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return "slow";
        });
        fast.applyToEither(slow, s -> s.toUpperCase())
                .thenAccept(System.out::println); // FAST

    }
}
