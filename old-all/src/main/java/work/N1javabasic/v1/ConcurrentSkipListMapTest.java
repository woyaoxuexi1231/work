package work.N1javabasic.v1;

import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * @author hulei
 * @since 2026/5/20 15:03
 */

public class ConcurrentSkipListMapTest {

    public static void main(String[] args) {
        // 自然排序（Key 必须实现 Comparable）
        ConcurrentSkipListMap<String, Integer> map = new ConcurrentSkipListMap<>();
        // 自定义比较器
        ConcurrentSkipListMap<String, Integer> map2 = new ConcurrentSkipListMap<>(Comparator.reverseOrder());


        ConcurrentSkipListMap<Integer, String> smap = new ConcurrentSkipListMap<>();
        smap.put(10, "ten");
        smap.put(20, "twenty");
        smap.put(30, "thirty");

        smap.ceilingKey(15);   // 20   >=15 的最小键
        smap.floorKey(15);     // 10   <=15 的最大键
        smap.higherKey(20);    // 30   >20 的最小键
        smap.lowerKey(20);     // 10   <20 的最大键
        smap.firstEntry();     // 10=ten
        smap.lastEntry();      // 30=thirty
    }
}
