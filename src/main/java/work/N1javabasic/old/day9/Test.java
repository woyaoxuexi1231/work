package work.N1javabasic.old.day9;

import lombok.SneakyThrows;

/**
 * @author hulei
 * @since 2026/5/4 15:13
 */

public class Test {

    @SneakyThrows
    public static void main(String[] args) {
        Thread t1 = new Thread(() -> {
            Test.test();
        });
        Thread t2 = new Thread(() -> {
            Test.test();
        });

        Test test = new Test();
        Test test1 = new Test();
        Thread thread3 = new Thread(() -> {
            test.test2();
        });
        Thread thread4 = new Thread(() -> {
            test1.test2();
        });

        t1.start();
        t2.start();
        thread3.start();
        thread4.start();


        Thread.sleep(10000);

        // javap -p -c Test.class
        //     103: monitorenter
        //      104: getstatic     #75                 // Field java/lang/System.out:Ljava/io/PrintStream;
        //      107: ldc           #77                 // String test
        //      109: invokevirtual #83                 // Method java/io/PrintStream.println:(Ljava/lang/String;)V
        //      112: aload         7
        //      114: monitorexit
        //      115: goto          126
        // 这里看到字节码中加上了 monitorenter 和 monitorexit
        synchronized (Test.class){
            System.out.println("test");
        }

    }

    @SneakyThrows
    public synchronized static void test() {
        System.out.println("test");
        Thread.sleep(5000);
    }

    @SneakyThrows
    public synchronized void test2() {
        System.out.println("test2");
        Thread.sleep(5000);
    }
}
