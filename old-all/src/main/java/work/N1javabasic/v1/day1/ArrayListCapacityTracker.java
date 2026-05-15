package work.N1javabasic.v1.day1;

import java.lang.reflect.Field;
import java.util.*;

public class ArrayListCapacityTracker {

    // 反射获取关键字段
    private static final Field ELEMENT_DATA_FIELD;
    private static final Field SIZE_FIELD;

    static {
        try {
            ELEMENT_DATA_FIELD = ArrayList.class.getDeclaredField("elementData");
            ELEMENT_DATA_FIELD.setAccessible(true);
            
            SIZE_FIELD = ArrayList.class.getDeclaredField("size");
            SIZE_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("JDK 版本不兼容", e);
        }
    }

    public static void main(String[] args) {
        // 创建初始容量为 2 的 ArrayList（便于观察多次扩容）
        ArrayList<String> list = new ArrayList<>(2);
        System.out.println("【初始状态】容量: " + getCapacity(list) + ", size: 0");

        // 添加 1000 个元素并跟踪扩容
        int expansionCount = 0;
        int lastCapacity = getCapacity(list);
        
        for (int i = 0; i < 1000; i++) {
            list.add("x");
            int currentCapacity = getCapacity(list);
            
            // 检测到容量变化（发生扩容）
            if (currentCapacity != lastCapacity) {
                expansionCount++;
                System.out.printf("【扩容 #%d】索引=%d | 需求容量=%d → 新容量=%d%n", 
                        expansionCount, i, i + 1, currentCapacity);
                lastCapacity = currentCapacity;
            }
        }
        
        System.out.printf("%n最终状态：容量=%d, size=%d, 扩容次数=%d%n", 
                getCapacity(list), list.size(), expansionCount);
    }

    // 获取当前实际容量（elementData.length）
    private static int getCapacity(ArrayList<?> list) {
        try {
            Object[] elementData = (Object[]) ELEMENT_DATA_FIELD.get(list);
            return elementData.length;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}