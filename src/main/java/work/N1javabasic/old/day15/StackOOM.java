package work.N1javabasic.old.day15;

/**
 * VM Args: -Xss2M   （每个线程栈极大，容易耗尽内存）
 * 运行时注意：可能导致操作系统假死，请先保存工作
 */
public class StackOOM {
    private void dontStop() { while (true) {} }

    public void stackLeakByThread() {
        while (true) {
            Thread t = new Thread(this::dontStop);
            t.start();
        }
    }

    public static void main(String[] args) {
        new StackOOM().stackLeakByThread();
    }
}