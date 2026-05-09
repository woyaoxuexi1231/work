package work.N1javabasic.old.day14;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LRUCache<K, V> {

    private final int capacity;
    private final Map<K, Entry<K, V>> map;
    private final Entry<K, V> head; // 虚拟头（最久未使用）
    private final Entry<K, V> tail; // 虚拟尾（最近使用）
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.head = new Entry<>(null, null, null);
        this.tail = new Entry<>(null, null, null);
        head.next = tail;
        tail.prev = head;
    }

    public V get(K key) {

        lock.readLock().lock();

        Entry<K, V> entry = map.get(key);
        if (entry == null) {
            lock.readLock().unlock();
            return null;
        }
        if (isExpired(entry)) {
            // 需要升级写锁删除
            lock.readLock().unlock();
            lock.writeLock().lock();
            try {
                removeNode(entry);
                map.remove(key);
                return null;
            } finally {
                lock.writeLock().unlock();
            }
        }

        // 需要移动节点，升级写锁
        lock.readLock().unlock();
        lock.writeLock().lock();
        try {
            moveToTail(entry);
            return entry.value;
        } finally {
            lock.writeLock().unlock();
        }

    }

    public void put(K key, V value, LocalDateTime ttl) {
        lock.writeLock().lock();
        try {
            Entry<K, V> entry = map.get(key);
            if (entry != null) {
                // 更新已存在的节点
                entry.value = value;
                entry.expireTime = ttl;
                moveToTail(entry);
            } else {
                // 新节点
                if (map.size() >= capacity) {
                    // 淘汰头节点
                    Entry<K, V> lru = head.next;
                    removeNode(lru);
                    map.remove(lru.key);
                }

                Entry<K, V> newEntry = new Entry<>(key, value, ttl);
                map.put(key, newEntry);
                addToTail(newEntry);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean isExpired(Entry<K, V> entry) {
        return entry.expireTime != null && entry.expireTime.isBefore(LocalDateTime.now());
    }

    private void addToTail(Entry<K, V> entry) {
        Entry<K, V> prev = tail.prev;
        prev.next = entry;
        entry.prev = prev;
        entry.next = tail;
        tail.prev = entry;
    }

    private void removeNode(Entry<K, V> entry) {
        entry.prev.next = entry.next;
        entry.next.prev = entry.prev;
    }

    private void moveToTail(Entry<K, V> entry) {
        if (entry == tail.prev) return;

        removeNode(entry);
        addToTail(entry);
    }

    static class Entry<K, V> {
        K key;
        V value;
        LocalDateTime expireTime;
        Entry<K, V> prev, next;

        Entry(K key, V value, LocalDateTime expireTime) {
            this.key = key;
            this.value = value;
            this.expireTime = expireTime;
        }
    }

    @Override
    public String toString() {
        // 按照顺序打印
        StringBuilder sb = new StringBuilder();
        Entry<K, V> node = head;
        while (node != null) {
            sb.append("[").append(node.key).append(":").append(node.value).append("]").append(" -> ");
            node = node.next;
        }
        return sb.toString();
    }
}