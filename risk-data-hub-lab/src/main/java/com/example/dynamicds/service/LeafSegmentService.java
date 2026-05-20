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

    public long nextId(String tag) {
        SegmentBuffer buffer = buffers.computeIfAbsent(tag, key -> new SegmentBuffer());
        synchronized (buffer) {
            // current buffer 用完时，先尝试切到预加载好的 next；没有再回源数据库拿新号段。
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

    private void triggerPreloadIfNeeded(String tag, SegmentBuffer buffer) {
        long remaining = buffer.current.end - buffer.current.nextId + 1;
        long total = Math.max(1, buffer.current.end - buffer.current.start + 1);
        boolean lowWaterMark = remaining * 100 / total <= 20;
        if (!lowWaterMark || buffer.loadingNext || buffer.next.ready) {
            return;
        }
        log.info("[Leaf] tag={} 当前号段剩余不足 20%，异步预加载 next buffer", tag);
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

    private Segment fetchSegment(String tag) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return routingMybatisExecutor.query(PlatformBootstrapService.DS_HUB, () ->
                transactionTemplate.execute(status -> {
                    LeafAlloc alloc = leafAllocMapper.selectForUpdate(tag);
                    if (alloc == null) {
                        throw new IllegalArgumentException("leaf tag 不存在: " + tag);
                    }
                    long oldMax = alloc.getMaxId();
                    int step = alloc.getStep();
                    long newMax = oldMax + step;
                    alloc.setMaxId(newMax);
                    leafAllocMapper.updateById(alloc);
                    log.info("[Leaf] tag={} 从数据库拿到新号段: {}-{}，step={}", tag, oldMax + 1, newMax, step);
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
