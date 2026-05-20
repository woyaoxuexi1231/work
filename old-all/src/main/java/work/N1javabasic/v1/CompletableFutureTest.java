package work.N1javabasic.v1;

import java.util.concurrent.CompletableFuture;

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
    }
}
