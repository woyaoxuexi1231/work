package work.N1javabasic.old.day12;

import cn.hutool.core.thread.ThreadFactoryBuilder;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author hulei
 * @since 2026/5/4 22:42
 */

public class Test {

    public static void main(String[] args) {

        // 线程池的 7 大参数
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                // corePoolSize 核心线程数，线程池中始终存活的线程数量，即使空闲也不会回收
                5,
                // maximumPoolSize 最大线程数，线程池中允许创建的最大线程数
                // 当任务队列满了时会创建新线程，但总量不能超过这个值
                10,
                // keepAliveTime 用于控制非核心线程的生命周期。
                // 当线程池中的线程数量超过corePoolSize后，这些多出来的空闲线程能等待新任务的最长时间，超时后就会被回收
                60,
                // unit 与keepAliveTime配合，指定其时间单位，如TimeUnit.SECONDS
                TimeUnit.SECONDS,
                // workQueue 用于保存等待执行的任务。当核心线程都在忙时，新任务会先被放入这个队列等待
                // 这是一个BlockingQueue，阻塞队列，常见队列包含：
                // ArrayBlockingQueue - 基于数组的有界阻塞队列，容量固定，创建时必须指定大小
                // LinkedBlockingQueue - 基于链表的无界阻塞队列，容量不固定，默认大小为Integer.MAX_VALUE，指定容量大小时则创建有界队列
                // SynchronousQueue - 无界同步队列，容量为0，没有容量限制，新任务会直接提交给线程池，线程池会选择一个空闲线程执行任务，如果所有线程都忙，则新任务会阻塞
                // PriorityBlockingQueue - 基于优先级队列的无界阻塞队列，容量不固定，任务会按照优先级排序执行
                // 无界队列会因为任务无法达到上限进而使 maximumPoolSize 无效
                new LinkedBlockingQueue<>(20),
                // threadFactory 线程工厂，将按照此线程工厂创建线程
                new ThreadFactoryBuilder().setNamePrefix("day10-").build(),
                // rejectedExecutionHandler 拒绝策略，当线程池和任务队列都已饱和，会调用这个策略
                //
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略，当线程池满时，由调用者线程执行任务
        );

        // Worker 工作原理详解
        threadPoolExecutor.execute(() -> System.out.println("开始执行任务"));
        /*
        这是一个建议的 worker工作原理
        while (从阻塞队列获取任务){
            try(){
                处理任务;
            } catch(E e){
                捕获异常;
            }
        }
        worker将不停的在阻塞队列中获取任务，然后执行
         */

        // 使用完线程池后，调用shutdown()方法
        threadPoolExecutor.shutdown();
    }
}
