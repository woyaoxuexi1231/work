package work.N1javabasic.v1.concurrent.forkjoinpool;

import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ForkJoinPool;

public class IncrementAction extends RecursiveAction {
    private final int[] array;
    private final int start, end;
    private static final int THRESHOLD = 10_000;

    public IncrementAction(int[] array, int start, int end) {
        this.array = array;
        this.start = start;
        this.end = end;
    }

    @Override
    protected void compute() {
        if (end - start <= THRESHOLD) {
            // 足够小，直接执行
            for (int i = start; i < end; i++) {
                array[i] += 1;
            }
        } else {
            // 任务拆分
            int mid = (start + end) >>> 1;
            IncrementAction left = new IncrementAction(array, start, mid);
            IncrementAction right = new IncrementAction(array, mid, end);
            invokeAll(left, right); // 同时执行两个子任务并等待完成
        }
    }

    // 测试
    public static void main(String[] args) {
        int[] data = new int[100_000];
        ForkJoinPool pool = new ForkJoinPool(); // 默认并行度=CPU核心数
        pool.invoke(new IncrementAction(data, 0, data.length));
        // 验证：每个元素都应变为 1
        System.out.println(data[0] + ", " + data[99_999]); // 1, 1
        pool.shutdown();
    }
}