package work.N1javabasic.v1.day1;

import java.util.ArrayList;
import java.util.List;

public class AutoboxingPerformanceTest {

    public static void main(String[] args) {
        // Part 1：性能对比实验（基本类型 vs 包装类型）
        testSumPerformance();
        
        // Part 2：缓存范围验证
        testIntegerCache();
        
        // Part 3：手动调整缓存上限验证（需在JVM参数中设置 -XX:AutoBoxCacheMax=200）
        verifyCustomCacheRange();
    }

    // █████████ Part 1：性能对比实验 █████████
    private static void testSumPerformance() {
        final int SIZE = 1_000_000;
        List<Integer> integerList = new ArrayList<>(SIZE);
        int[] primitiveArray = new int[SIZE];

        // 初始化数据（避免测试阶段包含初始化开销）
        for (int i = 0; i < SIZE; i++) {
            integerList.add(i);
            primitiveArray[i] = i;
        }

        // 测试 Integer 求和（自动装箱/拆箱）
        long start = System.nanoTime();
        long boxedSum = 0;
        for (Integer num : integerList) {
            boxedSum += num; // 隐式拆箱
        }
        long boxedTime = System.nanoTime() - start;

        // 测试 int 求和
        start = System.nanoTime();
        long primitiveSum = 0;
        for (int num : primitiveArray) {
            primitiveSum += num;
        }
        long primitiveTime = System.nanoTime() - start;

        System.out.printf("""
                ===== 性能对比（%d 次求和） =====
                Integer 求和耗时: %,d ns（含自动拆箱开销）
                int 求和耗时:    %,d ns
                性能差距:        %.1f 倍
                """, SIZE, boxedTime, primitiveTime, (double) boxedTime / primitiveTime);
    }

    // █████████ Part 2：缓存范围验证 █████████
    private static void testIntegerCache() {
        System.out.println("\n===== Integer 缓存范围验证 =====");
        System.out.println("默认缓存范围: [-128, 127]");

        // 测试缓存范围内
        Integer a1 = 127;
        Integer b1 = 127;
        System.out.printf("127 == 127: %s (缓存内，引用相等)%n", a1 == b1); // true

        // 测试缓存范围外
        Integer a2 = 128;
        Integer b2 = 128;
        System.out.printf("128 == 128: %s (缓存外，新建对象)%n", a2 == b2); // false
        System.out.printf("128 equals 128: %s (值相等)%n", a2.equals(b2));   // true

        // 验证valueOf()行为
        Integer c1 = Integer.valueOf(127);
        Integer c2 = Integer.valueOf(128);
        System.out.printf("valueOf(127) == 127: %s%n", c1 == 127); // true (缓存内)
        System.out.printf("valueOf(128) == 128: %s%n", c2 == 128); // false (缓存外)
    }

    // █████████ Part 3：自定义缓存上限验证 █████████
    private static void verifyCustomCacheRange() {
        System.out.println("\n===== 自定义缓存上限验证 =====");
        System.out.println("（请在JVM启动参数添加 -XX:AutoBoxCacheMax=200 后重试）");
        
        // 验证199（应命中缓存）
        Integer x1 = 199;
        Integer x2 = 199;
        System.out.printf("199 == 199: %s (若参数生效应为true)%n", x1 == x2);
        
        // 验证200（应超出缓存）
        Integer y1 = 201;
        Integer y2 = 201;
        System.out.printf("200 == 200: %s (应为false)%n", y1 == y2);
    }
}