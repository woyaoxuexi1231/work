package com.riskdatahub.sync.model;

import lombok.AllArgsConstructor;
import lombok.Data;

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

        /**
         * 构造包含数据的页块。
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
            return result;
        }
    }

    /**
     * 线程安全的同步计数器。
     * <p>
     * 运行在多线程环境下（拉取线程写入 pulled，落库线程写入 saved），
     * 累加操作发生在各自单一线程内，无需加锁。
     * </p>
     */
    public static class SyncCounter {
        private int pageCount;
        private int pulledCount;
        private int savedCount;
        private long lastRowId;

        public int getPageCount() { return pageCount; }

        public void setPageCount(int pageCount) { this.pageCount = pageCount; }

        public int getPulledCount() { return pulledCount; }

        public void addPulledCount(int pulledCount) { this.pulledCount += pulledCount; }

        public int getSavedCount() { return savedCount; }

        public void incrementSavedCount() { this.savedCount++; }

        public long getLastRowId() { return lastRowId; }

        public void setLastRowId(long lastRowId) { this.lastRowId = lastRowId; }
    }
}
