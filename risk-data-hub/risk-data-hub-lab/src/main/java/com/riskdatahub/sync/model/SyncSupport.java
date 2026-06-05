package com.riskdatahub.sync.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

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
     * 使用哨兵模式（{@link #end()}）标记数据结束，消费者只需判断 {@link #isEnd()} 即可优雅终止。
     * </p>
     *
     * @param <S> 源数据类型
     */
    @Data
    @AllArgsConstructor
    public static class PageChunk<S> {
        private int pageNo;
        private List<S> rows;
        private boolean end;
        private long createdAt;
        private long fetchDurationMs;

        public PageChunk(int pageNo, List<S> rows, boolean end) {
            this.pageNo = pageNo;
            this.rows = rows;
            this.end = end;
            this.createdAt = System.currentTimeMillis();
            this.fetchDurationMs = 0;
        }

        /**
         * 构造包含数据的页块（记录创建时间戳，用于计算队列等待耗时）。
         *
         * @param pageNo 页码
         * @param rows   数据行
         * @param <S>    数据类型
         * @return 数据页块
         */
        public static <S> PageChunk<S> data(int pageNo, List<S> rows) {
            return new PageChunk<>(pageNo, rows, false);
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
     * 单个业务类型的同步结果汇总 — 不可变值对象。
     */
    @Data
    @AllArgsConstructor
    public static class BusinessSyncResult {
        private String businessCode;
        private int pageCount;
        private int pulledCount;
        private int savedCount;
        private long lastRowId;

        // ====== 耗时指标（毫秒），用于性能分析 ======
        private long fetchDurationMs;
        private long transformDurationMs;
        private long saveDurationMs;
        private int fetchPageCount;
        private int saveBatchCount;
        private long maxFetchPageMs;
        private long maxSaveBatchMs;

        // ====== saveBatch 子步骤耗时 ======
        private long cacheLookupDurationMs;
        private long batchInsertDurationMs;
        private long globalIdQueryDurationMs;
        private long batchUpdateDurationMs;

        /**
         * 转换为 Map，保证字段顺序固定（便于 JSON 序列化和日志查看）。
         *
         * @return 有序 Map
         */
        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("pageCount", pageCount);
            result.put("pulledCount", pulledCount);
            result.put("savedCount", savedCount);
            result.put("lastRowId", lastRowId);
            result.put("fetchDurationMs", fetchDurationMs);
            result.put("transformDurationMs", transformDurationMs);
            result.put("saveDurationMs", saveDurationMs);
            result.put("fetchPageCount", fetchPageCount);
            result.put("saveBatchCount", saveBatchCount);
            result.put("maxFetchPageMs", maxFetchPageMs);
            result.put("maxSaveBatchMs", maxSaveBatchMs);
            result.put("cacheLookupDurationMs", cacheLookupDurationMs);
            result.put("batchInsertDurationMs", batchInsertDurationMs);
            result.put("globalIdQueryDurationMs", globalIdQueryDurationMs);
            result.put("batchUpdateDurationMs", batchUpdateDurationMs);
            return result;
        }
    }

    /**
     * 同步耗时指标采集 — 统计拉取/转换/落库各阶段耗时，用于性能分析。
     * <p>
     * 拉取线程写入 fetch* 字段，落库线程写入 transform/save* 字段，
     * 各字段只有单一线程写入，无需加锁。</p>
     */
    @Getter
    public static class SyncMetrics {
        // ====== 拉取阶段（fetch 线程写入） ======
        private long fetchDurationMs;
        private int fetchPageCount;
        private long maxFetchPageMs;

        // ====== 转换阶段（insert 线程写入） ======
        private long transformDurationMs;
        private long idGenDurationMs;        // Leaf ID 生成总耗时
        private long lastIdGenMs;            // 当批 ID 生成耗时
        private int lastIdGenCount;          // 当批生成 ID 数量

        // ====== 落库阶段（insert 线程写入） ======
        private long saveDurationMs;
        private int saveBatchCount;
        private long maxSaveBatchMs;

        // ====== saveBatch 内部子步骤（累计值，用于 sync_business_record） ======
        private long cacheLookupDurationMs;
        private long batchInsertDurationMs;
        private long globalIdQueryDurationMs;
        private long batchUpdateDurationMs;

        // ====== saveBatch 内部子步骤（当批值，用于 sync_batch_metrics） ======
        private long lastCacheLookupMs;
        private long lastSplitCheckMs;        // 拆分 toInsert/toUpdate 耗时
        private int lastInsertCount;
        private long lastBatchInsertMs;
        private long lastCacheAddMs;          // addNewIds 写入缓存耗时
        private long lastGlobalIdQueryMs;     // 查 globalId 耗时
        private long lastSetIdMs;             // 设置 globalId 耗时
        private int lastUpdateCount;
        private long lastBatchUpdateMs;

        public void recordFetchPage(long elapsedMs) {
            fetchDurationMs += elapsedMs;
            fetchPageCount++;
            if (elapsedMs > maxFetchPageMs) maxFetchPageMs = elapsedMs;
        }

        public void recordTransform(long elapsedMs) {
            transformDurationMs += elapsedMs;
        }

        public void recordIdGen(long elapsedMs, int count) {
            idGenDurationMs += elapsedMs;
            lastIdGenMs = elapsedMs;
            lastIdGenCount = count;
        }

        public void recordSaveBatch(long elapsedMs) {
            saveDurationMs += elapsedMs;
            saveBatchCount++;
            if (elapsedMs > maxSaveBatchMs) maxSaveBatchMs = elapsedMs;
        }

        public void recordCacheLookup(long elapsedMs) {
            cacheLookupDurationMs += elapsedMs;
            lastCacheLookupMs = elapsedMs;
        }

        public void recordSplitCheck(long elapsedMs) {
            lastSplitCheckMs = elapsedMs;
        }

        public void recordBatchInsert(long elapsedMs, long cacheAddMs, int insertCount) {
            batchInsertDurationMs += elapsedMs;
            lastBatchInsertMs = elapsedMs;
            lastCacheAddMs = cacheAddMs;
            lastInsertCount = insertCount;
        }

        public void recordGlobalIdQuery(long elapsedMs) {
            globalIdQueryDurationMs += elapsedMs;
            lastGlobalIdQueryMs = elapsedMs;
        }

        public void recordBatchUpdate(long queryIdMs, long setIdMs, long updateMs, int updateCount) {
            globalIdQueryDurationMs += queryIdMs;
            batchUpdateDurationMs += updateMs;
            lastGlobalIdQueryMs = queryIdMs;
            lastSetIdMs = setIdMs;
            lastBatchUpdateMs = updateMs;
            lastUpdateCount = updateCount;
        }

        /** 重置当批子步骤值（每批 saveBatch 前调用） */
        public void resetBatchSubTimings() {
            lastCacheLookupMs = 0;
            lastSplitCheckMs = 0;
            lastInsertCount = 0;
            lastBatchInsertMs = 0;
            lastCacheAddMs = 0;
            lastGlobalIdQueryMs = 0;
            lastSetIdMs = 0;
            lastUpdateCount = 0;
            lastBatchUpdateMs = 0;
            lastIdGenMs = 0;
            lastIdGenCount = 0;
        }

        public Double avgFetchPageMs() {
            return fetchPageCount > 0 ? (double) fetchDurationMs / fetchPageCount : 0;
        }

        public Double avgSaveBatchMs() {
            return saveBatchCount > 0 ? (double) saveDurationMs / saveBatchCount : 0;
        }

        public long totalDurationMs() {
            return fetchDurationMs + transformDurationMs + saveDurationMs;
        }
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
