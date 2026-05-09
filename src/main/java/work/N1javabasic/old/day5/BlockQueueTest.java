package work.N1javabasic.old.day5;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * 阻塞队列测试示例
 * <p>
 * 演示了 ArrayBlockingQueue 的基本用法和原理。
 * </p>
 * <p>
 * <strong>阻塞队列的定义：</strong>
 * 阻塞队列是一种支持阻塞操作的队列，当队列满时，插入操作会被阻塞；当队列空时，获取操作会被阻塞。
 * </p>
 * <p>
 * <strong>ArrayBlockingQueue 特点：</strong>
 * <ul>
 *   <li><strong>有界队列</strong>：容量固定，创建时指定容量</li>
 *   <li><strong>基于数组</strong>：使用数组实现，内存连续，访问效率高</li>
 *   <li><strong>线程安全</strong>：使用 ReentrantLock 保证并发安全</li>
 *   <li><strong>FIFO</strong>：先进先出，保证元素的顺序</li>
 *   <li><strong>阻塞操作</strong>：支持 put() 和 take() 等阻塞操作</li>
 * </ul>
 * </p>
 * <p>
 * <strong>ArrayBlockingQueue 实现原理：</strong>
 * <ul>
 *   <li><strong>数据结构</strong>：使用数组存储元素，使用两个索引（putIndex 和 takeIndex）实现循环队列</li>
 *   <li><strong>锁机制</strong>：使用一个 ReentrantLock 和两个 Condition（notEmpty 和 notFull）</li>
 *   <li><strong>put() 实现</strong>：
 *       <ul>
 *         <li>获取锁</li>
 *         <li>如果队列满，调用 notFull.await() 阻塞</li>
 *         <li>插入元素，调用 notEmpty.signal() 唤醒等待的消费者</li>
 *         <li>释放锁</li>
 *       </ul>
 *   </li>
 *   <li><strong>take() 实现</strong>：
 *       <ul>
 *         <li>获取锁</li>
 *         <li>如果队列空，调用 notEmpty.await() 阻塞</li>
 *         <li>取出元素，调用 notFull.signal() 唤醒等待的生产者</li>
 *         <li>释放锁</li>
 *       </ul>
 *   </li>
 * </ul>
 * </p>
 * <p>
 * <strong>阻塞操作详解：</strong>
 * <ul>
 *   <li><strong>put()</strong>：
 *       <ul>
 *         <li>当队列满时，会阻塞直到有空间可用</li>
 *         <li>支持中断，可能抛出 InterruptedException</li>
 *       </ul>
 *   </li>
 *   <li><strong>take()</strong>：
 *       <ul>
 *         <li>当队列空时，会阻塞直到有元素可取</li>
 *         <li>支持中断，可能抛出 InterruptedException</li>
 *       </ul>
 *   </li>
 *   <li><strong>offer()</strong>：非阻塞插入，队列满时返回 false</li>
 *   <li><strong>poll()</strong>：非阻塞获取，队列空时返回 null</li>
 * </ul>
 * </p>
 * <p>
 * <strong>其他阻塞队列类型：</strong>
 * <ul>
 *   <li><strong>LinkedBlockingQueue</strong>：
 *       <ul>
 *         <li>基于链表实现，可以是有界或无界的</li>
 *         <li>使用两个锁（putLock 和 takeLock），性能更好</li>
 *       </ul>
 *   </li>
 *   <li><strong>PriorityBlockingQueue</strong>：
 *       <ul>
 *         <li>无界队列，按优先级排序</li>
 *         <li>元素必须实现 Comparable 接口</li>
 *       </ul>
 *   </li>
 *   <li><strong>DelayQueue</strong>：
 *       <ul>
 *         <li>无界队列，元素带过期时间</li>
 *         <li>只有过期元素才能出队</li>
 *         <li>元素必须实现 Delayed 接口</li>
 *       </ul>
 *   </li>
 *   <li><strong>SynchronousQueue</strong>：
 *       <ul>
 *         <li>容量为 0，不存储元素</li>
 *         <li>生产者必须等待消费者，反之亦然</li>
 *       </ul>
 *   </li>
 * </ul>
 * </p>
 * <p>
 * <strong>使用场景：</strong>
 * <ul>
 *   <li><strong>生产者-消费者模式</strong>：使用阻塞队列实现生产者-消费者模式，简化代码</li>
 *   <li><strong>线程池</strong>：线程池使用阻塞队列作为任务队列</li>
 *   <strong>异步任务处理</strong>：使用阻塞队列缓冲任务，实现异步处理</li>
 * </ul>
 * </p>
 *
 * @author hulei42031
 * @since 2024-04-26 16:03
 */
@Slf4j
public class BlockQueueTest {

    /**
     * 程序入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        blockQueue();
    }

    /**
     * 演示阻塞队列的基本操作
     * <p>
     * 场景：生产者-消费者模式
     * <ul>
     *   <li>生产者线程：向队列中放入元素，当队列满时会阻塞</li>
     *   <li>消费者线程：从队列中取出元素，当队列空时会阻塞</li>
     * </ul>
     * </p>
     */
    @SneakyThrows
    private static void blockQueue() {
        log.info("=== 阻塞队列测试示例 ===");
        log.info("队列容量: 5，生产者将放入 10 个元素，消费者将取出 10 个元素");

        ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue<>(5);

        // 生产者线程
        Thread producer = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                try {
                    log.info("生产者：正在放入第 {} 个元素", i);
                    queue.put(i);
                    log.debug("生产者：成功放入元素 {}", i);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("生产者线程被中断", e);
                    break;
                }
            }
            log.info("生产者：已完成所有元素的放入");
        }, "ProducerThread");

        // 消费者线程
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    Integer element = queue.take();
                    log.info("消费者：成功取出元素 {}", element);
                }
                log.info("消费者：已完成所有元素的取出");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("消费者线程被中断", e);
            }
        }, "ConsumerThread");

        // 启动生产者
        producer.start();
        // 延迟启动消费者，模拟队列满的情况
        Thread.sleep(2000);
        // 启动消费者
        consumer.start();

        // 等待所有线程完成
        producer.join();
        consumer.join();

        log.info("测试完成");
    }
}
