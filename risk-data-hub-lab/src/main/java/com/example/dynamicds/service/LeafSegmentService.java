package com.example.dynamicds.service;

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
 * Leaf 号段模式 ID 生成器 — 中台全局 ID 的核心。
 *
 * 原理：
 * 1. 从数据库 leaf_alloc 表申请一段连续 ID（当前号段 current）
 * 2. 当前号段用掉 80% 后，异步预加载下一段（next），避免同步阻塞
 * 3. 当前号段耗尽时，无缝切换到预加载的 next 号段
 * 4. 若预加载未完成（例如首次启动），同步回源数据库申请新号段
 *
 * 四个业务表各自独立号段：clean_stock / clean_trade / clean_position / clean_asset
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LeafSegmentService {

    private final ConcurrentHashMap<String, SegmentBuffer> buffers = new ConcurrentHashMap<>();
    private final RoutingMybatisExecutor routingMybatisExecutor;
    private final LeafAllocMapper leafAllocMapper;
    private final PlatformTransactionManager transactionManager;
    private final ExecutorService preloadExecutor =
            Executors.newSingleThreadExecutor(new CustomizableThreadFactory("leaf-preload-"));

    /**
     * 获取下一个 ID：检查当前号段 → 用完则切换/申请 → 触发预加载 → 返回 ID
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

    public void clearLocalCache() {
        buffers.clear();
    }

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
     * 当前号段剩余 < 20% 时异步触发下一段预加载
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
     * 从数据库申请新号段（事务内：SELECT FOR UPDATE 加锁 → 更新 maxId → 返回）
     */
    private Segment fetchSegment(String tag) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return routingMybatisExecutor.query(PlatformBootstrapService.DS_HUB, () ->
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

    private static final class SegmentBuffer {
        private Segment current = Segment.empty();
        private Segment next = Segment.empty();
        private boolean loadingNext;
    }

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

        private static Segment empty() {
            return new Segment(0, 1, 0, false);
        }

        private boolean hasNext() {
            return ready && nextId <= end;
        }

        private Segment copy() {
            return new Segment(start, nextId, end, ready);
        }
    }
}
