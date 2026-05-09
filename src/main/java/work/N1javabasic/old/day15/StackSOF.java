package work.N1javabasic.old.day15;

/**
 * VM Args: -Xss128k   （设置栈容量为128k，降低溢出门槛）
 */
public class StackSOF {
    private int stackLength = 1;

    public void stackLeak() {
        stackLength++;
        stackLeak();  // 无限递归
    }

    public static void main(String[] args) {
        StackSOF sof = new StackSOF();
        try {
            sof.stackLeak();
        } catch (Throwable e) {
            System.out.println("stack length:" + sof.stackLength);
            throw e;  // 最终抛出 StackOverflowError
        }
    }
}