package work.N1javabasic.v1.day2;

import java.util.Objects;

class SimpleHashMap<K, V> {
    // 静态内部类：链表节点
    private static class Node<K, V> {
        final K key;
        V value;
        Node<K, V> next;

        Node(K key, V value, Node<K, V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    private Node<K, V>[] table;
    private static final int DEFAULT_CAPACITY = 16; // 2的幂次

    @SuppressWarnings("unchecked")
    public SimpleHashMap() {
        table = (Node<K, V>[]) new Node[DEFAULT_CAPACITY];
    }

    public V put(K key, V value) {
        // 1. 计算原始 hashCode
        int hashCode = key.hashCode();
        
        // 2. JDK8 扰动函数：高16位异或低16位
        int disturbedHash = hashCode ^ (hashCode >>> 16);
        
        // 3. 计算数组下标：hash & (n-1)
        int index = disturbedHash & (table.length - 1);
        
        // 4. 计算当前链表长度
        int chainLength = 0;
        Node<K, V> cur = table[index];
        Node<K, V> prev = null;
        boolean replaced = false;

        // 遍历链表：检查重复 key 并计数
        while (cur != null) {
            chainLength++;
            if (Objects.equals(cur.key, key)) {
                cur.value = value; // 覆盖旧值
                replaced = true;
                break;
            }
            prev = cur;
            cur = cur.next;
        }

        // 5. 无重复 key 时插入新节点（头插法）
        if (!replaced) {
            Node<K, V> newNode = new Node<>(key, value, table[index]);
            table[index] = newNode;
        }

        // 6. 打印关键信息
        System.out.printf("Key: %-10s | hashCode: %-10d | DisturbedHash: %-10d | Index: %-2d | ChainLen: %d%n",
                key, hashCode, disturbedHash, index, chainLength);

        return replaced ? cur.value : null;
    }

    // 为演示冲突故意设计的类（所有实例 hashCode 恒为 0）
    public static class BadKey {
        private final String name;

        public BadKey(String name) {
            this.name = name;
        }

        @Override
        public int hashCode() {
            return 0; // 强制所有实例产生哈希冲突
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BadKey badKey = (BadKey) o;
            return Objects.equals(name, badKey.name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static void main(String[] args) {
        SimpleHashMap<BadKey, String> map = new SimpleHashMap<>();
        
        // 放入 5 个强制冲突的 key
        for (int i = 1; i <= 5; i++) {
            map.put(new BadKey("key" + i), "value" + i);
        }
    }
}