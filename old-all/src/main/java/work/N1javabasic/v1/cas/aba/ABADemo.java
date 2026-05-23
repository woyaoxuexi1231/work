package work.N1javabasic.v1.cas.aba;

import java.util.concurrent.atomic.AtomicReference;

public class ABADemo {
    static class Node {
        int value;
        Node next;
        Node(int v, Node n) { value = v; next = n; }
    }

    static AtomicReference<Node> top = new AtomicReference<>();

    // 无锁栈弹出
    static Node pop() {
        Node oldTop;
        Node newTop;
        do {
            oldTop = top.get();
            if (oldTop == null) return null;
            newTop = oldTop.next;
        } while (!top.compareAndSet(oldTop, newTop));
        return oldTop;
    }

    public static void main(String[] args) throws InterruptedException {
        // 初始化栈：A -> B -> C
        Node C = new Node(3, null);
        Node B = new Node(2, C);
        Node A = new Node(1, B);
        top.set(A);

        // 线程1：准备弹出 A，但还没执行 CAS
        Thread t1 = new Thread(() -> {
            Node oldTop = top.get();  // 读到 A
            // 此时让线程2操作
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
            // 线程1继续：期望 top 还是 A，设为 A.next (B)
            if (top.compareAndSet(oldTop, oldTop.next)) {
                System.out.println("线程1弹出: " + oldTop.value);
            } else {
                System.out.println("线程1 CAS 失败");
            }
        });

        // 线程2：做两次弹出，再压回 A（中间出现 ABA）
        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            Node popped1 = pop(); // 弹出 A
            Node popped2 = pop(); // 弹出 B
            System.out.println("线程2弹出: " + popped1.value + ", " + popped2.value);
            // 再把 A 压回栈顶
            top.set(popped1); // 栈顶又变成了 A
        });

        t1.start();
        Thread.sleep(50); // 保证线程1先读到 oldTop
        t2.start();
        t1.join();
        t2.join();

        System.out.println("最终栈顶: " + (top.get() == null ? "空" : top.get().value));
        // 这里可能会打印出 A 被弹出两次，但 B 其实丢失了，这就是 ABA 问题
    }
}