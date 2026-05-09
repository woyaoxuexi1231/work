package work.N1javabasic.old.day7;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.LinkedHashMap;

/**
 * @author hulei
 * @since 2026/5/4 11:09
 */

public class Day7Test {

    public static void main(String[] args) {
        LinkedHashMap<Cache, String> queue = new LinkedHashMap<>();
        queue.put(new Cache(LocalDate.now().plusDays(1), "hulei1"), "hulei1");
        queue.put(new Cache(LocalDate.now().plusDays(4), "hulei4"), "hulei4");
        queue.put(new Cache(LocalDate.now().plusDays(5), "hulei5"), "hulei5");
        queue.put(new Cache(LocalDate.now().plusDays(6), "hulei6"), "hulei6");
        queue.get(new Cache(LocalDate.now().plusDays(1), "hulei1"));
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static  class Cache implements Comparable<Cache> {
        private LocalDate updateTime;
        private String name;

        @Override
        public int compareTo(@NotNull Cache o) {
            if (o.getUpdateTime().isBefore(this.getUpdateTime())) {
                return 1;
            } else if (o.getUpdateTime().isAfter(this.getUpdateTime())) {
                return -1;
            } else {
                return 0;
            }
        }
    }

}
