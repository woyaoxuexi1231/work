package com.riskdatahub.id;

import com.riskdatahub.common.constant.HubConstants;
import com.riskdatahub.datasource.RoutingMybatisExecutor;
import com.riskdatahub.id.entity.LeafAlloc;
import com.riskdatahub.id.mapper.LeafAllocMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Leaf 号段模式 ID 生成器 — 中台全局唯一 ID 的核心，美团 Leaf 算法的简化实现。
 * <p>
 * <b>为什么不用自增 ID 或 UUID？</b>
 * <ul>
 *   <li>自增 ID — 跨数据库同步时冲突，且同步前无法预知 ID</li>
 *   <li>UUID — 太长（36 字符）、无序（影响 B+ 树索引性能）</li>
 *   <li>Leaf 号段 — 有序递增、纯数字（bigint）、可跨库不冲突、批量获取性能高</li>
 * </ul>
 * </p>
 * <p>
 * <b>双缓冲（double buffer）设计：</b>
 * 当前号段（current）用完之后无缝切换到预加载的下一段（next），
 * 切换是纯内存操作（指针交换），零等待。
 * 水位线设为 20%，即在当前号段剩余不足 20% 时异步触发预加载。
 * </p>
 *
 * @author risk-data-hub
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeafSegmentService {

    /** 每个 tag 一个 SegmentBuffer，ConcurrentHashMap 保证线程安全 */
    private final ConcurrentHashMap<String, SegmentBuffer> buffers = new ConcurrentHashMap<>();

    private final RoutingMybatisExecutor routingMybatisExecutor;

    private final LeafAllocMapper leafAllocMapper;

    private final PlatformTransactionManager transactionManager;

    /** 单线程异步预加载线程池 */
    private final ExecutorService preloadExecutor =
            Executors.newSingleThreadExecutor(new CustomizableThreadFactory("leaf-preload-"));

    /**
     * 获取下一个全局唯一 ID。
     * <p>
     * 流程：computeIfAbsent 惰性初始化 → synchronized 确保线程安全 →
     * 号段耗尽时切换 → 检查水位线触发预加载。
     * </p>
     *
     * @param tag 业务标签（如 "clean_trade"）
     * @return 全局唯一 ID
     */
    public long nextId(String tag) {
        SegmentBuffer buffer = buffers.computeIfAbsent(tag, key -> new SegmentBuffer());
        synchronized (buffer) {
            if (!buffer.current.hasNext()) {
                switchSegment(buffer, tag);
            }
            long id = buffer.current.nextId++;
            triggerPreloadIfNeeded(tag, buffer);
            return id;
        }
    }

    /**
     * 批量获取 ID（供测试 / 管理接口使用）。
     *
     * @param tag   业务标签
     * @param count 需要获取的 ID 数量
     * @return 包含 tag、count、ids 数组和 buffer 状态的 Map
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
     * 查询当前双缓冲状态（用于监控和管理接口）。
     *
     * @param tag 业务标签
     * @return 包含 current / next 号段状态的 Map
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
     * 清空本地缓存的号段，数据初始化后调用以确保旧的号段不会重用。
     */
    public void clearLocalCache() {
        buffers.clear();
    }

    /**
     * 切换号段：优先切换到预加载好的 next，没有再回源数据库申请新号段。
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
     * 检查水位线：当前号段剩余不足 20% 时异步触发预加载。
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
     * 从数据库申请新号段（悲观锁 FOR UPDATE 防止多实例竞争）。
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
     * 号段缓冲区 — 双缓冲容器。
     * current 为当前发放的号段，next 为预加载好的下一段。
     */
    private static final class SegmentBuffer {
        private Segment current = Segment.empty();
        private Segment next = Segment.empty();
        private boolean loadingNext;
    }

    /**
     * 号段 — 表示 [start, end] 范围内的连续 ID 集合。
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

        /** 创建号段副本，用于 current 切换 */
        private Segment copy() {
            return new Segment(start, nextId, end, ready);
        }
    }
}
