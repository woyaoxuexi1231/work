package work.N1javabasic.newn.day1;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

class Person {
    private String name;
    private int age;

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    // 只重写 equals，不重写 hashCode
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Person)) return false;
        Person other = (Person) obj;
        return age == other.age && Objects.equals(name, other.name);
    }
    // hashCode 使用 Object 默认实现（native，基于内存地址）
}

public class TestEqualsHashCode {
    public static void main(String[] args) {
        Set<Person> set = new HashSet<>();
        Person p1 = new Person("Alice", 30);
        Person p2 = new Person("Alice", 30);   // 逻辑相等

        set.add(p1);
        set.add(p2);

        System.out.println("Set 大小: " + set.size());  // 期望为 2，出现重复元素
        System.out.println("p1.equals(p2): " + p1.equals(p2));  // true
        System.out.println("p1.hashCode(): " + p1.hashCode());
        System.out.println("p2.hashCode(): " + p2.hashCode());
        System.out.println("p1 和 p2 hashCode 相同？ " + (p1.hashCode() == p2.hashCode()));
        System.out.println("Set 包含: " + set);

        /*
        🔍 原理反思
        1) 为什么只重写 equals 不重写 hashCode 会导致 HashSet 出现重复元素？

        HashSet 底层使用 HashMap：先根据对象的 hashCode() 定位到哈希桶，再用 equals() 判断桶内是否存在相同对象。
        如果两个 equals 相等的对象拥有不同的 hashCode，它们会被放入不同的桶中，HashSet 不会认为它们冲突，当然也就无法去重。

        2) hashCode 和 equals 的契约关系是什么？

        Object 类声明的核心约定（来自 Java 文档）：

        自反性、对称性、传递性、一致性 均须满足。

        相等对象必须具有相等的哈希码：如果 a.equals(b) 返回 true，则 a.hashCode() 必须等于 b.hashCode()。

        不相等的对象不要求有不等的哈希码，但散列性能依赖其散列值的均匀分布。

        3) 涉及哪些 Object 类的核心知识点？

        public native int hashCode(); —— 本地方法，默认返回基于对象内存地址的标识哈希码（identity hash code）。

        public boolean equals(Object obj) —— 默认实现使用 == 比较引用地址。

        所以默认 equals 与默认 hashCode 是匹配的：地址相同 → equals 为 true → hashCode 相同。

        一旦重写 equals 使用逻辑字段比较，必须同时重写 hashCode 使其与 equals 一致，否则违反契约，散列表容器（HashMap、HashSet、Hashtable）将无法正常工作。


         */
    }
}