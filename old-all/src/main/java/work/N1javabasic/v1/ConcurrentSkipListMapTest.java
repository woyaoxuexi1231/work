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


    }
}
