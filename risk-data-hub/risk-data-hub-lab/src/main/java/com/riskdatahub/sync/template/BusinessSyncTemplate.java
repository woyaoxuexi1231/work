package com.riskdatahub.sync.template;

import com.riskdatahub.sync.SyncEngine;
import com.riskdatahub.sync.model.BusinessSyncContext;
import com.riskdatahub.sync.model.SyncSupport.BusinessSyncResult;

/**
 * 业务同步模板接口 — 策略模式的核心接口。
 * <p>
 * 不同业务类型（股票/交易/持仓/资金）实现此接口，通过 Spring 自动注入到
 * {@link SyncEngine#businessSyncTemplates} 列表中。
 * 新增一种业务同步时，只需新建一个 {@code @Service} 实现类，无需修改编排代码（开闭原则）。
 * </p>
 *
 * @author risk-data-hub
 */
public interface BusinessSyncTemplate {

    /**
     * 获取业务编码（如 STOCK / TRADE / POSITION / ASSET）。
     *
     * @return 业务编码
     */
    String businessCode();

    /**
     * 执行同步流程：拉取 → 转换 → 落库。
     *
     * @param context 同步上下文
     * @return 同步结果汇总
     * @throws Exception 同步过程中任意异常
     */
    BusinessSyncResult execute(BusinessSyncContext context) throws Exception;
}
