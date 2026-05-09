package work.N1javabasic.old.day7;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.checkerframework.checker.units.qual.A;
import org.checkerframework.checker.units.qual.N;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * @author hulei
 * @since 2026/5/4 10:55
 */

public class LRUcache extends LinkedHashMap<String, Day7Test.Cache> {

    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Day7Test.Cache> eldest) {
        int maxCapacity = 5;
        return size() > maxCapacity;  // 当大小超过容量时，移除头部节点
    }
}
