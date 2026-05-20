package work.N1javabasic.v1;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author hulei
 * @since 2026/5/20 22:23
 */

public class ConcurrentHashMapTest {

    public static void main(String[] args) {
        ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key3", "value3");
        int size = map.size();
        /*
        final long sumCount() {
            CounterCell[] cs = counterCells; // CounterCell 内部拥有一个volatile long value
            long sum = baseCount; // private transient volatile long baseCount; 可以从table中重建，没必要序列化
            if (cs != null) {
                for (CounterCell c : cs)
                    if (c != null)
                        sum += c.value;
            }
            return sum;
        }

        @jdk.internal.vm.annotation.Contended static final class CounterCell {
            volatile long value;
            CounterCell(long x) { value = x; }
        }
         */
    }
}
