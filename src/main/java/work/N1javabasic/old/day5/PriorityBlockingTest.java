package work.N1javabasic.old.day5;

import lombok.SneakyThrows;

import java.util.concurrent.PriorityBlockingQueue;

/**
 * @author hulei
 * @since 2026/4/26 17:56
 */

public class PriorityBlockingTest {
    @SneakyThrows
    public static void main(String[] args) {
        System.out.println("=== 优先级队列测试 ===");
        PriorityBlockingQueue<Integer> queue = new PriorityBlockingQueue<>();
        queue.add(5);
        queue.add(15);
        queue.add(25);
        queue.add(24);
        queue.add(7);
        queue.add(9);
        queue.add(0);

        System.out.println("队列中的元素：");
        System.out.println(queue);
        System.out.println("队列的头部元素：" + queue.peek());

        for (int i = 0; i < 5; i++) {
            // System.out.println(queue.take());
            System.out.println(queue.take());
        }
    }
}
