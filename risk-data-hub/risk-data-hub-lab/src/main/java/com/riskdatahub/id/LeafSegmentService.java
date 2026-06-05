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
     * 获取下一个全局唯一 ID（核心入口）。
     * <p>流程：惰性初始化号段 → synchronized 线程安全发放 → 号段耗尽自动切换 → 检查水位线触发预加载。</p>
     *
     * @param tag 业务标签（如 sync_task、sync_business_record）
     * @return 全局唯一 ID
     */
    public long nextId(String tag) {
        // 每个 tag 首次访问时自动创建 SegmentBuffer
        SegmentBuffer buffer = buffers.computeIfAbsent(tag, key -> new SegmentBuffer());
        // synchronized 保证同一 tag 的号段发放线程安全
        synchronized (buffer) {
            // 当前号段没有剩余 ID 时，自动切换（优先用预加载的 next，还是从 DB 申请）
            if (!buffer.current.hasNext()) {
                switchSegment(buffer, tag);
            }
            // 从当前号段中取下一个 ID，原子递增（nextId 会自增到 end 为止）
            long id = buffer.current.nextId++;
            // 检查是否需要异步预加载下一段（剩余不足 20% 时触发）
            triggerPreloadIfNeeded(tag, buffer);
            return id;
        }
    }

    /**
     * 批量获取 ID — 单个 synchronized 块内连续分配，避免逐次锁竞争。
     * <p>比循环调用 {@link #nextId(String)} 性能高一个数量级（减少 synchronized 进入/退出次数）。</p>
     *
     * @param tag   业务标签
     * @param count 需要的 ID 数量
     * @return ID 数组（长度 = count）
     */
    public long[] nextIdBatch(String tag, int count) {
        if (count <= 0) return new long[0];
        long[] ids = new long[count];
        SegmentBuffer buffer = buffers.computeIfAbsent(tag, key -> new SegmentBuffer());
        synchronized (buffer) {
            for (int i = 0; i < count; i++) {
                if (!buffer.current.hasNext()) {
                    switchSegment(buffer, tag);
                }
                ids[i] = buffer.current.nextId++;
                // 只在最后一次检查水位线，避免频繁触发
                if (i == count - 1) {
                    triggerPreloadIfNeeded(tag, buffer);
                }
            }
        }
        return ids;
    }

    /**
     * 批量获取 ID（供测试 / 管理接口使用）。
     * <p>循环调用 nextId，内部已做线程安全处理。</p>
     *
     * @param tag   业务标签
     * @param count 需要的 ID 数量
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
     * 查询双缓冲状态（监控用）。
     * <p>返回 current 和 next 两个号段的 start / nextId / end 等信息。</p>
     *
     * @param tag 业务标签
     * @return 双缓冲各字段的 Map
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
     * 清空本地缓存（数据初始化后调用）。
     * <p>确保旧的号段不会继续发放，重建时从数据库重新获取。</p>
     */
    public void clearLocalCache() {
        buffers.clear();
    }

    /**
     * 号段切换（内部方法）。
     * <p>优先切换到预加载好的 next（零等待），没有则回源数据库申请。</p>
     *
     * @param buffer 双缓冲容器
     * @param tag    业务标签
     */
    private void switchSegment(SegmentBuffer buffer, String tag) {
        // 有预加载好的 next 号段 → 直接切换（纯内存操作）
        if (buffer.next.ready) {
            log.debug("[Leaf] tag={} 当前号段耗尽，切换到预加载 next buffer: {}-{}", tag, buffer.next.start, buffer.next.end);
            buffer.current = buffer.next.copy();
            buffer.next = Segment.empty();
            return;
        }
        // 没有预加载好的 next → 回源数据库申请（悲观锁，可能慢）
        log.debug("[Leaf] tag={} 当前无 next buffer，回源数据库申请新号段", tag);
        Segment fresh = fetchSegment(tag);
        buffer.current = fresh;
        buffer.next = Segment.empty();
    }

    /**
     * 水位线检查 &amp; 预加载触发。
     * <p>当前号段剩余不足 20% 时，异步提交到单线程池申请下一段，
     * 防止高并发下号段耗尽时所有线程都去抢数据库。</p>
     *
     * @param tag    业务标签
     * @param buffer 双缓冲容器
     */
    private void triggerPreloadIfNeeded(String tag, SegmentBuffer buffer) {
        long remaining = buffer.current.end - buffer.current.nextId + 1;
        long total = Math.max(1, buffer.current.end - buffer.current.start + 1);
        boolean lowWaterMark = remaining * 100 / total <= 20;
        // 不满足触发条件，或已在加载中，或 next 已就绪 → 跳过
        if (!lowWaterMark || buffer.loadingNext || buffer.next.ready) {
            return;
        }
        log.debug("[Leaf] tag={} 当前号段剩余不足 20%，异步预加载下一段", tag);
        buffer.loadingNext = true;
        // 提交到单线程池异步执行，不阻塞当前 ID 发放
        preloadExecutor.submit(() -> {
            Segment next = fetchSegment(tag);
            synchronized (buffer) {
                buffer.next = next;
                buffer.loadingNext = false;
                log.debug("[Leaf] tag={} next buffer 预加载完成: {}-{}", tag, next.start, next.end);
            }
        });
    }

    /**
     * 从数据库申请新号段。
     * <p>流程：SELECT ... FOR UPDATE 悲观锁 → 读取 max_id → 增加步长 → 更新 → 返回号段。
     * 事务由 TransactionTemplate 管理，数据源切换到中台库。</p>
     *
     * @param tag 业务标签
     * @return 新申请的号段
     */
    private Segment fetchSegment(String tag) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        // 中台库数据源下执行，使用悲观锁防止多实例竞争
        return routingMybatisExecutor.query(HubConstants.DS_HUB, () ->
                transactionTemplate.execute(status -> {
                    // 悲观锁查询：锁定对应 biz_tag 的行，其他实例会等待
                    LeafAlloc alloc = leafAllocMapper.selectForUpdate(tag);
                    if (alloc == null) {
                        throw new IllegalArgumentException("Leaf 发号器标签不存在: " + tag);
                    }
                    // 计算新号段：从 oldMax+1 到 oldMax+step
                    long oldMax = alloc.getMaxId();
                    int step = alloc.getStep();
                    long newMax = oldMax + step;
                    // 更新数据库中的 max_id（追加步长），以便下次申请时从新位置开始
                    alloc.setMaxId(newMax);
                    leafAllocMapper.updateById(alloc);
                    log.debug("[Leaf] tag={} 从数据库申请新号段: {}-{}，步长={}", tag, oldMax + 1, newMax, step);
                    // 返回新号段：start=oldMax+1, nextId=oldMax+1（还没发）, end=newMax
                    return new Segment(oldMax + 1, oldMax + 1, newMax, true);
                }));
    }

    /**
     * 号段缓冲区 — 双缓冲容器。
     * <p>current = 当前发放号段，next = 预加载号段。</p>
     */
    private static final class SegmentBuffer {
        private Segment current = Segment.empty();
        private Segment next = Segment.empty();
        /** 是否正在异步预加载下一段 */
        private boolean loadingNext;
    }

    /**
     * 号段 — 表示 [start, end] 范围内的连续 ID。
     * <p>nextId 是当前已发放到的位置。</p>
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

        /** 空号段哨兵 — ready=false，hasNext() 永远返回 false */
        private static Segment empty() {
            return new Segment(0, 1, 0, false);
        }

        /** 号段是否有剩余 ID */
        private boolean hasNext() {
            return ready && nextId <= end;
        }

        /** 创建号段副本，用于 current 切换（避免引用共享） */
        private Segment copy() {
            return new Segment(start, nextId, end, ready);
        }
    }
}
