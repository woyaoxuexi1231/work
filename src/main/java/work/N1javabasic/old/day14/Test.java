package work.N1javabasic.old.day14;

import java.time.LocalDateTime;

/**
 * @author hulei
 * @since 2026/5/5 1:13
 */

public class Test {

    public static void main(String[] args) throws Exception {
        LRUCache<String, String> cache = new LRUCache<>(3);

        // 测试容量淘汰
        cache.put("1", "v1", LocalDateTime.now().plusSeconds(10));
        cache.put("2", "v2", LocalDateTime.now().plusSeconds(10));
        cache.put("3", "v3", LocalDateTime.now().plusSeconds(10));
        cache.put("4", "v4", LocalDateTime.now().plusSeconds(10)); // 淘汰 "1"
        System.out.println(cache.get("1") == null ? "✅ 1 已淘汰" : "❌ 应该淘汰 1");
        System.out.println(cache);

        // 测试 LRU 淘汰
        cache.get("2"); // 访问 "2"，变为最近使用
        cache.put("5", "v5", LocalDateTime.now().plusSeconds(10)); // 淘汰 "3"
        System.out.println(cache.get("3") == null ? "✅ 3 已淘汰" : "❌ 应该淘汰 3");
        System.out.println(cache.get("2").equals("v2") ? "✅ 2 正在缓存" : "❌ 2 已淘汰");
        System.out.println(cache);

        // 测试过期
        cache.put("6", "v6", LocalDateTime.now().minusSeconds(1)); // 已过期
        Thread.sleep(1500);
        System.out.println(cache.get("6") == null ? "✅6 已过期" : "❌ 6 未过期");
        System.out.println(cache);
    }
}
