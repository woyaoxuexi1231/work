package com.example.dynamicds.bootstrap;

import com.example.dynamicds.entity.BrokerFundAccount;
import com.example.dynamicds.entity.BrokerPositionBalance;
import com.example.dynamicds.entity.BrokerStockQuote;
import com.example.dynamicds.entity.BrokerTradeDeal;
import com.example.dynamicds.mapper.BrokerFundAccountMapper;
import com.example.dynamicds.mapper.BrokerPositionBalanceMapper;
import com.example.dynamicds.mapper.BrokerStockQuoteMapper;
import com.example.dynamicds.mapper.BrokerTradeDealMapper;
import com.example.dynamicds.service.LeafSegmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BrokerDemoDataSeeder {

    private static final String TAG_BROKER_QUOTE = "broker_stock_quote";
    private static final String TAG_BROKER_DEAL = "broker_trade_deal";
    private static final String TAG_BROKER_POSITION = "broker_position_balance";
    private static final String TAG_BROKER_FUND = "broker_fund_account";
    private static final List<String> BROKER_STATUSES = List.of("A", "S", "X");
    private static final List<String> BROKER_CLIENTS = List.of("华泰资管账户", "中信机构账户", "国君量化账户", "招商自营账户", "东方财富量化户");
    private static final int BROKER_DEAL_REPEAT = 4;

    private final LeafSegmentService leafSegmentService;
    private final BrokerStockQuoteMapper brokerStockQuoteMapper;
    private final BrokerTradeDealMapper brokerTradeDealMapper;
    private final BrokerPositionBalanceMapper brokerPositionBalanceMapper;
    private final BrokerFundAccountMapper brokerFundAccountMapper;

    public void seed(MarketSeedSnapshot snapshot, int index) {
        brokerStockQuoteMapper.insert(BrokerStockQuote.fromSeed(
                leafSegmentService.nextId(TAG_BROKER_QUOTE), snapshot));

        for (int repeat = 0; repeat < BROKER_DEAL_REPEAT; repeat++) {
            brokerTradeDealMapper.insert(BrokerTradeDeal.fromSeed(
                    leafSegmentService.nextId(TAG_BROKER_DEAL),
                    snapshot,
                    BROKER_CLIENTS.get(repeat % BROKER_CLIENTS.size()),
                    repeat % 2 == 0 ? "2" : "1",
                    BROKER_STATUSES.get(repeat % BROKER_STATUSES.size()),
                    repeat));
        }

        brokerPositionBalanceMapper.insert(BrokerPositionBalance.fromSeed(
                leafSegmentService.nextId(TAG_BROKER_POSITION), snapshot, BROKER_CLIENTS.get(0)));

        if (index % 3 == 0) {
            brokerFundAccountMapper.insert(BrokerFundAccount.fromSeed(
                    leafSegmentService.nextId(TAG_BROKER_FUND), snapshot, BROKER_CLIENTS.get(0), "BRK-ACCT-0001"));
        }
    }
}
