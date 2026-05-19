package com.example.dynamicds.service;

import com.example.dynamicds.datasource.RoutingJdbcExecutor;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class LeafSegmentService {

    private final ConcurrentHashMap<String, SegmentBuffer> buffers = new ConcurrentHashMap<>();
    private final DynamicLocalTxSupport localTxSupport;
    private final ExecutorService preloadExecutor =
            Executors.newSingleThreadExecutor(new CustomizableThreadFactory("leaf-preload-"));

    public LeafSegmentService(DynamicLocalTxSupport localTxSupport) {
        this.localTxSupport = localTxSupport;
    }

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
            buffer.current = buffer.next.copy();
            buffer.next = Segment.empty();
            return;
        }
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
        buffer.loadingNext = true;
        preloadExecutor.submit(() -> {
            Segment next = fetchSegment(tag);
            synchronized (buffer) {
                buffer.next = next;
                buffer.loadingNext = false;
            }
        });
    }

    private Segment fetchSegment(String tag) {
        return localTxSupport.executeOn(PlatformBootstrapService.DS_META, connection -> {
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
