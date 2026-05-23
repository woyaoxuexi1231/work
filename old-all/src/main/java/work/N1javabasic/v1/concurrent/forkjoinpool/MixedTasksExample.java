package work.N1javabasic.v1.concurrent.forkjoinpool;

import java.util.concurrent.*;

public class MixedTasksExample {
    public static void main(String[] args) throws Exception {
        ForkJoinPool pool = new ForkJoinPool(4); // 指定并行度

        // 1. 执行 Runnable（无返回）
        pool.execute(() -> System.out.println("执行 Runnable"));

        // 2. 提交 Callable，返回 Future
        Future<String> future = pool.submit(() -> {
            Thread.sleep(100);
            return "Callable 结果";
        });
        System.out.println(future.get());

        // 3. 将 RecursiveTask 与 Callable 结合
        ForkJoinTask<Integer> task = pool.submit(() -> {
            // 模拟复杂计算
            int sum = 0;
            for (int i = 0; i < 1000; i++) sum += i;
            return sum;
        });
        System.out.println("结果: " + task.get());

        // 4. 带异常处理的任务
        ForkJoinTask<Object> errorTask = pool.submit(() -> {
            throw new RuntimeException("任务内异常");
        });
        try {
            errorTask.get();
        } catch (ExecutionException e) {
            System.out.println("捕获异常: " + e.getCause().getMessage());
        }

        pool.shutdown();
    }
}