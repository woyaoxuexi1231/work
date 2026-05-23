package work.N1javabasic.v1.cas.aba;

import java.util.concurrent.atomic.AtomicMarkableReference;

public class MarkableRefDemo {
    static class Message {
        String content;
        Message(String c) { content = c; }
    }

    static AtomicMarkableReference<Message> ref = new AtomicMarkableReference<>(null, false);

    // 模拟处理消息，标记为已处理
    static void process(Message msg) {
        boolean[] markHolder = new boolean[1];
        Message current = ref.get(markHolder);
        // 仅当消息未被标记时处理
        if (current == msg && !markHolder[0]) {
            // 尝试将标记从 false 改为 true
            if (ref.compareAndSet(msg, msg, false, true)) {
                System.out.println("消息已处理: " + msg.content);
            } else {
                System.out.println("处理失败，已被其他线程处理");
            }
        } else {
            System.out.println("消息已被处理或不存在");
        }
    }

    public static void main(String[] args) {
        Message msg = new Message("订单支付");

        // 初始设置消息和标记 false（未处理）
        ref.set(msg, false);

        Thread t1 = new Thread(() -> process(msg));
        Thread t2 = new Thread(() -> process(msg));

        t1.start();
        t2.start();
        // 两个线程同时尝试处理同一消息，只有第一个能成功将标记从 false 改为 true
    }
}