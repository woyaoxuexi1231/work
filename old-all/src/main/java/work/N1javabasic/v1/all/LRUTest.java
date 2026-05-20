package work.N1javabasic.v1.all;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author hulei
 * @since 2026/5/20 18:26
 */

public class LRUTest {
    public static void main(String[] args) {
        LRUCache cache = new LRUCache(4);
        cache.put("1", "1");
        cache.put("2", "2");
        cache.put("3", "3");
        cache.put("4", "4");
        cache.put("5", "5");
        cache.put("6", "6");
        cache.put("7", "7");
        System.out.println(cache);
    }
}

class LRUCache extends LinkedHashMap<String, Object> {

    private final int capacity;

    public LRUCache(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor, true);
        this.capacity = initialCapacity;
    }

    public LRUCache(int initialCapacity) {
        super(initialCapacity, 0.75f, true);
        this.capacity = initialCapacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
        // 新增元素后会调用 afterNodeInsertion -> removeEldestEntry
        // 这里是LRU，least recently used，删除最老的元素
        return size() > capacity;
    }

}
