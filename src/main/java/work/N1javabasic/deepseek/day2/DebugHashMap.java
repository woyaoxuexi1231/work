package work.N1javabasic.deepseek.day2;

public class DebugHashMap<K, V> {
    // 关键常量（与JDK 1.8 HashMap保持一致）
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // 16
    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    static final int TREEIFY_THRESHOLD = 8;
    static final int MIN_TREEIFY_CAPACITY = 64;

    // 模拟Node节点
    static class Node<K,V> {
        final int hash;
        final K key;
        V value;
        Node<K,V> next;

        Node(int hash, K key, V value, Node<K,V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    // 存储数据的桶数组
    transient Node<K,V>[] table;
    transient int size;
    int threshold;
    final float loadFactor;

    public DebugHashMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
        this.threshold = DEFAULT_INITIAL_CAPACITY;
        this.table = new Node[DEFAULT_INITIAL_CAPACITY];
    }

    // 计算hash值（与JDK 1.8实现一致）
    static final int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }

    public V put(K key, V value) {
        return putVal(hash(key), key, value, false, true);
    }

    final V putVal(int hash, K key, V value, boolean onlyIfAbsent, boolean evict) {
        Node<K,V>[] tab = table;
        Node<K,V> p;
        int n = tab.length, i;

        // 1. 检查桶是否为空
        if ((p = tab[i = (n - 1) & hash]) == null) {
            System.out.println("[步骤] 桶 " + i + " 为空，直接创建新节点");
            tab[i] = new Node<>(hash, key, value, null);
        } else {
            Node<K,V> e;
            K k;

            // 2. 检查是否命中首个节点
            if (p.hash == hash &&
                    ((k = p.key) == key || (key != null && key.equals(k)))) {
                System.out.println("[步骤] 命中首节点 - 桶 " + i + " 的首个节点键匹配成功");
                e = p;
            }
            // 3. 检查是否是树节点（简化处理，实际JDK中会调用树的插入方法）
            else if (p instanceof TreeNode) {
                System.out.println("[步骤] 桶是树结构 - 桶 " + i + " 使用树插入逻辑");
                // 实际JDK中会调用putTreeVal
                e = p;
            }
            else {
                // 4. 遍历链表
                System.out.println("[步骤] 遍历链表 - 开始遍历桶 " + i + " 的链表");
                Node<K,V> last = p;
                int binCount = 0;

                // 遍历链表查找匹配节点
                while ((e = last.next) != null) {
                    binCount++;
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k)))) {
                        System.out.println("[步骤] 链表匹配 - 在链表位置 " + binCount + " 找到匹配的键");
                        break;
                    }
                    last = e;
                }

                // 5. 未找到匹配节点，需要添加新节点
                if (e == null) {
                    System.out.println("[步骤] 追加到尾部 - 将新节点追加到链表尾部（长度=" + (binCount+1) + "）");
                    last.next = new Node<>(hash, key, value, null);

                    // 6. 检查是否需要树化
                    if (binCount >= TREEIFY_THRESHOLD - 1) {
                        System.out.println("[步骤] 树化检查 - 链表长度达到 " + (binCount+1) + "（>= " + TREEIFY_THRESHOLD + "）");

                        if (n >= MIN_TREEIFY_CAPACITY) {
                            System.out.println("[步骤] 确认树化 - 表容量 " + n + " >= " + MIN_TREEIFY_CAPACITY + "，转换为红黑树");
                            // 实际JDK中会调用treeifyBin
                        } else {
                            System.out.println("[步骤] 取消树化 - 表容量 " + n + " < " + MIN_TREEIFY_CAPACITY + "，将进行扩容");
                        }
                    }
                }
            }

            // 处理已存在key的情况
            if (e != null) {
                V oldValue = e.value;
                if (!onlyIfAbsent) {
                    e.value = value;
                }
                return oldValue;
            }
        }

        size++;
        // 实际扩容检查（简化）
        if (size > threshold) {
            System.out.println("[步骤] 扩容 - 当前大小 " + size + " 超过阈值 " + threshold);
        }

        return null;
    }

    // 简化版TreeNode用于演示
    static class TreeNode<K,V> extends Node<K,V> {
        TreeNode(int hash, K key, V value, Node<K,V> next) {
            super(hash, key, value, next);
        }
    }

    public static void main(String[] args) {
        DebugHashMap<String, String> map = new DebugHashMap<>();
        for (int i = 0; i < 100; i++) {
            map.put("key" + i, "value" + i);
        }
    }
}
