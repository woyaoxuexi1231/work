package com.example.nacos.controller;

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
 * Nacos 服务注册发现演示 Controller
 * 展示如何从 Nacos 注册中心获取服务实例列表
 */
@RestController
public class NacosController {

    @Autowired
    private DiscoveryClient discoveryClient;

    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * 查询当前服务信息
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> result = new HashMap<>();
        result.put("application", applicationName);
        result.put("registry", "Nacos");
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
