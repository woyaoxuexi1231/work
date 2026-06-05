package com.riskdatahub.common.constant;

/**
 * 平台常量定义 — 集中管理所有数据源标识和类型枚举。
 * <p>
 * 所有引用数据源 key 或数据源类型的地方必须通过此类引用，
 * 禁止在业务代码中硬编码字符串常量。
 * </p>
 *
 * @author risk-data-hub
 */
public final class HubConstants {

    private HubConstants() {
    }

    /** 中台库（risk_hub）数据源 key */
    public static final String DS_HUB = "risk_hub";

    /** OMS 交易系统数据源 key */
    public static final String DS_TRADE_OMS = "trade_oms";

    /** Broker 券商系统数据源 key */
    public static final String DS_TRADE_BROKER = "trade_broker";

    /** 中台库类型标识 */
    public static final String TYPE_HUB = "HUB";

    /** OMS 交易系统类型标识 */
    public static final String TYPE_TRADE_OMS = "TRADE_OMS";

    /** Broker 券商系统类型标识 */
    public static final String TYPE_TRADE_BROKER = "TRADE_BROKER";
}
