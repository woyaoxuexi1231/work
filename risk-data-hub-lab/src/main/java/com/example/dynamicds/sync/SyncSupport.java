package com.example.dynamicds.sync;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SyncSupport {

    private SyncSupport() {
    }

    @Data
    @AllArgsConstructor
    public static class PageChunk<S> {
        private int pageNo;
        private List<S> rows;
        private boolean end;

        public static <S> PageChunk<S> data(int pageNo, List<S> rows) {
            return new PageChunk<>(pageNo, rows, false);
        }

        public static <S> PageChunk<S> end() {
            return new PageChunk<>(0, List.of(), true);
        }
    }

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
            result.put("pageCount", pageCount);
            result.put("pulledCount", pulledCount);
            result.put("savedCount", savedCount);
            result.put("lastRowId", lastRowId);
            return result;
        }
    }

    @Data
    @AllArgsConstructor
    public static class SyncProgress {
        private String businessCode;
        private String stage;
        private int pageNo;
        private int pulledCount;
        private int savedCount;
        private long lastRowId;

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("stage", stage);
            result.put("pageNo", pageNo);
            result.put("pulledCount", pulledCount);
            result.put("savedCount", savedCount);
            result.put("lastRowId", lastRowId);
            return result;
        }
    }

    public interface SyncProgressListener {
        void onProgress(SyncProgress progress);
    }

    public static class SyncCounter {
        private int pageCount;
        private int pulledCount;
        private int savedCount;
        private long lastRowId;

        public int getPageCount() {
            return pageCount;
        }

        public void setPageCount(int pageCount) {
            this.pageCount = pageCount;
        }

        public int getPulledCount() {
            return pulledCount;
        }

        public void addPulledCount(int pulledCount) {
            this.pulledCount += pulledCount;
        }

        public int getSavedCount() {
            return savedCount;
        }

        public void incrementSavedCount() {
            this.savedCount++;
        }

        public long getLastRowId() {
            return lastRowId;
        }

        public void setLastRowId(long lastRowId) {
            this.lastRowId = lastRowId;
        }
    }
}
