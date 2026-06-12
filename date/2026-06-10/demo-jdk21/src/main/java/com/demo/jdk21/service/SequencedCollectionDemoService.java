package com.demo.jdk21.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 演示 JDK 21 Sequenced Collections（有序集合）—— 正式
 *
 * 核心思想：统一了 List/LinkedHashSet/LinkedHashMap 等有序集合的 API
 * 替代方案：list.get(0)、list.get(list.size()-1)、Collections.reverse() 等分散写法
 */
@Service
public class SequencedCollectionDemoService {

    private static final Logger log = LoggerFactory.getLogger(SequencedCollectionDemoService.class);

    public Map<String, Object> demo() {
        var result = new LinkedHashMap<String, Object>();

        // === 1. SequencedCollection（List 实现了它）===
        var list = new ArrayList<>(List.of("apple", "banana", "cherry", "date", "elderberry"));

        // getFirst() / getLast()：获取首尾元素
        result.put("1_getFirst", list.getFirst());   // "apple"
        result.put("1_getLast", list.getLast());      // "elderberry"

        // addFirst() / addLast()：在首尾添加
        list.addFirst("zero");
        list.addLast("final");
        result.put("2_addFirst_addLast", list);

        // reversed()：返回反转的视图（不修改原列表）
        result.put("3_reversed", list.reversed());
        result.put("3_原列表不变", list);

        // === 2. SequencedSet（LinkedHashSet 实现了它）===
        var set = new LinkedHashSet<>(List.of("c", "a", "b", "d"));
        result.put("4_Set_getFirst", set.getFirst());  // "c"（保持插入顺序）
        result.put("4_Set_getLast", set.getLast());    // "d"
        result.put("4_Set_reversed", set.reversed());

        // === 3. SequencedMap（LinkedHashMap 实现了它）===
        var map = new LinkedHashMap<String, Integer>();
        map.put("first", 1);
        map.put("second", 2);
        map.put("third", 3);

        result.put("5_Map_firstEntry", map.firstEntry());
        result.put("5_Map_lastEntry", map.lastEntry());
        result.put("5_Map_reversed", map.reversed());

        // pollFirstEntry / pollLastEntry：取出并删除
        var polledFirst = map.pollFirstEntry();
        var polledLast = map.pollLastEntry();
        result.put("6_pollFirst", polledFirst);
        result.put("6_pollLast", polledLast);
        result.put("6_剩余", map);

        result.put("7_说明", "getFirst/getLast/reversed/addFirst/addLast — 一套 API 统一 List/Set/Map");

        log.info("✅ Sequenced Collections 演示完成");
        return result;
    }
}
