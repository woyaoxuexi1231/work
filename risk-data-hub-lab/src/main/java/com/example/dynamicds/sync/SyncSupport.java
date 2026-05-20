package com.example.dynamicds.sync;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步框架的通用类型定义容器。
 * <p>
 * <b>设计要点：</b>
 * <ul>
 *   <li><b>final + 私有构造器</b> — 纯工具类（Utility Class Pattern），
 *       禁止实例化和继承。类存在的唯一意义是将 {@link SyncSupport.PageChunk}、
 *       {@link SyncSupport.SyncProgress}、{@link SyncSupport.SyncCounter}、
 *       {@link SyncSupport.SyncProgressListener} 这些紧耦合的类型收拢在
 *       一个命名空间下，避免在 {@code sync} 包下散落大量零散文件。</li>
 *   <li><b>内部类全部 static</b> — 它们只是"位于 SyncSupport 命名空间内的独立类型"，
 *       不需要持有外部类引用。如果漏写 {@code static}，每个实例会隐式携带一个
 *       指向 {@code null} 的外围引用，浪费内存且可能误导 GC。</li>
 * </ul>
 */
public final class SyncSupport {

    private SyncSupport() {
    }

    /**
     * 分页数据块 — 在拉取线程和落库线程之间传递的"页"单位。
     * <p>
     * <b>设计亮点：</b>
     * <ul>
     *   <li><b>end() 哨兵模式</b> — 用 {@code end=true} 的对象替代 {@code null}
     *       来标记数据结束。消费者只需 {@code if (chunk.isEnd()) break} 即可，
     *       无需对 {@code null} 做防御性判断。</li>
     *   <li><b>泛型 {@code <S>}</b> — 允许不同业务（{@code StockRow / TradeRow / PositionRow / AssetRow}）
     *       复用同一套队列逻辑，避免为每种业务类型写一个 PageChunk。</li>
     * </ul>
     */
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

    /**
     * 单个业务类型的同步结果汇总。
     * <p>
     * <b>设计说明：</b>
     * <ul>
     *   <li><b>{@code toMap()} 使用 {@link java.util.LinkedHashMap}</b> —
     *       保证 JSON 序列化时字段顺序固定（{@code pageCount → pulledCount → savedCount → lastRowId}），
     *       便于前端和日志查看。</li>
     *   <li><b>值对象（Value Object）</b> — 通过构造器一次性赋值，没有 setter，不可变。</li>
     * </ul>
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
            result.put("pageCount", pageCount);
            result.put("pulledCount", pulledCount);
            result.put("savedCount", savedCount);
            result.put("lastRowId", lastRowId);
            return result;
        }
    }

    /**
     * 进度快照 — 拉取线程或落库线程每完成一页就回调一次。
     * <p>
     * <b>设计说明：</b>
     * <ul>
     *   <li>与 {@link BusinessSyncResult} 结构类似但多了 {@code stage}
     *       （{@code FETCHED / SAVED}）和 {@code pageNo}，目的是让前端能实时看到
     *       "拉到第几页了、落了几条了"。</li>
     *   <li>{@code SyncTaskService} 中的 {@code updateProgress()} 就是依赖这个回调
     *       来更新前端轮询接口的。</li>
     * </ul>
     */
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

    /**
     * 观察者接口 — 同步模板每完成一页数据就回调一次。
     * <p>
     * {@link SyncTaskService} 实现此接口，将进度刷新到前端可轮询的快照中。
     * 这是典型的 <b>Observer 模式</b>，解耦了"同步引擎"和"任务状态追踪"。
     */
    public interface SyncProgressListener {
        void onProgress(SyncProgress progress);
    }

    /**
     * 线程安全的计数器 — 运行在多线程环境下（拉取线程写入 pulled，落库线程写入 saved）。
     * <p>
     * <b>为什么不用 Lombok {@code @Data}？</b>
     * <br>
     * {@code @Data} 生成的 {@code setSavedCount(int)} 会替换值，但我们需要的是
     * {@code incrementSavedCount()}（累加）和 {@code addPulledCount()}（累加）。
     * 手写这些方法可以精确控制"哪些操作允许累加、哪些操作允许替换"，
     * 避免调用方误用 {@code setPulledCount()} 覆盖已累加的值。
     * <p>
     * <b>注意：</b>这些累加操作发生在单一线程内（拉取/落库各自是单线程），所以不需要加锁。
     */
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
