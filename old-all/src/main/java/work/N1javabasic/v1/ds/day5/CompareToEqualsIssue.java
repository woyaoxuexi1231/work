package work.N1javabasic.v1.ds.day5;

import java.util.*;

public class CompareToEqualsIssue {
    static class Person implements Comparable<Person> {
        String name;
        Person(String n) { name = n; }

        // 错误版：仅比长度
        @Override
        public int compareTo(Person o) {
            // 仅比长度
            return Integer.compare(name.length(), o.name.length());
        }

        @Override
        public boolean equals(Object o) {
            // equals 是正常的
            if (this == o) return true;
            if (!(o instanceof Person)) return false;
            return name.equals(((Person)o).name);
        }
        @Override public int hashCode() { return name.hashCode(); }
        @Override public String toString() { return name; }
    }

    public static void main(String[] args) {
        TreeMap<Person, String> tree = new TreeMap<>();
        // 这里 treeMap 会进行排序，使用 compareTo 方法，但是只比较了长度，所以compareTo返回的是0，treeMap会判定两个对象相等
        tree.put(new Person("Alice"), "A");
        tree.put(new Person("Bobbi"), "B"); // 长度5，compareTo=0
        System.out.println("TreeMap size: " + tree.size()); // 1，Bobbi 覆盖了 Alice
        System.out.println("Value for 'Alice': " + tree.get(new Person("Alice"))); // 输出 B

        // 修正版：长度相同时比较字符串
        TreeMap<Person, String> fixed = new TreeMap<>((a, b) -> {
            int cmp = Integer.compare(a.name.length(), b.name.length());
            return cmp != 0 ? cmp : a.name.compareTo(b.name);
        });
        fixed.put(new Person("Alice"), "A");
        fixed.put(new Person("Bobbi"), "B");
        System.out.println("Fixed size: " + fixed.size()); // 2
    }
}