package work.N1javabasic.deepseek.day2;
import java.util.*;

public class HashMapResizeSimulation {

    static class Node {
        final int hash;
        final int key;
        int value;
        Node next;

        Node(int key, int value) {
            this.hash = key; // 简化：key.hashCode() = key
            this.key = key;
            this.value = value;
        }
    }

    public static void main(String[] args) {
        // 1. 手动构建扩容前的旧表（容量=4，阈值=3）
        Node[] oldTable = new Node[4];
        // 插入 key=0 (hash=0 → 索引=0)
        oldTable[0] = new Node(0, 0);
        // 插入 key=4 (hash=4 → 索引=0，与 key=0 冲突)
        oldTable[0].next = new Node(4, 4);
        // 插入 key=1 (hash=1 → 索引=1)
        oldTable[1] = new Node(1, 1);
        // 插入 key=5 (hash=5 → 索引=1，触发扩容！)
        oldTable[1].next = new Node(5, 5); // 此时 size=4 > 阈值 3

        // 2. 手动执行扩容逻辑（完全遵循 JDK 17 resize()）
        int newCapacity = oldTable.length << 1; // 新容量 = 8
        Node[] newTable = new Node[newCapacity];

        System.out.println("===== 扩容前状态 (手动构建，含4个元素) =====");
        printTable(oldTable, oldTable.length);

        System.out.println("\n===== 扩容迁移过程 =====");
        for (int oldIndex = 0; oldIndex < oldTable.length; oldIndex++) {
            Node node = oldTable[oldIndex];
            while (node != null) {
                // JDK 17 扩容迁移核心逻辑：
                int newIndex = (node.hash & (newCapacity - 1)); // 新索引 = hash & (新容量-1)

                // 验证高位标志（e.hash & oldCap）
                int highBit = node.hash & oldTable.length; // oldTable.length = 旧容量
                String branch = (highBit == 0) ? "低位链表" : "高位链表 (索引+旧容量)";

                System.out.printf(
                        "key=%-2d | 旧索引=%d | hash=%d | highBit=%d | 新索引=%d → %s%n",
                        node.key, oldIndex, node.hash, highBit, newIndex, branch
                );

                // 将节点添加到新表（简化：头插法）
                Node next = node.next;
                node.next = newTable[newIndex];
                newTable[newIndex] = node;
                node = next;
            }
        }

        System.out.println("\n===== 扩容后状态 =====");
        printTable(newTable, newCapacity);
    }

    private static void printTable(Node[] table, int capacity) {
        for (int i = 0; i < capacity; i++) {
            System.out.printf("索引 %d: ", i);
            Node node = table[i];
            while (node != null) {
                System.out.printf("key=%d → ", node.key);
                node = node.next;
            }
            System.out.println("null");
        }
    }
}