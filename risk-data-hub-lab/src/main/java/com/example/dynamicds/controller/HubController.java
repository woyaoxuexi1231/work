package com.example.dynamicds.controller;

import com.example.dynamicds.dto.ApiResult;
import com.example.dynamicds.service.DictionaryService;
import com.example.dynamicds.service.DualDataSourceTxService;
import com.example.dynamicds.service.LeafSegmentService;
import com.example.dynamicds.service.MessageOutboxService;
import com.example.dynamicds.service.OverviewService;
import com.example.dynamicds.service.PlatformBootstrapService;
import com.example.dynamicds.service.TradeEtlService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/hub")
public class HubController {

    private final OverviewService overviewService;
    private final PlatformBootstrapService bootstrapService;
    private final TradeEtlService tradeEtlService;
    private final DualDataSourceTxService dualDataSourceTxService;
    private final LeafSegmentService leafSegmentService;
    private final DictionaryService dictionaryService;
    private final MessageOutboxService messageOutboxService;

    public HubController(OverviewService overviewService,
                         PlatformBootstrapService bootstrapService,
                         TradeEtlService tradeEtlService,
                         DualDataSourceTxService dualDataSourceTxService,
                         LeafSegmentService leafSegmentService,
                         DictionaryService dictionaryService,
                         MessageOutboxService messageOutboxService) {
        this.overviewService = overviewService;
        this.bootstrapService = bootstrapService;
        this.tradeEtlService = tradeEtlService;
        this.dualDataSourceTxService = dualDataSourceTxService;
        this.leafSegmentService = leafSegmentService;
        this.dictionaryService = dictionaryService;
        this.messageOutboxService = messageOutboxService;
    }

    @GetMapping("/overview")
    public ApiResult<Map<String, Object>> overview() {
        return ApiResult.ok(overviewService.overview());
    }

    @PostMapping("/reset")
    public ApiResult<Void> reset() {
        bootstrapService.resetDemoData();
        return ApiResult.ok();
    }

    @PostMapping("/etl/init")
    public ApiResult<Map<String, Object>> initClean() {
        return ApiResult.ok(tradeEtlService.runBootstrapClean());
    }

    @PostMapping("/etl/realtime")
    public ApiResult<Map<String, Object>> realtime(@RequestParam String systemKey) {
        return ApiResult.ok(tradeEtlService.runRealtimeClean(systemKey));
    }

    @GetMapping("/cleaned-trades")
    public ApiResult<?> cleanedTrades() {
        return ApiResult.ok(tradeEtlService.cleanedTrades());
    }

    @PostMapping("/tx/coordinate")
    public ApiResult<Map<String, Object>> coordinate(@RequestParam String sourceSystem,
                                                     @RequestParam(defaultValue = "false") boolean simulateFailure) throws InterruptedException {
        return ApiResult.ok(dualDataSourceTxService.runCoordinatedWrite(sourceSystem, simulateFailure));
    }

    @PostMapping("/id/next")
    public ApiResult<Map<String, Object>> nextIds(@RequestParam String tag,
                                                  @RequestParam(defaultValue = "10") int count) {
        return ApiResult.ok(leafSegmentService.nextIds(tag, count));
    }

    @GetMapping("/id/state")
    public ApiResult<Map<String, Object>> idState(@RequestParam String tag) {
        return ApiResult.ok(leafSegmentService.state(tag));
    }

    @GetMapping("/dict")
    public ApiResult<?> dict() {
        return ApiResult.ok(dictionaryService.listAll());
    }

    @PostMapping("/dict")
    public ApiResult<Void> saveDict(@RequestBody Map<String, String> body) {
        dictionaryService.save(
                body.get("dictType"),
                body.get("dictCode"),
                body.get("dictName"),
                body.getOrDefault("dictDesc", "")
        );
        return ApiResult.ok();
    }

    @GetMapping("/messages")
    public ApiResult<?> messages() {
        return ApiResult.ok(messageOutboxService.recentMessages());
    }
}
