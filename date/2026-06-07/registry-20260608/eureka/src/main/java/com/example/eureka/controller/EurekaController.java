package com.example.eureka.controller;

import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Eureka 服务注册发现演示 Controller
 * 展示如何从 Eureka Server 获取服务实例列表
 */
@RestController
public class EurekaController {

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private EurekaClient eurekaClient;

    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * 查询当前服务信息
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> result = new HashMap<>();
        result.put("application", applicationName);
        result.put("registry", "Eureka");
        result.put("instances", discoveryClient.getInstances(applicationName).stream()
                .map(this::instanceToMap).collect(Collectors.toList()));
        return result;
    }

    /**
     * 查询指定服务的实例列表
     */
    @GetMapping("/services/{serviceName}")
    public Map<String, Object> getServiceInstances(@PathVariable String serviceName) {
        Map<String, Object> result = new HashMap<>();
        result.put("service", serviceName);
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        result.put("count", instances.size());
        result.put("instances", instances.stream().map(this::instanceToMap).collect(Collectors.toList()));
        return result;
    }

    /**
     * 列出所有已注册的服务名
     */
    @GetMapping("/services")
    public Map<String, Object> listServices() {
        Map<String, Object> result = new HashMap<>();
        result.put("services", discoveryClient.getServices());
        return result;
    }

    /**
     * Eureka 原生 API: 查询应用详情
     */
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
