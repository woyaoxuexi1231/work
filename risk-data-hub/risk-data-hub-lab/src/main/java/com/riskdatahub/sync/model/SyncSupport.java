package com.riskdatahub.sync.model;

import com.riskdatahub.common.util.TimeUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步框架通用类型定义容器。
 * <p>
 * 将 {@link PageChunk}、{@link BusinessSyncResult}、{@link SyncCounter}
 * 这些紧耦合的类型收拢在同一命名空间下，
 * 避免 sync 包下散落大量零散文件。
 * </p>
 *
 * @author risk-data-hub
 */
public final class SyncSupport {

    private SyncSupport() {
    }

    /**
     * 分页数据块 — 在拉取线程和落库线程之间传递的"页"单位。
     * <p>
     * {@link #metrics} 随页传递，拉取/落库线程通过 {@link SyncMetrics} 打点时间戳，持久化时再写入 DB。
     * 使用哨兵模式（{@link #end()}）标记数据结束，消费者只需判断 {@link #isEnd()} 即可优雅终止。
     * </p>
     *
     * @param <S> 源数据类型
     */
    @Data
    public static class PageChunk<S> {
        private int pageNo;
        private List<S> rows;
        private boolean end;
        private SyncMetrics metrics;

        public PageChunk(int pageNo, List<S> rows, boolean end) {
            this.pageNo = pageNo;
            this.rows = rows;
            this.end = end;
        }

        public static <S> PageChunk<S> data(int pageNo, List<S> rows, SyncMetrics metrics) {
            PageChunk<S> chunk = new PageChunk<>(pageNo, rows, false);
            chunk.setMetrics(metrics);
            return chunk;
        }

        /**
         * 构造结束哨兵对象。
         *
         * @param <S> 数据类型
         * @return 结束哨兵
         */
        public static <S> PageChunk<S> end() {
            return new PageChunk<>(0, Collections.emptyList(), true);
        }
    }

    /**
     * 单个业务类型的同步结果汇总。
     */
    @Data
    @AllArgsConstructor
    public static class BusinessSyncResult {
        private String businessCode;
        private int pageCount;
        private int pulledCount;
        private int savedCount;
        private long lastRowId;

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("businessCode", businessCode);
            result.put("pageCount", pageCount);
            result.put("pulledCount", pulledCount);
            result.put("savedCount", savedCount);
            result.put("lastRowId", lastRowId);
            return result;
        }
    }

    /**
     * 批次时间节点采集 — 拉取/落库全流程唯一打点入口，持久化前由模板转为 {@link com.riskdatahub.sync.entity.SyncBatchMetrics}。
     */
    @Data
    public static class SyncMetrics {
        private Integer batchNo;
        private Integer pulledCount;
        private Integer savedCount;
        private Integer insertCount;
        private Integer updateCount;

        private LocalDateTime fetchStartedAt;
        private LocalDateTime fetchFinishedAt;
        private LocalDateTime fetchQueuedAt;
        private LocalDateTime fetchQueuedFinishedAt;
        private LocalDateTime idGenStartedAt;
        private LocalDateTime idGenFinishedAt;
        private LocalDateTime transformStartedAt;
        private LocalDateTime transformFinishedAt;
        private LocalDateTime cacheLookupStartedAt;
        private LocalDateTime cacheLookupFinishedAt;
        private LocalDateTime insertStartedAt;
        private LocalDateTime insertFinishedAt;
        private LocalDateTime cacheAddStartedAt;
        private LocalDateTime cacheAddFinishedAt;
        private LocalDateTime globalIdQueryStartedAt;
        private LocalDateTime globalIdQueryFinishedAt;
        private LocalDateTime setIdStartedAt;
        private LocalDateTime setIdFinishedAt;
        private LocalDateTime updateStartedAt;
        private LocalDateTime updateFinishedAt;

        public static SyncMetrics forPage(int pageNo, int rowCount) {
            SyncMetrics m = new SyncMetrics();
            m.setBatchNo(pageNo);
            m.setPulledCount(rowCount);
            m.setSavedCount(rowCount);
            return m;
        }

        public void stampFetchStarted() { fetchStartedAt = TimeUtils.now(); }
        public void stampFetchFinished() { fetchFinishedAt = TimeUtils.now(); }
        public void stampFetchQueued() { fetchQueuedAt = TimeUtils.now(); }
        public void stampFetchQueuedFinished() { fetchQueuedFinishedAt = TimeUtils.now(); }
        public void stampIdGenStarted() { idGenStartedAt = TimeUtils.now(); }
        public void stampIdGenFinished() { idGenFinishedAt = TimeUtils.now(); }
        public void stampTransformStarted() { transformStartedAt = TimeUtils.now(); }
        public void stampTransformFinished() { transformFinishedAt = TimeUtils.now(); }
        public void stampCacheLookupStarted() { cacheLookupStartedAt = TimeUtils.now(); }
        public void stampCacheLookupFinished() { cacheLookupFinishedAt = TimeUtils.now(); }
        public void stampInsertStarted() { insertStartedAt = TimeUtils.now(); }
        public void stampInsertFinished(int cnt) { insertFinishedAt = TimeUtils.now(); insertCount = cnt; }
        public void stampCacheAddStarted() { cacheAddStartedAt = TimeUtils.now(); }
        public void stampCacheAddFinished() { cacheAddFinishedAt = TimeUtils.now(); }
        public void stampGlobalIdQueryStarted() { globalIdQueryStartedAt = TimeUtils.now(); }
        public void stampGlobalIdQueryFinished() { globalIdQueryFinishedAt = TimeUtils.now(); }
        public void stampSetIdStarted() { setIdStartedAt = TimeUtils.now(); }
        public void stampSetIdFinished() { setIdFinishedAt = TimeUtils.now(); }
        public void stampUpdateStarted() { updateStartedAt = TimeUtils.now(); }
        public void stampUpdateFinished(int cnt) { updateFinishedAt = TimeUtils.now(); updateCount = cnt; }
    }

    /**
     * 线程安全的同步计数器。
     * <p>
     * 运行在多线程环境下（拉取线程写入 pulled，落库线程写入 saved），
     * 累加操作发生在各自单一线程内，无需加锁。
     * </p>
     */
    @Getter
    public static class SyncCounter {
        @Setter
        private int pageCount;
        private volatile int pulledCount;
        private volatile int savedCount;
        @Setter
        private long lastRowId;
        private long savedMaxRowId;

        public void addPulledCount(int pulledCount) { this.pulledCount += pulledCount; }

        public void incrementSavedCount() { this.savedCount++; }

        public void updateSavedMaxRowId(long rowId) {
            if (rowId > savedMaxRowId) {
                this.savedMaxRowId = rowId;
            }
        }
    }
}
