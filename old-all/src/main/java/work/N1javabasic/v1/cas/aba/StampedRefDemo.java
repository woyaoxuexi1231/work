package work.N1javabasic.v1.cas.aba;

import java.util.concurrent.atomic.AtomicStampedReference;

public class StampedRefDemo {
    static class Node {
        int value;
        Node next;
        Node(int v, Node n) { value = v; next = n; }
    }

    static AtomicStampedReference<Node> top = new AtomicStampedReference<>(null, 0);

    static Node pop() {
        int[] stampHolder = new int[1];
        Node oldTop;
        Node newTop;
        do {
            oldTop = top.get(stampHolder);
            if (oldTop == null) return null;
            newTop = oldTop.next;
            // CAS 时同时检查引用和版本，并将版本+1
        } while (!top.compareAndSet(oldTop, newTop, stampHolder[0], stampHolder[0] + 1));
        return oldTop;
    }

    static void push(Node node) {
        int[] stampHolder = new int[1];
        Node oldTop;
        do {
            oldTop = top.get(stampHolder);
            node.next = oldTop;
        } while (!top.compareAndSet(oldTop, node, stampHolder[0], stampHolder[0] + 1));
    }

    public static void main(String[] args) throws InterruptedException {
        Node C = new Node(3, null);
        Node B = new Node(2, C);
        Node A = new Node(1, B);
        top.set(A, 0); // 初始版本0

        Thread t1 = new Thread(() -> {
            int[] stamp = new int[1];
            Node oldTop = top.get(stamp);
            // 模拟停顿
            try { Thread.sleep(200); } catch (InterruptedException e) {}
            // 尝试用旧版本 CAS，期望版本还是 stamp[0]
            if (top.compareAndSet(oldTop, oldTop.next, stamp[0], stamp[0] + 1)) {
                System.out.println("线程1弹出: " + oldTop.value);
            } else {
                System.out.println("线程1 CAS 失败（版本已变）");
            }
        });

        Thread t2 = new Thread(() -> {
            Node p1 = pop(); // 弹出 A，版本变成1
            Node p2 = pop(); // 弹出 B，版本变成2
            System.out.println("线程2弹出: " + p1.value + ", " + p2.value);
            push(p1);        // 压回 A，版本变成3
        });

        t1.start();
        Thread.sleep(50);
        t2.start();
        t1.join();
        t2.join();

        // 现在线程1会看到版本已从0变成3，CAS 失败，避免了 ABA
        int[] lastStamp = new int[1];
        Node lastTop = top.get(lastStamp);
        System.out.println("最终栈顶: " + (lastTop == null ? "空" : lastTop.value) + " 版本:" + lastStamp[0]);
    }
}