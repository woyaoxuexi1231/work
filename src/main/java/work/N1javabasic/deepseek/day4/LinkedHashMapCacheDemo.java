package work.N1javabasic.deepseek.day4;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 使用 LinkedHashMap 实现简单的 LRU 配置缓存
 * 
 * 依赖：无（JDK 内置）
 * 适用场景：单线程、低并发、仅需限制条目数（如系统字典）
 * 缺点：无过期机制、线程不安全、无监控
 */
public class LinkedHashMapCacheDemo {

    // 最大缓存条目数
    private static final int MAX_ENTRIES = 5;

    // 匿名内部类重写 removeEldestEntry 实现 LRU
    private static final Map<String, String> configCache = new LinkedHashMap<String, String>(
            16, 0.75f, true) {  // accessOrder=true 按访问顺序排序
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_ENTRIES; // 超出容量时自动删除最久未访问条目
        }
    };

    public static void main(String[] args) {
        // 模拟从数据库加载配置
        String[] keys = {"db.url", "db.user", "db.password", "app.timeout", "app.lang", "app.version", "app.name"};
        for (String key : keys) {
            String value = loadFromDB(key);  // 模拟加载
            configCache.put(key, value);
            System.out.printf("加载配置 [%s] -> [%s]，当前缓存大小: %d%n", key, value, configCache.size());
        }

        System.out.println("\n访问缓存 'db.url': " + configCache.get("db.url"));
        // 再次放入新配置，触发淘汰
        configCache.put("extra.config", "123");
        System.out.println("放入新配置后，缓存内容: " + configCache);
    }

    private static String loadFromDB(String key) {
        // 模拟耗时加载
        sleep(50);
        return "val_of_" + key;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}