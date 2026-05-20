package com.example.dynamicds.sync;

import lombok.Builder;
import lombok.Getter;

/**
 * 同步上下文 — 不可变对象（Immutable Object），携带当前同步任务的参数。
 * <p>
 * <b>为什么用 @Builder + 所有字段 final？</b>
 * <ol>
 *   <li><b>不可变（Immutable）</b> — 一旦创建，字段值不再改变。
 *       在多线程环境下（TradeEtlService 并发派发 4 个 Future），
 *       每个线程读到的是同一份上下文，不会出现某个线程修改了 pageSize 导致其他线程行为变化。</li>
 *   <li><b>Builder 模式 vs 构造器</b> — 4 个字段用构造器容易搞混顺序
 *       （哪个是 pageSize 哪个是 batchNo？），Builder 显式命名每个字段，
 *       代码可读性更好：{@code BusinessSyncContext.builder().dataSourceKey("trade_oms").pageSize(100).build()}</li>
 *   <li><b>没有 setter → 不可变 → 线程安全</b></li>
 * </ol>
 * <p>
 * <b>为什么用一个对象封装而不是散装参数？</b><br>
 * 抽象模板有 5 个抽象方法（fetchPage / transform / save / ...），
 * 每个方法都需要 dataSourceKey、datasourceType、batchNo 这几个参数。
 * 如果散装传递，每个抽象方法都要写 {@code (String dataSourceKey, String datasourceType, int pageSize, String batchNo)}，
 * 参数列表过长且容易传错。封装成 BusinessSyncContext 后，每个抽象方法只传一个参数即可。
 */
@Getter
@Builder
public class BusinessSyncContext {

    /** 数据源 key（如 trade_oms / trade_broker） */
    private final String dataSourceKey;
    /** 数据源类型（TRADE_OMS / TRADE_BROKER） */
    private final String datasourceType;
    /** 每页拉取行数 */
    private final int pageSize;
    /** 同步批次号（SYNC-时间戳） */
    private final String batchNo;
}
