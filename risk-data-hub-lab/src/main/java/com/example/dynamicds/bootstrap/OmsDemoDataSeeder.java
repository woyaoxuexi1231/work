package com.example.dynamicds.bootstrap;

import com.example.dynamicds.entity.OmsCashAsset;
import com.example.dynamicds.entity.OmsPositionHolding;
import com.example.dynamicds.entity.OmsStockSnapshot;
import com.example.dynamicds.entity.OmsTradeOrder;
import com.example.dynamicds.mapper.OmsCashAssetMapper;
import com.example.dynamicds.mapper.OmsPositionHoldingMapper;
import com.example.dynamicds.mapper.OmsStockSnapshotMapper;
import com.example.dynamicds.mapper.OmsTradeOrderMapper;
import com.example.dynamicds.service.LeafSegmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OmsDemoDataSeeder {

    private static final String TAG_OMS_SNAPSHOT = "oms_stock_snapshot";
    private static final String TAG_OMS_ORDER = "oms_trade_order";
    private static final String TAG_OMS_POSITION = "oms_position_holding";
    private static final String TAG_OMS_CASH = "oms_cash_asset";
    private static final List<String> OMS_STATUSES = List.of("NEW", "DONE", "CANCEL");
    private static final List<String> OMS_ACCOUNTS = List.of("量化一号", "量化二号", "多因子策略", "中性策略", "高频策略");
    private static final int OMS_ORDER_REPEAT = 3;

    private final LeafSegmentService leafSegmentService;
    private final OmsStockSnapshotMapper omsStockSnapshotMapper;
    private final OmsTradeOrderMapper omsTradeOrderMapper;
    private final OmsPositionHoldingMapper omsPositionHoldingMapper;
    private final OmsCashAssetMapper omsCashAssetMapper;

    public void seed(MarketSeedSnapshot snapshot, int index) {
        omsStockSnapshotMapper.insert(OmsStockSnapshot.fromSeed(
                leafSegmentService.nextId(TAG_OMS_SNAPSHOT), snapshot));

        for (int repeat = 0; repeat < OMS_ORDER_REPEAT; repeat++) {
            omsTradeOrderMapper.insert(OmsTradeOrder.fromSeed(
                    leafSegmentService.nextId(TAG_OMS_ORDER),
                    snapshot,
                    OMS_ACCOUNTS.get(repeat % OMS_ACCOUNTS.size()),
                    repeat % 2 == 0 ? "S" : "B",
                    OMS_STATUSES.get(repeat % OMS_STATUSES.size()),
                    repeat));
        }

        omsPositionHoldingMapper.insert(OmsPositionHolding.fromSeed(
                leafSegmentService.nextId(TAG_OMS_POSITION), snapshot, OMS_ACCOUNTS.get(0)));

        if (index % 3 == 0) {
            omsCashAssetMapper.insert(OmsCashAsset.fromSeed(
                    leafSegmentService.nextId(TAG_OMS_CASH), snapshot, OMS_ACCOUNTS.get(0), "OMS-ACCT-0001"));
        }
    }
}
