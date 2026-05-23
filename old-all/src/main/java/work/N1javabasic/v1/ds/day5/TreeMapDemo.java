package work.N1javabasic.v1.ds.day5;

import java.util.*;

public class TreeMapDemo {
    public static void main(String[] args) {
        // 1. 自然排序
        TreeMap<String, Double> natural = new TreeMap<>();
        natural.put("D", 150.0);
        natural.put("A", 200.0);
        natural.put("C", 150.0);
        natural.put("B", 300.0);
        System.out.println("自然顺序: " + natural.keySet()); // [A, B, C, D]
        System.out.println(natural.get("A"));

        // 2. 自定义排序
        TreeMap<OrderKey, String> custom = new TreeMap<>();
        custom.put(new OrderKey("D", 150.0), "D");
        custom.put(new OrderKey("A", 200.0), "A");
        custom.put(new OrderKey("C", 150.0), "C");
        custom.put(new OrderKey("B", 300.0), "B");
        System.out.print("自定义排序: ");
        for (OrderKey k : custom.keySet()) {
            System.out.print(k + " ");
        }
        // 预期: [C:150.0, D:150.0, A:200.0, B:300.0]  金额升序，同金额 ID 降序

        // 3. 区间操作
        System.out.println("\nfirstEntry: " + custom.firstEntry().getKey());
        System.out.println("lastEntry: " + custom.lastEntry().getKey());
        // 金额在 [150, 200) 的订单（不含200）
        NavigableMap<OrderKey, String> sub = custom.subMap(
            new OrderKey("", 150.0), true,
            new OrderKey("", 200.0), false
        );
        System.out.println("区间 [150,200): " + sub.keySet());
    }
}

class OrderKey implements Comparable<OrderKey> {
    String id;
    double amount;
    OrderKey(String id, double amount) { this.id = id; this.amount = amount; }
    public int compareTo(OrderKey o) {
        int cmp = Double.compare(amount, o.amount);
        if (cmp != 0) return cmp;
        return id.compareTo(o.id); // 金额相同，ID升序
    }
    public String toString() { return id + ":" + amount; }
}