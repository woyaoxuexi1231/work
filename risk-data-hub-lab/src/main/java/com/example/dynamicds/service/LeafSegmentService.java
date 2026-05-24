package com.example.dynamicds.service;

import com.example.dynamicds.bootstrap.HubConstants;
import com.example.dynamicds.datasource.RoutingMybatisExecutor;
import com.example.dynamicds.entity.LeafAlloc;
import com.example.dynamicds.mapper.LeafAllocMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Leaf 号段模式 ID 生成器 — 中台全局 ID 的核心，美团 Leaf 算法的简化实现。
 * <p>
 * <b>为什么不用自增 ID 或 UUID？</b>
 * <ul>
 *   <li><b>自增 ID</b> — 跨数据库同步时冲突，且同步前无法预知 ID。</li>
 *   <li><b>UUID</b> — 太长（36 字符）、无序（影响 B+ 树索引性能）。</li>
 *   <li><b>Leaf 号段</b> — 有序递增、纯数字（bigint）、可跨库不冲突、批量获取性能高。</li>
 * </ul>
 * <p>
 * <b>双缓冲（double buffer）设计</b><br>
 * 当前号段（current）用完之后无缝切换到预加载的下一段（next），
 * 切换是纯内存操作（指针交换），零等待。
 * 这避免了每次 ID 耗尽时都去数据库申请新号段的性能尖刺。
 * <p>
 * <b>为什么水位线是 20%？</b><br>
 * 20% 是经验值：假设号段步长 20，用掉 16 个（80%）时触发预加载。
 * 预加载的异步线程需要几十毫秒到几百毫秒，剩余 20% 足够覆盖这段时间。
 * 如果设 5%，网络抖动可能导致 ID 耗尽时预加载还没完成，被迫同步等待。
 * 如果设 50%，会过早预加载，浪费数据库连接。
 * <p>
 * <b>synchronized 锁在 buffer 上而非方法上</b><br>
 * 每个 tag（业务类型）有独立的 SegmentBuffer，锁只在同一个 buffer 内竞争。
 * 不同业务（clean_stock vs clean_trade）的 nextId() 调用不会互相阻塞，
 * 比直接在方法上 synchronized 或使用全局锁粒度更细。
 * <p>
 * <b>ConcurrentHashMap computeIfAbsent 的原子性</b><br>
 * buffers.computeIfAbsent(tag, ...) 保证多线程首次访问同一个 tag 时，
 * 只有一个线程创建 SegmentBuffer，其他线程等待。避免了"先检查再插入"的竞态条件。
 * <p>
 * <b>为什么 Segment 用 static final class？</b>
 * <ul>
 *   <li><b>final</b> — 不可继承，确保 Segment 的行为不会被子类改变。</li>
 *   <li><b>static</b> — 不持有外部类（SegmentBuffer）的引用，避免隐式引用链导致 GC 问题。</li>
 *   <li><b>不可变对象</b> — 字段全部 final（除 nextId 外）：start/end/ready 在构造后不再变化，
 *       只有 nextId 随分配递增。</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LeafSegmentService {

    /** 每个 tag 一个 SegmentBuffer，ConcurrentHashMap 保证线程安全 */
    private final ConcurrentHashMap<String, SegmentBuffer> buffers = new ConcurrentHashMap<>();
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final LeafAllocMapper leafAllocMapper;
    private final PlatformTransactionManager transactionManager;
    /** 单线程异步预加载 — 不需要多线程，因为预加载是轻量级 DB 操作 */
    private final ExecutorService preloadExecutor =
            Executors.newSingleThreadExecutor(new CustomizableThreadFactory("leaf-preload-"));

    /**
     * 获取下一个 ID：
     * 1. computeIfAbsent 惰性初始化当前 tag 的 SegmentBuffer（线程安全）
     * 2. synchronized(buffer) 保证同一 tag 内 ID 严格递增、不重复
     * 3. 当前号段耗尽 → switchSegment() 切换或回源
     * 4. 每次分配后检查水位线 → 触发异步预加载
     */
    public long nextId(String tag) {
        SegmentBuffer buffer = buffers.computeIfAbsent(tag, key -> new SegmentBuffer());
        synchronized (buffer) {
            // 当前号段用尽时，先尝试切换到预加载好的 next；没有再回源数据库申请新号段
            if (!buffer.current.hasNext()) {
                switchSegment(buffer, tag);
            }
            long id = buffer.current.nextId++;
            triggerPreloadIfNeeded(tag, buffer);
            return id;
        }
    }

    /**
     * 批量获取 ID（供测试/管理接口使用）
     */
    public Map<String, Object> nextIds(String tag, int count) {
        Map<String, Object> result = new LinkedHashMap<>();
        long[] ids = new long[count];
        for (int i = 0; i < count; i++) {
            ids[i] = nextId(tag);
        }
        result.put("tag", tag);
        result.put("count", count);
        result.put("ids", ids);
        result.put("bufferState", state(tag));
        return result;
    }

    /**
     * 查询当前双缓冲状态（用于监控和管理接口）
     */
    public Map<String, Object> state(String tag) {
        SegmentBuffer buffer = buffers.computeIfAbsent(tag, key -> new SegmentBuffer());
        synchronized (buffer) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("currentStart", buffer.current.start);
            result.put("currentNext", buffer.current.nextId);
            result.put("currentEnd", buffer.current.end);
            result.put("nextStart", buffer.next.start);
            result.put("nextEnd", buffer.next.end);
            result.put("nextReady", buffer.next.ready);
            result.put("loadingNext", buffer.loadingNext);
            return result;
        }
    }

    /**
     * 清空本地缓存 — 数据初始化后调用，确保旧的号段不会重用（可能已清表）
     */
    public void clearLocalCache() {
        buffers.clear();
    }

    /**
     * 切换号段：优先切换到预加载好的 next，没有再回源数据库申请
     */
    private void switchSegment(SegmentBuffer buffer, String tag) {
        if (buffer.next.ready) {
            log.info("[Leaf] tag={} 当前号段耗尽，切换到预加载 next buffer: {}-{}", tag, buffer.next.start, buffer.next.end);
            buffer.current = buffer.next.copy();
            buffer.next = Segment.empty();
            return;
        }
        log.info("[Leaf] tag={} 当前无 next buffer，回源数据库申请新号段", tag);
        Segment fresh = fetchSegment(tag);
        buffer.current = fresh;
        buffer.next = Segment.empty();
    }

    /**
     * 当前号段剩余 < 20% 时异步触发下一段预加载。
     * 双缓冲的关键：提前加载，让 next 就绪，等 current 耗尽时零等待切换。
     *
     * 注意 loadingNext 标志的作用：防止重复提交预加载任务。
     * 即使在高并发下，同一个 tag 的多个线程同时进入此方法，
     * 也只有第一个满足条件 + loadingNext=false 的线程会提交任务。
     */
    private void triggerPreloadIfNeeded(String tag, SegmentBuffer buffer) {
        long remaining = buffer.current.end - buffer.current.nextId + 1;
        long total = Math.max(1, buffer.current.end - buffer.current.start + 1);
        boolean lowWaterMark = remaining * 100 / total <= 20;
        if (!lowWaterMark || buffer.loadingNext || buffer.next.ready) {
            return;
        }
        log.info("[Leaf] tag={} 当前号段剩余不足 20%，异步预加载下一段", tag);
        buffer.loadingNext = true;
        preloadExecutor.submit(() -> {
            Segment next = fetchSegment(tag);
            synchronized (buffer) {
                buffer.next = next;
                buffer.loadingNext = false;
                log.info("[Leaf] tag={} next buffer 预加载完成: {}-{}", tag, next.start, next.end);
            }
        });
    }

    /**
     * 从数据库申请新号段。
     * 事务内 SELECT ... FOR UPDATE — 悲观锁防止多实例同时申请号段导致 ID 重复。
     * FOR UPDATE 锁的是 leaf_alloc 表的行级锁，不会阻塞其他表的读写。
     * 注意：这里手动创建 TransactionTemplate 而非使用 @Transactional，
     * 因为方法在异步线程中执行，@Transactional 的事务传播可能不生效。
     */
    private Segment fetchSegment(String tag) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return routingMybatisExecutor.query(HubConstants.DS_HUB, () ->
                transactionTemplate.execute(status -> {
                    LeafAlloc alloc = leafAllocMapper.selectForUpdate(tag);
                    if (alloc == null) {
                        throw new IllegalArgumentException("Leaf 发号器标签不存在: " + tag);
                    }
                    long oldMax = alloc.getMaxId();
                    int step = alloc.getStep();
                    long newMax = oldMax + step;
                    alloc.setMaxId(newMax);
                    leafAllocMapper.updateById(alloc);
                    log.info("[Leaf] tag={} 从数据库申请新号段: {}-{}，步长={}", tag, oldMax + 1, newMax, step);
                    return new Segment(oldMax + 1, oldMax + 1, newMax, true);
                }));
    }

    /**
     * 号段缓冲区 — 双缓冲的"盒子"。
     * current 是正在发放的号段，next 是预加载好的下一段。
     * loadingNext 防止重复提交预加载任务。
     */
    private static final class SegmentBuffer {
        private Segment current = Segment.empty();
        private Segment next = Segment.empty();
        private boolean loadingNext;
    }

    /**
     * 号段 — 表示 [start, end] 范围内的连续 ID 集合。
     * - start：起始 ID（包含）
     * - nextId：下一个要分配的 ID（从 start 开始，逐步递增）
     * - end：结束 ID（包含）
     * - ready：号段是否可用（empty() 返回 ready=false 的哨兵对象）
     *
     * 为什么用 static？
     * 避免隐式持有 SegmentBuffer（外部类）的引用，防止内存泄漏。
     * 为什么用 final class？
     * 号段逻辑应固定不可被继承篡改。
     */
    private static final class Segment {
        private final long start;
        private long nextId;
        private final long end;
        private final boolean ready;

        private Segment(long start, long nextId, long end, boolean ready) {
            this.start = start;
            this.nextId = nextId;
            this.end = end;
            this.ready = ready;
        }

        /** 空号段哨兵 — ready=false，hasNext() 返回 false */
        private static Segment empty() {
            return new Segment(0, 1, 0, false);
        }

        /** 号段是否还有剩余 ID */
        private boolean hasNext() {
            return ready && nextId <= end;
        }

        /**
         * 创建当前快照的副本。
         * 切换号段时使用 copy() 而非直接引用 next，这样 next 可以安全重置为 empty()，
         * 而 current 仍保留旧号段的状态用于调试。
         */
        private Segment copy() {
            return new Segment(start, nextId, end, ready);
        }
    }
}
