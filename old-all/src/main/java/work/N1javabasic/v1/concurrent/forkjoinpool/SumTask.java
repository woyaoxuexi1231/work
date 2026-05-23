package work.N1javabasic.v1.concurrent.forkjoinpool;

import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ForkJoinPool;

public class SumTask extends RecursiveTask<Long> {
    private final int[] array;
    private final int start, end;
    private static final int THRESHOLD = 10_000;

    public SumTask(int[] array, int start, int end) {
        this.array = array;
        this.start = start;
        this.end = end;
    }

    @Override
    protected Long compute() {
        if (end - start <= THRESHOLD) {
            long sum = 0;
            for (int i = start; i < end; i++) {
                sum += array[i];
            }
            return sum;
        } else {
            int mid = (start + end) >>> 1;
            SumTask left = new SumTask(array, start, mid);
            SumTask right = new SumTask(array, mid, end);
            left.fork();            // 异步执行左任务
            long rightResult = right.compute(); // 当前线程执行右任务
            long leftResult = left.join();      // 等待左任务完成
            return leftResult + rightResult;
        }
    }

    public static void main(String[] args) {
        int[] data = new int[100_000];
        for (int i = 0; i < data.length; i++) data[i] = i + 1;
        ForkJoinPool pool = ForkJoinPool.commonPool(); // 使用公共池
        Long result = pool.invoke(new SumTask(data, 0, data.length));
        System.out.println("Sum = " + result); // 5000050000
    }
}