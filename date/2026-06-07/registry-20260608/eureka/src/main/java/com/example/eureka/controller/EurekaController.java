package com.example.eureka.controller;

import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Eureka AP (自我保护模式) 测试 Controller
 *
 * ========== 核心原理 ==========
 * Eureka Server 每分钟统计心跳续约数:
 *   预期续约数 = 注册实例数 × 2 (每个实例每30s发一次心跳)
 *   实际续约数 = 实际收到的心跳次数
 *   当 实际续约数 < 预期续约数 × 0.85 → 触发自我保护
 *
 * ========== 怎么触发 ==========
 * 停 Eureka Server 节点 → 不会触发 (客户端还在给其他节点发心跳)
 * 正确方式: 注册大量"假实例"，只注册不发心跳 → 续约率暴跌 → 触发保护
 *
 * ========== 测试流程 ==========
 * 1. POST /test/fake-register?count=10 → 注册10个假实例 (不发心跳)
 * 2. 等待 ~2 分钟
 * 3. 打开 Dashboard → 看到红色 EMERGENCY 告警 = 自我保护已触发
 * 4. GET /test/fake-status → 假实例仍在 = 证明保护生效
 * 5. 对比: 关闭自我保护后重复测试 → 假实例被剔除
 */
@RestController
public class EurekaController {

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private EurekaClient eurekaClient;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${eureka.client.service-url.defaultZone:http://localhost:8761/eureka/}")
    private String eurekaServerUrl;

    // ==================== 基础查询 ====================

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> result = new HashMap<>();
        result.put("application", applicationName);
        result.put("registry", "Eureka (Cluster Mode, AP)");
        result.put("instances", discoveryClient.getInstances(applicationName).stream()
                .map(this::instanceToMap).collect(Collectors.toList()));
        return result;
    }

    @GetMapping("/services")
    public Map<String, Object> listServices() {
        Map<String, Object> result = new HashMap<>();
        result.put("services", discoveryClient.getServices());
        return result;
    }

    @GetMapping("/services/{serviceName}")
    public Map<String, Object> getServiceInstances(@PathVariable String serviceName) {
        Map<String, Object> result = new HashMap<>();
        result.put("service", serviceName);
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        result.put("count", instances.size());
        result.put("instances", instances.stream().map(this::instanceToMap).collect(Collectors.toList()));
        return result;
    }

    @GetMapping("/eureka/app/{appName}")
    public Map<String, Object> eurekaAppInfo(@PathVariable String appName) {
        Map<String, Object> result = new HashMap<>();
        Application app = eurekaClient.getApplication(appName);
        if (app != null) {
            result.put("name", app.getName());
            result.put("instances", app.getInstances().stream().map(inst -> {
                Map<String, Object> m = new HashMap<>();
                m.put("instanceId", inst.getInstanceId());
                m.put("hostName", inst.getHostName());
                m.put("ipAddr", inst.getIPAddr());
                m.put("port", inst.getPort());
                m.put("status", inst.getStatus().name());
                return m;
            }).collect(Collectors.toList()));
        } else {
            result.put("message", "Application not found: " + appName);
        }
        return result;
    }

    // ==================== 自我保护测试: 批量假注册 ====================

    /**
     * 批量注册假实例到 Eureka Server
     * 这些实例只注册，不发心跳 → 续约率暴跌 → 触发自我保护
     *
     * 原理: 通过 Eureka REST API (POST /eureka/apps/{appName}) 直接注册
     * 注册后没有 EurekaClient 维护心跳，所以 ~90s 后 lease 过期
     * 但因为续约率低于 85%，自我保护激活，实例不会被剔除
     */
    @PostMapping("/test/fake-register")
    public Map<String, Object> fakeRegister(
            @RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "FAKE-SERVICE") String appName) {

        String serverUrl = eurekaServerUrl.split(",")[0].trim();
        List<String> registered = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            String instanceId = String.format("fake-host-%d", i);
            int port = 30000 + i;

            // 构造 Eureka REST API XML 请求体
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<instance>"
                    + "<instanceId>" + instanceId + "</instanceId>"
                    + "<hostName>fake-host</hostName>"
                    + "<app>" + appName + "</app>"
                    + "<ipAddr>10.0.0." + i + "</ipAddr>"
                    + "<status>UP</status>"
                    + "<port enabled=\"true\">" + port + "</port>"
                    + "<vipAddress>" + appName.toLowerCase() + "</vipAddress>"
                    + "<secureVipAddress>" + appName.toLowerCase() + "</secureVipAddress>"
                    + "<dataCenterInfo>"
                    + "<name>MyOwn</name>"
                    + "<class>com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo</class>"
                    + "</dataCenterInfo>"
                    + "<leaseInfo>"
                    + "<renewalIntervalInSecs>30</renewalIntervalInSecs>"
                    + "<durationInSecs>90</durationInSecs>"
                    + "</leaseInfo>"
                    + "</instance>";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            HttpEntity<String> entity = new HttpEntity<>(xml, headers);

            try {
                ResponseEntity<String> resp = restTemplate.exchange(
                        serverUrl + "apps/" + appName,
                        HttpMethod.POST, entity, String.class);
                if (resp.getStatusCode().is2xxSuccessful()) {
                    registered.add("10.0.0." + i + ":" + port + " (" + instanceId + ")");
                } else {
                    failed.add(instanceId + " -> HTTP " + resp.getStatusCode());
                }
            } catch (Exception e) {
                failed.add(instanceId + " -> " + e.getMessage());
            }
        }

        int realInstances = 0;
        for (Application app : eurekaClient.getApplications().getRegisteredApplications()) {
            realInstances += app.getInstances().size();
        }
        // +count 因为刚注册的假实例可能还没同步到 client cache
        int totalAfter = realInstances + count;
        double renewalRate = totalAfter > 0 ? (double) realInstances / totalAfter : 1.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registered", registered);
        result.put("failed", failed);
        result.put("registeredCount", registered.size());

        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("realInstancesBefore", realInstances);
        analysis.put("fakeInstancesAdded", registered.size());
        analysis.put("totalInstancesAfter", totalAfter);
        analysis.put("expectedRenewalRate", String.format("%.0f%%", renewalRate * 100));
        analysis.put("threshold", "85%");
        analysis.put("willTriggerSelfPreservation", renewalRate < 0.85);
        result.put("renewalAnalysis", analysis);

        result.put("nextSteps", Arrays.asList(
                "1. 等待 ~2 分钟 (让假实例的 lease 过期)",
                "2. 打开 Dashboard: " + serverUrl.replace("/eureka/", "/"),
                "3. 观察是否出现红色 EMERGENCY 告警",
                "4. GET /test/fake-status → 查看假实例是否仍在 (保护模式下不会剔除)",
                "5. 对比: 关闭自我保护后 (profile=no-sp)，假实例会被剔除"
        ));
        return result;
    }

    /**
     * 查看假实例当前状态
     * 自我保护开启时: 假实例仍在 (不剔除)
     * 自我保护关闭时: 假实例已被剔除 (~90s 后)
     */
    @GetMapping("/test/fake-status")
    public Map<String, Object> fakeStatus(
            @RequestParam(defaultValue = "FAKE-SERVICE") String appName) {

        Map<String, Object> result = new LinkedHashMap<>();

        // 查询 Eureka Server (通过 client cache)
        Application fakeApp = eurekaClient.getApplication(appName);
        if (fakeApp != null) {
            result.put("fakeService", appName);
            result.put("fakeInstancesStillVisible", fakeApp.getInstances().size());
            result.put("instances", fakeApp.getInstances().stream().map(inst -> {
                Map<String, Object> m = new HashMap<>();
                m.put("instanceId", inst.getInstanceId());
                m.put("ip", inst.getIPAddr());
                m.put("port", inst.getPort());
                m.put("status", inst.getStatus().name());
                return m;
            }).collect(Collectors.toList()));
            result.put("verdict", "假实例仍在 → 自我保护已激活 (AP: 宁返过时数据也不丢)");
        } else {
            result.put("fakeService", appName);
            result.put("fakeInstancesStillVisible", 0);
            result.put("verdict", "假实例已被剔除 → 自我保护未激活或已关闭");
        }

        // 全量统计
        int totalApps = 0, totalInstances = 0;
        List<Map<String, Object>> allApps = new ArrayList<>();
        for (Application app : eurekaClient.getApplications().getRegisteredApplications()) {
            Map<String, Object> appInfo = new HashMap<>();
            appInfo.put("name", app.getName());
            appInfo.put("count", app.getInstances().size());
            allApps.add(appInfo);
            totalApps++;
            totalInstances += app.getInstances().size();
        }
        result.put("allApplications", allApps);
        result.put("totalInstances", totalInstances);

        String serverUrl = eurekaServerUrl.split(",")[0].trim();
        result.put("dashboardUrl", serverUrl.replace("/eureka/", "/"));
        return result;
    }

    /**
     * 清理所有假实例 (逐个 DELETE)
     */
    @DeleteMapping("/test/fake-cleanup")
    public Map<String, Object> fakeCleanup(
            @RequestParam(defaultValue = "FAKE-SERVICE") String appName) {

        String serverUrl = eurekaServerUrl.split(",")[0].trim();
        Application fakeApp = eurekaClient.getApplication(appName);
        List<String> removed = new ArrayList<>();

        if (fakeApp != null) {
            for (com.netflix.appinfo.InstanceInfo inst : fakeApp.getInstances()) {
                try {
                    restTemplate.delete(serverUrl + "apps/" + appName + "/" + inst.getInstanceId());
                    removed.add(inst.getInstanceId());
                } catch (Exception ignored) {}
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("removed", removed);
        result.put("count", removed.size());
        result.put("message", removed.isEmpty()
                ? "没有假实例需要清理 (可能已被剔除或从未注册)"
                : "已清理，续约率将逐步恢复正常");
        return result;
    }

    // ==================== 工具方法 ====================

    private Map<String, Object> instanceToMap(ServiceInstance instance) {
        Map<String, Object> map = new HashMap<>();
        map.put("host", instance.getHost());
        map.put("port", instance.getPort());
        map.put("serviceId", instance.getServiceId());
        map.put("uri", instance.getUri().toString());
        map.put("metadata", instance.getMetadata());
        return map;
    }
}
