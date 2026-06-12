package com.demo.jdk17.controller;

import com.demo.jdk17.service.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JDK 17 所有新特性的统一演示入口
 *
 * GET /api/record              → Record
 * GET /api/sealed              → Sealed Interface
 * GET /api/instanceof          → instanceof 模式匹配
 * GET /api/switch-expr         → Switch 表达式 + 模式匹配
 * GET /api/text-block          → Text Blocks
 * GET /api/var                 → var + 集合工厂 + Stream.toList()
 * GET /api/string-optional     → String 新方法 + Optional 增强
 * GET /api/http-client         → HTTP Client
 * GET /api/npe                 → NPE 增强
 * GET /api/features            → 查看所有可用端点
 */
@RestController
@RequestMapping("/api")
public class Jdk17FeatureController {

    private final RecordDemoService recordService;
    private final SealedDemoService sealedService;
    private final InstanceofDemoService instanceofService;
    private final SwitchDemoService switchService;
    private final TextBlockDemoService textBlockService;
    private final VarCollectionDemoService varCollectionService;
    private final StringOptionalDemoService stringOptionalService;
    private final HttpClientDemoService httpClientService;
    private final NpeDemoService npeService;

    public Jdk17FeatureController(
            RecordDemoService recordService,
            SealedDemoService sealedService,
            InstanceofDemoService instanceofService,
            SwitchDemoService switchService,
            TextBlockDemoService textBlockService,
            VarCollectionDemoService varCollectionService,
            StringOptionalDemoService stringOptionalService,
            HttpClientDemoService httpClientService,
            NpeDemoService npeService) {
        this.recordService = recordService;
        this.sealedService = sealedService;
        this.instanceofService = instanceofService;
        this.switchService = switchService;
        this.textBlockService = textBlockService;
        this.varCollectionService = varCollectionService;
        this.stringOptionalService = stringOptionalService;
        this.httpClientService = httpClientService;
        this.npeService = npeService;
    }

    @GetMapping("/record")
    public Map<String, Object> record() {
        return recordService.demo();
    }

    @GetMapping("/sealed")
    public Map<String, Object> sealed() {
        return sealedService.demo();
    }

    @GetMapping("/instanceof")
    public Map<String, Object> instanceofDemo() {
        return instanceofService.demo();
    }

    @GetMapping("/switch-expr")
    public Map<String, Object> switchExpr() {
        return switchService.demo();
    }

    @GetMapping("/text-block")
    public Map<String, Object> textBlock() {
        return textBlockService.demo();
    }

    @GetMapping("/var")
    public Map<String, Object> varDemo() {
        return varCollectionService.demo();
    }

    @GetMapping("/string-optional")
    public Map<String, Object> stringOptional() {
        return stringOptionalService.demo();
    }

    @GetMapping("/http-client")
    public Map<String, Object> httpClient() {
        return httpClientService.demo();
    }

    @GetMapping("/npe")
    public Map<String, Object> npe() {
        return npeService.demo();
    }

    @GetMapping("/features")
    public Map<String, String> listFeatures() {
        var features = new LinkedHashMap<String, String>();
        features.put("GET /api/record", "Record：不可变数据载体 + 紧凑构造器");
        features.put("GET /api/sealed", "Sealed Interface：permits 限定实现类 + switch 穷举");
        features.put("GET /api/instanceof", "instanceof 模式匹配：判断+绑定一步完成");
        features.put("GET /api/switch-expr", "Switch 表达式 + 模式匹配（预览）");
        features.put("GET /api/text-block", "Text Blocks：多行字符串 \"\"\"...\"\"\"");
        features.put("GET /api/var", "var + List.of() + Stream.toList()");
        features.put("GET /api/string-optional", "String 新方法 + Optional 增强");
        features.put("GET /api/http-client", "JDK 11 HTTP Client（同步/异步）");
        features.put("GET /api/npe", "NPE 增强：精准指出 null 变量名");
        return features;
    }
}
