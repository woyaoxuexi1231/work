package work.N1javabasic.newn.day1;

import java.util.HashMap;
import java.util.Map;

class CollisionObject {
    int id;
    CollisionObject(int id) { this.id = id; }

    // 故意制造哈希冲突 —— 全部返回 1
    @Override
    public int hashCode() {
        return 1;
    }

    // equals 正常基于 id 比较
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CollisionObject)) return false;
        return id == ((CollisionObject) o).id;
    }
}

class BadConsistency {
    String value;
    BadConsistency(String value) { this.value = value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BadConsistency)) return false;
        return value.equals(((BadConsistency) o).value);
    }
    // 故意不重写 hashCode（使用 Object 默认实现）
}

public class TestContract {
    public static void main(String[] args) {
        // 一、证明 hashCode 相同的对象 equals 不一定相同
        CollisionObject a = new CollisionObject(100);
        CollisionObject b = new CollisionObject(200);
        System.out.println("a.hashCode() == b.hashCode() : " + (a.hashCode() == b.hashCode())); // true
        System.out.println("a.equals(b) : " + a.equals(b)); // false
        System.out.println("-----------------------------");

        // 二、证明 equals 相同的对象，hashCode 必须相同，否则 HashMap 异常
        Map<BadConsistency, String> map = new HashMap<>();
        BadConsistency k1 = new BadConsistency("key");
        BadConsistency k2 = new BadConsistency("key"); // equals k1，但 hashCode 不同
        map.put(k1, "value1");
        System.out.println("map.get(k1): " + map.get(k1));     // value1
        System.out.println("map.get(k2): " + map.get(k2));     // null，因为 hashCode 不同导致定位到不同桶
        System.out.println("k1.equals(k2): " + k1.equals(k2)); // true
        System.out.println("k1.hashCode() == k2.hashCode(): " + (k1.hashCode() == k2.hashCode())); // false
    }

/*
🔍 原理反思
1) hashCode 与 equals 的强制契约规则

如果两个对象 equals 为 true，则它们的 hashCode() 必须返回相同的整数。

如果两个对象 equals 为 false，则它们的 hashCode() 可以相同也可以不同。

该契约是单向的：equals 决定 hashCode 的一致性方向，反之不成立。

2) 什么是哈希冲突？

不同对象具有相同 hashCode 的现象称为哈希冲突。
由于哈希值空间（int 范围约 42 亿）可能小于对象值空间，根据鸽巢原理必然存在冲突。
散列表必须处理冲突，常见方法：链地址法（拉链法）、开放寻址法。

3) 为什么允许哈希冲突，但不允许 equals 相同而 hashCode 不同？

允许冲突：因为哈希函数不要求单射，输入空间巨大，哈希值空间有限，冲突不可避免。只要冲突处理机制得当，依然能高效检索。

不允许 equals 相同而 hashCode 不同：散列表依赖 hashCode 确定桶位置。如果两个相等的对象哈希码不同，会被分到不同桶，导致 contains、get 等方法对逻辑相等的对象失效，违反散列表语义。这是正确性约束，而非性能优化。
 */
}