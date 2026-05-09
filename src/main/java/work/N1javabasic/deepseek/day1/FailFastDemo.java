package work.N1javabasic.deepseek.day1;

import lombok.SneakyThrows;

import java.lang.reflect.Field;
import java.util.*;

public class FailFastDemo {

    @SneakyThrows
    public static void main(String[] args) {
        // 创建 ArrayList 并添加元素
        List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C", "D"));
        
        System.out.println("===== 触发 fail-fast 异常演示 =====");
        try {
            // 获取 modCount 初始值（通过反射）
            int initialModCount = getModCount(list);
            System.out.println("初始 modCount: " + initialModCount);
            
            Iterator<String> it = list.iterator();
            int expectedModCount = getExpectedModCount(it);
            System.out.println("迭代器 expectedModCount: " + expectedModCount);
            
            System.out.println("迭代中直接调用 list.remove(0)...");
            while (it.hasNext()) {
                String item = it.next();
                System.out.println("访问元素: " + item);
                
                // 在迭代过程中直接修改列表（触发 fail-fast）
                if ("A".equals(item)) {
                    list.remove(0);  // 错误操作：使用列表的 remove 而非迭代器的 remove
                }
            }
        } catch (ConcurrentModificationException e) {
            System.out.println("\n✅ 捕获到 ConcurrentModificationException!");
            System.out.println("异常信息: " + e.getMessage());
            
            // 再次检查 modCount（通过反射）
            try {
                int currentModCount = getModCount(list);
                System.out.println("当前 modCount: " + currentModCount);
                System.out.println("迭代器 expectedModCount 仍为: " + 
                                  getExpectedModCount(list.iterator())); // ← 这里创建了新迭代器 输出结果是 1
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        
        System.out.println("\n===== 安全删除演示（使用 Iterator.remove()） =====");
        List<String> safeList = new ArrayList<>(Arrays.asList("X", "Y", "Z"));
        Iterator<String> safeIt = safeList.iterator();
        
        while (safeIt.hasNext()) {
            String item = safeIt.next();
            System.out.println("安全删除前: " + safeList);
            
            if ("Y".equals(item)) {
                System.out.println("→ 安全执行 safeIt.remove() 删除: " + item);
                safeIt.remove();  // 正确操作：使用迭代器的 remove
            }
            
            System.out.println("安全删除后: " + safeList);
        }
    }

    // 通过反射获取 ArrayList 的 modCount
    private static int getModCount(List<?> list) throws Exception {
        // java.lang.reflect.Field modCountField = ArrayList.class.getDeclaredField("modCount");
        // modCountField.setAccessible(true);
        // return modCountField.getInt(list);

        Field modCount = ArrayList.class.getSuperclass().getDeclaredField("modCount");
        modCount.setAccessible(true);
        return (int) modCount.get(list);
    }

    // 通过反射获取 Iterator 的 expectedModCount
    private static int getExpectedModCount(Iterator<?> it) throws Exception {
        java.lang.reflect.Field itrField = it.getClass().getDeclaredField("expectedModCount");
        itrField.setAccessible(true);
        return itrField.getInt(it);
    }
}