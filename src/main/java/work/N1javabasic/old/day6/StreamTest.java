package work.N1javabasic.old.day6;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * @author hulei
 * @since 2026/5/2 16:40
 */

public class StreamTest {

    public static void main(String[] args) {

        test();
        parallel();
        optional();

        DefaultInterface defaultInterface = new DefaultTest();
        defaultInterface.print();
    }

    public static void test(){

        // ========== Stream 流式计算原理、Lambda 的实现方式 （invokedynamic） =============
        List<String> list = List.of("hello", "world", "hello world");
        // 这里应该是使用了过滤链，感觉有点像责任链模式
        // 在最终执行foreach, collection 这种方法的时候再一次调用之前创建的各种函数式接口来进行数据的处理
        // lambda 函数是接口的实现方式有很多种，首先函数式接口必须是一个接口，且此接口有且仅能包含一个方法
        // 函数式接口可以使用 @FunctionalInterface 进行修饰
        list.stream()
                .filter(s -> s.contains("hello"))
                .map(String::toUpperCase)
                .forEach(System.out::println);
        System.out.println("==================");

    }

    public static void parallel(){
        // =========== stream parallel stream 并行流 =====================
        List<Integer> list = IntStream.rangeClosed(1, 50_000_000).boxed().collect(Collectors.toList());

        // 模拟一个稍微耗时的计算（比如求平方根）
        long t1 = System.nanoTime();
        double sum1 = list.stream().mapToDouble(i -> Math.sqrt(i)).sum();
        long t2 = System.nanoTime();

        long t3 = System.nanoTime();
        double sum2 = list.parallelStream().mapToDouble(i -> Math.sqrt(i)).sum();
        long t4 = System.nanoTime();

        System.out.println("串行: " + (t2 - t1)/1_000_000 + "ms");
        System.out.println("并行: " + (t4 - t3)/1_000_000 + "ms");
        // 在 8 核机器上，并行可能快 3~5 倍


        // 有什么坑？
        // 1. 底层使用forkjoinpool，都是默认用的同一个线程池，那么会和其他使用默认 forkjoinpool的任务冲突，导致性能下降
        // 2. 使用并行流需要确保各任务独立

    }

    public static void optional(){
        // =========== Optional 优化 null 值处理 =====================
        String o = Optional.<String>ofNullable(null).orElseGet(() -> "hello");

        // 我认为，Optional确实一定程度上避免了空指针的问题
        // 1. 可以通过isPresent方法判断是否为空
        // 2. 可以通过orElseGet方法获取一个默认值

    }



}
