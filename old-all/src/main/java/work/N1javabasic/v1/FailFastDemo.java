package work.N1javabasic.v1;

import java.util.*;

public class FailFastDemo {
    public static void main(String[] args) {
        List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C", "D"));
        
        System.out.println("❌ 场景1：foreach 遍历时删除元素");
        try {
            for (String item : list) {
                if (item.equals("B")) {
                    list.remove(item);  // 💥 ConcurrentModificationException
                }
            }
        } catch (ConcurrentModificationException e) {
            System.out.println("  捕获异常: " + e.getClass().getSimpleName());
        }
        
        System.out.println("\n❌ 场景2：普通 for 循环删除");
        try {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).equals("C")) {
                    list.remove(i);  // 💥 索引错乱 + 异常
                }
            }
        } catch (ConcurrentModificationException e) {
            System.out.println("  捕获异常: " + e.getClass().getSimpleName());
        }
    }
}
