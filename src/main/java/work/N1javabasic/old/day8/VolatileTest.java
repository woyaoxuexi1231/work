package work.N1javabasic.old.day8;

/**
 * @author hulei
 * @since 2026/5/4 11:31
 */

public class VolatileTest {

    public static volatile int count = 0;

    public static volatile boolean flag = true;

    public static void main(String[] args) throws Exception {

        // 这里创建了两个线程，每个线程对count进行10000次自增操作
        // 其实这里可以看出，volatile 在这种情况下对于线程安全没有任何帮助，因为 count++ 非原子操作

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                count++;
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                count++;
            }
        });
        t1.start();
        t2.start();


        Thread readerThread = new Thread(() -> {
            System.out.println("读线程启动，等待 flag 变为 true");
            while (flag) {
                // 空循环等待 flag 变为 true
                // 如果 flag 没有使用 volatile 修饰，读线程可能看不到写线程的修改，导致死循环
            }
            System.out.println("读线程检测到 flag 变为 true，count 的值: " + count);
        }, "ReaderThread");

        Thread writerThread = new Thread(() -> {
            System.out.println("设置 flag = true");
            flag = true; // 修改 flag 为 true
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            flag = false;
        }, "WriterThread");

        readerThread.start();
        writerThread.start();


        t1.join();
        t2.join();


        /*
        javap -p -c VolatileTest.class


        private static void lambda$main$0();
          Code:
             0: iconst_0             // i = 0
             1: istore_0
             2: iload_0
             3: sipush 10000
             6: if_icmpge 23         // 如果 i >= 10000 → return

             9: getstatic #64        // ★ volatile 读 count
                // --- LoadLoad 屏障 --- （保证后续读操作不会被重排到此读之前）
                // --- LoadStore 屏障 ---（保证后续写操作不会被重排到此读之前）

            12: iconst_1
            13: iadd                  // count + 1
                //   <--- 注意！这里没有任何屏障，可以和其它线程交错 --->

            14: putstatic #64        // ★ volatile 写 count
                // putstatic 之前会插入：
                // --- StoreStore 屏障 ---（保证此写之前的所有普通写已经完成，不能重排到该写之后）
                // putstatic 之后会插入：
                // --- StoreLoad 屏障 ---（保证此写之后的读写操作不会被重排到此写之前，
                //                         并确保其它处理器立即可见此写）

            17: iinc   0, 1          // i++
            20: goto   2
            23: return


        getstatic（读）
        iadd（改）
        putstatic（写）

         */

    }
}
