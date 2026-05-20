package work.N1javabasic.v1;

import java.util.TreeMap;

/**
 * @author hulei
 * @since 2026/5/20 18:17
 */

public class TreeMapTest {
    public static void main(String[] args) {
        TreeMap<Integer, String> map = new TreeMap<>();
        map.put(10, "ten");
        map.put(20, "twenty");
        map.put(30, "thirty");
        map.put(40, "forty");

        System.out.println(map.ceilingKey(25));  // 30   >=25 的最小键
        System.out.println(map.floorKey(25));    // 20   <=25 的最大键
        System.out.println(map.higherKey(20));   // 30   >20 的最小键
        System.out.println(map.lowerKey(20));    // 10   <20 的最大键

        System.out.println(map.firstEntry());    // 10=ten
        System.out.println(map.lastEntry());     // 40=forty



    }
}
