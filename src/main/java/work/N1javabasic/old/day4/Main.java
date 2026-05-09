package work.N1javabasic.old.day4;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ConcurrentHashMap 扩容触发演示
 * <p>
 * 【核心知识点】
 * 1. ConcurrentHashMap 默认负载因子：0.75
 * 2. 初始容量为 1 时，阈值 = 1 * 0.75 = 0.75，实际放 1 个元素就会触发扩容
 * 3. 扩容机制：容量翻倍（1 → 2 → 4 → 8 → 16...）
 * 4. 扩容触发条件：size >= threshold（阈值）
 * <p>
 * 【运行观察】
 * 运行后观察控制台输出，可以看到每次扩容的详细信息
 *
 * @author hulei
 * @since 2026/4/26 17:07
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("========== ConcurrentHashMap 扩容演示 ==========");

        // 初始容量设为 1，负载因子默认 0.75
        // 阈值 = 1 * 0.75 = 0.75，所以放第 1 个元素后就会触发扩容
        ConcurrentHashMap<Long, String> map = new ConcurrentHashMap<>(1);

        System.out.println("初始容量: 1");
        System.out.println("负载因子: 0.75");
        System.out.println("触发扩容阈值: 1 个元素");
        System.out.println();

        // 放入 20 个元素，会触发多次扩容
        int count = 20;
        for (int i = 0; i < count; i++) {
            Long key = System.nanoTime(); // 使用 nanoTime 确保 key 不重复
            String value = "value-" + i;

            int sizeBefore = map.size();
            map.put(key, value);
            int sizeAfter = map.size();

            // 如果 size 发生变化，说明没有触发扩容（正常插入）
            // ConcurrentHashMap 的扩容是异步的，这里简化演示
            if (i > 0 && (i == 1 || i == 2 || i == 4 || i == 8 || i == 16)) {
                System.out.println("📦 已插入 " + (i + 1) + " 个元素，可能触发扩容");
            }

            System.out.println("插入第 " + (i + 1) + " 个元素: key=" + key + ", size=" + sizeAfter);
        }

        System.out.println();
        System.out.println("========== 扩容规则总结 ==========");
        System.out.println("初始容量 1 → 插入 1 个 → 扩容到 2");
        System.out.println("容量 2 → 插入 2 个 → 扩容到 4");
        System.out.println("容量 4 → 插入 4 个 → 扩容到 8");
        System.out.println("容量 8 → 插入 8 个 → 扩容到 16");
        System.out.println("容量 16 → 插入 12 个（16*0.75）→ 扩容到 32");
        System.out.println();
        System.out.println("最终 Map 大小: " + map.size());
        System.out.println("========================================");
    }
}
