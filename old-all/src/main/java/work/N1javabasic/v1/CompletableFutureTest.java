package work.N1javabasic.v1;

import io.reactivex.rxjava3.core.Completable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;

import static java.lang.Thread.sleep;

/**
 * @author hulei
 * @since 2026/5/20 15:18
 */

public class CompletableFutureTest {
    public static void main(String[] args) throws ExecutionException, InterruptedException, TimeoutException {
        // 无返回值的异步任务
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            System.out.println("run in " + Thread.currentThread().getName());
        });

        // 有返回值的异步任务
        CompletableFuture<String> supplyFuture = CompletableFuture.supplyAsync(() -> {
            return "result";
        });


        // 默认使用 ForkJoinPool.commonPool()（守护线程），一般要传入自定义线程池：
        Executor pool = Executors.newFixedThreadPool(10);
        CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> "data", pool);
        ((ThreadPoolExecutor) pool).shutdown();

        /*
        2.1 一对一依赖
            thenApply(fn) — 接收上一步结果，返回新值（有返回）
            thenAccept(consumer) — 接收结果，不返回（消费）
            thenRun(runnable) — 不接收结果，也不返回（只执行动作）
        每个都有对应的 thenXxxAsync 可指定线程池，否则默认用上一步的线程。
         */
        CompletableFuture.supplyAsync(() -> "Hello")
                .thenApply(s -> s + " World")
                .thenApply(String::toUpperCase)
                .thenAccept(System.out::println);   // HELLO WORLD

        /*
        2.2 组合两个任务
            thenCombine(other, bifn) — 等待两者都完成，再合并结果
            thenAcceptBoth(other, biconsumer) — 消费两者结果
            runAfterBoth(other, runnable) — 两者都完成再执行动作
         */
        CompletableFuture<String> cf1 = CompletableFuture.supplyAsync(() -> "Hello");
        CompletableFuture<String> cf2 = CompletableFuture.supplyAsync(() -> "World");
        cf1.thenCombine(cf2, (r1, r2) -> r1 + " " + r2);
        cf1.thenAcceptBoth(cf2, (r1, r2) -> System.out.println(r1 + " " + r2));
        cf1.runAfterBoth(cf2, () -> System.out.println("Done"));

        /*
        2.3 只要其中一个完成
            applyToEither(other, fn) — 先完成的那个结果用于 fn
            acceptEither(other, consumer)
            runAfterEither(other, runnable)
         */
        CompletableFuture<String> fast = CompletableFuture.supplyAsync(() -> {
            try {
                sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return "fast";
        });
        CompletableFuture<String> slow = CompletableFuture.supplyAsync(() -> {
            try {
                sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return "slow";
        });
        fast.applyToEither(slow, s -> s.toUpperCase())
                .thenAccept(System.out::println); // FAST


        // exceptionally
        // 捕获异常，提供备用值：
        CompletableFuture.supplyAsync(() -> {
                    if (true) throw new RuntimeException("oops");
                    return "ok";
                })
                .exceptionally(ex -> "recovered " + ex.getMessage())
                .thenAccept(System.out::println);  // recovered java.lang.RuntimeException: oops
        // handle
        // 无论正常还是异常都会调用，可以转换或恢复：
        CompletableFuture.supplyAsync(() -> {
                    if (true) throw new RuntimeException("fail");
                    return 1;
                })
                .handle((res, ex) -> {
                    if (ex != null) return -1;
                    return res * 2;
                })
                .thenAccept(System.out::println);  // -1


        // whenComplete
        // 单纯做副作用，不改变结果：
        CompletableFuture.supplyAsync(() -> "data")
                .whenComplete((res, ex) -> {
                    if (ex != null) System.out.println("failed" + ex);
                    else System.out.println("done: " + res);
                });

        // 4. 多任务编排
        CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> "A");
        CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> "B");
        CompletableFuture<String> f3 = CompletableFuture.supplyAsync(() -> "C");

        List<CompletableFuture<?>> cList = new ArrayList<>();
        cList.add(f1);
        cList.add(f2);
        cList.add(f3);
        /*
        零长数组写法（更优惯例）
            创建了一个长度为 0 的数组，方法发现 a.length < size，于是立即自己创建了一个新的 length == size 的数组并返回。
            那个零长数组很快就成为垃圾被回收。
        优势：
            更简洁，不用提前计算 size。
            在大多数 JVM 上性能反而更好。现代 JIT 编译器对零长数组的分配有极致优化（转义分析、栈上分配），而 size() 方法调用和后续数组分配反而更重。
            线程安全性：如果在调用 size() 和 toArray 之间集合发生了并发修改，size() 得到的值可能已经过时。传零长数组让 toArray 内部根据集合的实际长度创建数组，天然避免了不一致。
        实际上，Josh Bloch（Java 集合框架作者之一）在《Effective Java》中也推荐这种写法。
         */
        CompletableFuture.allOf(cList.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    // 此时 f1/f2/f3 都已完成，可调用 join()
                    System.out.println(f1.join() + f2.join() + f3.join());
                });

        // allOf：等所有完成
        CompletableFuture<Void> all = CompletableFuture.allOf(f1, f2, f3);
        all.thenRun(() -> {
            // 此时 f1/f2/f3 都已完成，可调用 join()
            System.out.println(f1.join() + f2.join() + f3.join());
        });

        // anyOf：任意一个完成
        CompletableFuture<Object> any = CompletableFuture.anyOf(f1, f2, f3);
        any.thenAccept(res -> System.out.println("first done: " + res));


        // 5.手动完成与超时
        CompletableFuture<String> manualFuture = new CompletableFuture<>();
        new Thread(() -> {
            try { Thread.sleep(2000); manualFuture.complete("manual"); }
            catch (Exception e) { manualFuture.completeExceptionally(e); }
        }).start();

        // JDK9+ 有 orTimeout / completeOnTimeout，JDK8 可以自己实现超时
        manualFuture.exceptionally(ex -> "timeout")
                .get(1, java.util.concurrent.TimeUnit.SECONDS);
    }
}
