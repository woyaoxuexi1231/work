package work.N1javabasic.deepseek.day2;

import java.util.HashSet;
import java.util.Set;

public class Jdk7HashMapDeadLoopSimulation {

    // 模拟 JDK 1.7 HashMap 的节点（Entry）
    static class Node {
        int key;
        Node next;

        Node(int key) {
            this.key = key;
        }
    }

    // 模拟 HashMap 的数组（哈希桶）
    static volatile Node[] table = new Node[1];

    public static void main(String[] args) throws InterruptedException {
        // 1. 初始化链表：A(3) -> B(7) -> null
        Node nodeA = new Node(3);
        Node nodeB = new Node(7);
        nodeA.next = nodeB;
        table[0] = nodeA;

        System.out.println("初始链表状态: 3 -> 7 -> null\n");

        // 2. 模拟线程1：执行到一半被挂起
        Thread thread1 = new Thread(() -> {
            // 模拟 transfer 方法的核心逻辑
            Node[] newTable = new Node[1]; // 假设扩容后的新数组
            Node e = table[0]; // e 指向 A(3)

            if (e != null) {
                // 【关键点】线程1在这里被挂起！它记录了 e=A, next=B
                Node next = e.next;

                try {
                    // 强行休眠，等待线程2完成扩容并改变内存中的链表结构
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}

                // 线程1恢复执行，继续头插法迁移
                // 此时内存中 B.next 已经被线程2改成了指向 A
                System.out.println("线程1恢复执行，开始将节点插入新数组...");
                do {
                    int i = 0; // 假设 hash 计算后都在下标 0
                    e.next = newTable[i]; // 头插法：当前节点指向新桶的头节点
                    newTable[i] = e;      // 更新新桶的头节点
                    e = next;             // 处理下一个节点

                    // 打印线程1当前的操作状态，方便观察
                    if (e != null) {
                        System.out.println("线程1: 准备处理节点 key=" + e.key + ", 它的 next 指向 key=" + (e.next != null ? e.next.key : "null"));
                        next = e.next;
                    }
                } while (e != null);

                System.out.println("线程1扩容完成（但实际上已经形成了死循环结构）\n");
            }
        }, "Thread-1");

        // 3. 模拟线程2：顺利完成整个扩容过程
        Thread thread2 = new Thread(() -> {
            // 模拟 transfer 方法
            Node[] newTable = new Node[1];
            Node e = table[0];

            if (e != null) {
                do {
                    Node next = e.next;
                    int i = 0;
                    e.next = newTable[i]; // 头插法
                    newTable[i] = e;
                    e = next;
                } while (e != null);
            }
            // 线程2扩容完成，由于头插法，链表被反转：B(7) -> A(3) -> null
            // 注意：这里我们直接把反转后的结果写回全局的 table，模拟多线程共享内存
            table[0] = newTable[0];
            System.out.println("线程2扩容完成，链表被反转为: 7 -> 3 -> null");
        }, "Thread-2");

        // 4. 启动线程，制造并发冲突
        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        // 5. 验证死循环：尝试遍历最终的链表
        System.out.println("准备遍历链表，如果卡死说明形成了环形链表：");
        Node current = table[0]; // 获取扩容后的头节点
        Set<Node> visited = new HashSet<>();

        int count = 0;
        while (current != null) {
            // 防止真的把电脑跑死，设置一个遍历上限
            if (count++ > 10) {
                System.out.println("⚠️ 遍历超过10次仍未结束，检测到死循环！程序强制退出。");
                return;
            }

            // 如果节点重复出现，说明有环
            // if (visited.contains(current)) {
            //     System.out.println("⚠️ 发现节点 key=" + current.key + " 重复出现，确认形成环形链表！");
            //     return;
            // }

            System.out.print(current.key + " -> ");
            visited.add(current);
            current = current.next;
        }
        System.out.println("null");
    }
}