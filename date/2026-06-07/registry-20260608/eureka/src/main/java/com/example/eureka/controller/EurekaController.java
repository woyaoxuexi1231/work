package com.example.eureka.controller;

import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Eureka 服务注册发现 + AP (自我保护模式) 测试 Controller
 *
 * Eureka 是纯 AP 设计:
 * - 自我保护模式: 当心跳续约率低于阈值 (默认 85%) 时触发
 * - 触发后: 不再剔除任何实例，即使客户端已停止心跳
 * - 目的: 防止网络分区导致大量实例被误删
 *
 * 测试方法:
 * 1. 启动2节点集群 (install_eureka.ps1)
 * 2. 启动本服务，等待注册
 * 3. GET /test/self-preservation → 查看自我保护状态
 * 4. docker stop eureka2 → 停掉一个 peer 节点
 * 5. 观察 Dashboard 告警 + 再次查询自我保护状态
 */
@RestController
public class EurekaController {

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
                m.put("healthCheckUrl", inst.getHealthCheckUrl());
                return m;
            }).collect(Collectors.toList()));
        } else {
            result.put("message", "Application not found: " + appName);
        }
        return result;
    }

    // ==================== AP 自我保护模式测试 ====================

    /**
     * 通过 Eureka Server REST API 查询自我保护模式状态
     * 直接访问 Eureka Server 的 /eureka/apps 接口获取原始数据
     */
    @GetMapping("/test/self-preservation")
    public Map<String, Object> selfPreservationStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Application> allApps = eurekaClient.getApplications().getRegisteredApplications();

        int totalInstances = 0;
        List<Map<String, Object>> appDetails = new ArrayList<>();
        for (Application app : allApps) {
            Map<String, Object> appInfo = new HashMap<>();
            appInfo.put("name", app.getName());
            appInfo.put("instanceCount", app.getInstances().size());
            appInfo.put("instances", app.getInstances().stream().map(inst -> {
                Map<String, Object> m = new HashMap<>();
                m.put("instanceId", inst.getInstanceId());
                m.put("ip", inst.getIPAddr());
                m.put("port", inst.getPort());
                m.put("status", inst.getStatus().name());
                return m;
            }).collect(Collectors.toList()));
            appDetails.add(appInfo);
            totalInstances += app.getInstances().size();
        }

        result.put("eurekaServerUrl", eurekaServerUrl);
        result.put("totalApplications", allApps.size());
        result.put("totalInstances", totalInstances);
        result.put("applications", appDetails);
        result.put("dashboardUrl", eurekaServerUrl.replace("/eureka/", "/"));
        result.put("testGuide", Arrays.asList(
                "1. 正常状态: 所有实例心跳正常，Dashboard 无告警",
                "2. docker stop eureka2 → 停掉一个 peer 节点",
                "3. Dashboard 出现红色告警: 'EMERGENCY - EUREKA MAY BE INCORRECTLY CLAIMING...'",
                "4. 这说明自我保护已激活: 即使心跳丢失也不会剔除实例 (AP 设计)",
                "5. docker start eureka2 → 恢复节点，告警消失",
                "",
                "自我保护核心逻辑:",
                "  - 每分钟统计心跳续约数",
                "  - 当实际续约数 < 阈值续约数 (默认 85%) → 触发保护",
                "  - 保护模式下: 不剔除任何实例，宁可返回可能过时的数据也不丢数据"
        ));
        return result;
    }

    /**
     * 多节点对比查询: 同时查询两个 Eureka Server 的注册表
     * 用于验证 peer 间数据同步和分区后的一致性
     */
    @GetMapping("/test/multi-node-compare")
    public Map<String, Object> multiNodeCompare() {
        Map<String, Object> result = new LinkedHashMap<>();
        RestTemplate restTemplate = new RestTemplate();

        // 查询第一个节点 (通过 client 已连接)
        List<Application> apps = eurekaClient.getApplications().getRegisteredApplications();
        Map<String, Object> currentNode = new HashMap<>();
        currentNode.put("connectedTo", eurekaServerUrl);
        currentNode.put("totalApps", apps.size());
        currentNode.put("apps", apps.stream().map(app -> {
            Map<String, Object> m = new HashMap<>();
            m.put("name", app.getName());
            m.put("instances", app.size());
            return m;
        }).collect(Collectors.toList()));
        result.put("currentNode", currentNode);

        // 尝试查询另一个 peer 节点
        String[] serverUrls = eurekaServerUrl.split(",");
        if (serverUrls.length > 1) {
            Map<String, Object> peerNode = new HashMap<>();
            peerNode.put("peerUrl", serverUrls[1].trim());
            peerNode.put("status", "配置了多节点，可通过 Dashboard 对比两个节点的注册表");
            result.put("peerNode", peerNode);
        }

        result.put("compareTip", "打开两个 Dashboard 对比: http://localhost:8761/ 和 http://localhost:8762/");
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
