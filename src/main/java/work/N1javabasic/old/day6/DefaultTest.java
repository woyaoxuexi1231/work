package work.N1javabasic.old.day6;

/**
 * @author hulei
 * @since 2026/5/4 9:57
 */

public class DefaultTest implements DefaultInterface{
    @Override
    public void print() {
        // 方法一致的话，肯定不行，可以继承
        DefaultInterface.super.print();
    }
}
