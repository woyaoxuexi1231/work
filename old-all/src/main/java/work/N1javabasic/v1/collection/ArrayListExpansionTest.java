package work.N1javabasic.v1.collection;

import java.util.*;

/**
 * 模拟内存池，用于测试 ArrayList 的 1.5 倍扩容策略是否真的能更好地利用内存碎片。
 * 使用首次适应算法分配连续内存块。
 */
class MemoryPool {
    // 空闲块链表节点
    private static class Block {
        int start;      // 起始地址
        int size;       // 块大小
        Block next;

        Block(int start, int size, Block next) {
            this.start = start;
            this.size = size;
            this.next = next;
        }
    }

    private Block freeList;
    private final int totalSize;
    private final Map<Integer, Integer> allocated = new HashMap<>(); // 地址 -> 大小，用于释放

    public MemoryPool(int totalSize) {
        this.totalSize = totalSize;
        // 初始时整个内存是一个大空闲块
        freeList = new Block(0, totalSize, null);
    }

    /**
     * 分配一块大小为 size 的连续内存，返回起始地址，失败返回 -1。
     */
    public int allocate(int size) {
        Block prev = null;
        Block curr = freeList;
        while (curr != null) {
            if (curr.size >= size) {
                int allocStart = curr.start;
                if (curr.size == size) {
                    // 正好整块分配
                    if (prev == null) freeList = curr.next;
                    else prev.next = curr.next;
                } else {
                    // 分割块
                    curr.start += size;
                    curr.size -= size;
                }
                allocated.put(allocStart, size);
                return allocStart;
            }
            prev = curr;
            curr = curr.next;
        }
        return -1; // 没有足够大的连续块
    }

    /**
     * 释放地址 start 开始的块，大小必须是分配时的大小。
     * 会与相邻空闲块合并。
     */
    public void free(int start) {
        Integer size = allocated.remove(start);
        if (size == null) throw new IllegalArgumentException("Invalid free");
        // 找到插入位置，保持空闲链表按地址排序
        Block prev = null;
        Block curr = freeList;
        while (curr != null && curr.start < start) {
            prev = curr;
            curr = curr.next;
        }
        // 新建释放块
        Block newBlock = new Block(start, size, curr);
        if (prev == null) {
            freeList = newBlock;
        } else {
            prev.next = newBlock;
        }
        // 尝试与前一个块合并
        if (prev != null && prev.start + prev.size == start) {
            prev.size += newBlock.size;
            prev.next = newBlock.next;
            newBlock = prev;
        }
        // 尝试与后一个块合并
        if (newBlock.next != null && newBlock.start + newBlock.size == newBlock.next.start) {
            newBlock.size += newBlock.next.size;
            newBlock.next = newBlock.next.next;
        }
    }

    // 调试用：打印空闲块列表
    public void printFreeList() {
        Block curr = freeList;
        System.out.print("Free blocks: ");
        while (curr != null) {
            System.out.print("[" + curr.start + ", " + (curr.start + curr.size) + ") ");
            curr = curr.next;
        }
        System.out.println();
    }

    /** 返回当前已分配的总字节数（用于检查） */
    public int totalAllocated() {
        int sum = 0;
        for (int sz : allocated.values()) sum += sz;
        return sum;
    }
}

public class ArrayListExpansionTest {
    private static final int MEMORY_SIZE = 4096;      // 模拟内存总大小
    private static final int INITIAL_CAPACITY = 10;   // ArrayList 默认初始容量
    private static final int SEED = 12345;             // 固定随机种子，保证可重复
    private static final int PRE_ALLOC_COUNT = 80;    // 预先制造碎片的小对象数量
    private static final int PRE_ALLOC_MIN = 8;
    private static final int PRE_ALLOC_MAX = 64;

    public static void main(String[] args) {
        // 先制造一个具有碎片的固定内存状态（用固定种子）
        Random rand = new Random(SEED);
        
        // 记录预先分配的对象信息（地址和大小），我们会在两个测试池中重现相同的碎片
        int[] preSizes = new int[PRE_ALLOC_COUNT];
        boolean[] preFreed = new boolean[PRE_ALLOC_COUNT];
        for (int i = 0; i < PRE_ALLOC_COUNT; i++) {
            preSizes[i] = PRE_ALLOC_MIN + rand.nextInt(PRE_ALLOC_MAX - PRE_ALLOC_MIN + 1);
            // 随机决定是否释放（约一半概率释放）
            preFreed[i] = rand.nextBoolean();
        }

        // 测试 1.5 倍扩容
        int maxCapacity15 = testExpansion(1.5, INITIAL_CAPACITY, preSizes, preFreed);
        // 测试 2 倍扩容
        int maxCapacity2 = testExpansion(2.0, INITIAL_CAPACITY, preSizes, preFreed);

        System.out.println("========== 结果 ==========");
        System.out.println("1.5倍扩容能达到的最大容量: " + maxCapacity15);
        System.out.println("2倍扩容能达到的最大容量:   " + maxCapacity2);
        if (maxCapacity15 > maxCapacity2) {
            System.out.println("结论：1.5倍扩容更能利用内存碎片，获得了更大的连续空间。");
        } else if (maxCapacity15 < maxCapacity2) {
            System.out.println("结论：本次测试中2倍扩容更好（可能碎片模式对其有利）。");
        } else {
            System.out.println("结论：两者表现相同。");
        }
    }

    /**
     * 在给定的碎片模式（preSizes, preFreed）下模拟 ArrayList 扩容过程。
     * @param factor 扩容因子，1.5 或 2.0
     * @param initialCapacity 初始容量
     * @param preSizes 预先分配的块大小数组
     * @param preFreed 对应块是否被释放
     * @return 能达到的最大容量（即最后成功分配的新数组大小）
     */
    private static int testExpansion(double factor, int initialCapacity,
                                     int[] preSizes, boolean[] preFreed) {
        // 创建独立内存池
        MemoryPool pool = new MemoryPool(MEMORY_SIZE);
        Map<Integer, Integer> preAllocMap = new HashMap<>(); // 模拟预先存在的占用块

        // 按照相同的碎片模式分配和释放
        for (int i = 0; i < preSizes.length; i++) {
            int addr = pool.allocate(preSizes[i]);
            if (addr == -1) {
                System.out.println("制造碎片时分配失败，内存池太小");
                return -1;
            }
            preAllocMap.put(addr, preSizes[i]);
            if (preFreed[i]) {
                pool.free(addr);
                preAllocMap.remove(addr);
            }
        }
        // 此时内存池处于碎片状态，preAllocMap 中的块仍然占用

        // 模拟 ArrayList 的增长
        int currentAddr = pool.allocate(initialCapacity); // 初始数组分配
        if (currentAddr == -1) {
            System.out.println("无法分配初始数组");
            return -1;
        }
        int currentCapacity = initialCapacity;
        int maxAchievedCapacity = initialCapacity;

        // 模拟不断添加元素，每次需要扩容时触发
        // 我们只关心连续扩容的极限，因此不模拟元素个数，而是直接反复扩容直到失败。
        // 实际情况：每次 size==capacity 时触发扩容，扩容后 capacity 变大。
        while (true) {
            int newCapacity;
            if (factor == 1.5) {
                // ArrayList 实际算法: newCapacity = oldCapacity + (oldCapacity >> 1)
                newCapacity = currentCapacity + (currentCapacity >> 1);
            } else {
                newCapacity = currentCapacity * 2;
            }
            // 尝试分配新数组
            int newAddr = pool.allocate(newCapacity);
            if (newAddr == -1) {
                // 分配失败，结束
                break;
            }
            // 成功：释放旧数组，更新当前
            pool.free(currentAddr);
            currentAddr = newAddr;
            currentCapacity = newCapacity;
            maxAchievedCapacity = newCapacity;
        }
        return maxAchievedCapacity;
    }
}