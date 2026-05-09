package work.N1javabasic.old.day13;

import lombok.SneakyThrows;

import java.util.concurrent.CompletableFuture;

/**
 * @author hulei
 * @since 2026/5/4 23:55
 */

public class Test {

    public static ThreadLocal<String> threadLocal = new ThreadLocal<>();

    @SneakyThrows
    public static void main(String[] args) {
        Thread thread = new Thread(() -> {
            threadLocal.set("你好");

            System.out.println(threadLocal.get());

            // 避免内存泄露
            threadLocal.remove();
        });

        thread.start();
        thread.join();

        // CompletableFuture

        CompletableFuture<Integer> future1 = CompletableFuture.supplyAsync(() -> 10);
        CompletableFuture<Integer> future2 = CompletableFuture.supplyAsync(() -> 20);
        CompletableFuture<Integer> combined = future1.thenCombine(future2, (a, b) -> a + b);

        CompletableFuture<Integer> future3 = CompletableFuture.supplyAsync(() -> 10);
        CompletableFuture<Integer> future4 = CompletableFuture.supplyAsync(() -> 20);
        CompletableFuture.allOf(future3, future4);
    }
}
