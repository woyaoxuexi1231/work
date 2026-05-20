package com.example.dynamicds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
    private final DynamicLocalTxSupport localTxSupport;
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
        return localTxSupport.executeOn(PlatformBootstrapService.DS_HUB, connection -> {
            // 这里故意保留手写 JDBC + select ... for update，
            // 因为 Leaf-segment 的核心价值就在于“中心库一行记录的行锁分配号段”。
            try (PreparedStatement select = connection.prepareStatement(
                    "select max_id, step from leaf_alloc where biz_tag = ? for update")) {
                select.setString(1, tag);
                try (ResultSet rs = select.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("leaf tag 不存在: " + tag);
                    }
                    long oldMax = rs.getLong("max_id");
                    int step = rs.getInt("step");
                    long newMax = oldMax + step;
                    try (PreparedStatement update = connection.prepareStatement(
                            "update leaf_alloc set max_id = ? where biz_tag = ?")) {
                        update.setLong(1, newMax);
                        update.setString(2, tag);
                        update.executeUpdate();
                    }
                    log.info("[Leaf] tag={} 从数据库拿到新号段: {}-{}，step={}", tag, oldMax + 1, newMax, step);
                    return new Segment(oldMax + 1, oldMax + 1, newMax, true);
                }
            }
        });
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
