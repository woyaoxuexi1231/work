package com.example.nacos.controller;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Nacos 服务注册发现 + AP/CP 对比测试 Controller
 *
 * AP (临时实例): ephemeral=true  → Distro 协议，异步复制，允许短暂不一致
 * CP (持久实例): ephemeral=false → Raft 协议，强一致性，需多数节点存活才能写入
 *
 * 测试方法:
 * 1. 启动3节点集群 (install_nacos.ps1)
 * 2. POST /test/register/ap  → 注册临时实例 (AP)
 * 3. POST /test/register/cp  → 注册持久实例 (CP)
 * 4. docker stop nacos3      → 停掉一个节点
 * 5. GET /test/ap-vs-cp      → 对比两种实例在各节点的可见性
 * 6. 再次注册 CP 实例        → 2/3 多数仍可写；停掉第2个节点后 CP 写入失败
 */
@RestController
public class NacosController {

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private NamingService namingService;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.port:8081}")
    private int serverPort;

    // ==================== 基础查询 ====================

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> result = new HashMap<>();
        result.put("application", applicationName);
        result.put("registry", "Nacos (Cluster Mode)");
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

    // ==================== AP/CP 对比测试 ====================

    /**
     * 注册临时实例 (AP 模式 - Distro 协议)
     * 特点: 异步复制，允许短暂不一致，节点故障后实例仍可见
     */
    @PostMapping("/test/register/ap")
    public Map<String, Object> registerAPInstance(
            @RequestParam(defaultValue = "ap-test-service") String serviceName,
            @RequestParam(required = false) String ip,
            @RequestParam(defaultValue = "0") int port) throws Exception {

        String realIp = (ip != null && !ip.isEmpty()) ? ip : getLocalIp();
        int realPort = port > 0 ? port : serverPort;

        Instance instance = new Instance();
        instance.setIp(realIp);
        instance.setPort(realPort);
        instance.setEphemeral(true);  // ← AP: 临时实例
        instance.setWeight(1.0);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("mode", "AP");
        metadata.put("protocol", "Distro");
        metadata.put("registeredAt", String.valueOf(System.currentTimeMillis()));
        instance.setMetadata(metadata);

        namingService.registerInstance(serviceName, instance);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "registered");
        result.put("mode", "AP (Ephemeral)");
        result.put("protocol", "Distro");
        result.put("service", serviceName);
        result.put("instance", realIp + ":" + realPort);
        result.put("behavior", "节点故障后，实例信息仍保留在各节点内存中 (最终一致)");
        return result;
    }

    /**
     * 注册持久实例 (CP 模式 - Raft 协议)
     * 特点: 强一致性，需多数节点存活 (>N/2)，写入需 Leader 确认
     */
    @PostMapping("/test/register/cp")
    public Map<String, Object> registerCPInstance(
            @RequestParam(defaultValue = "cp-test-service") String serviceName,
            @RequestParam(required = false) String ip,
            @RequestParam(defaultValue = "0") int port) throws Exception {

        String realIp = (ip != null && !ip.isEmpty()) ? ip : getLocalIp();
        int realPort = port > 0 ? port : serverPort;

        Instance instance = new Instance();
        instance.setIp(realIp);
        instance.setPort(realPort);
        instance.setEphemeral(false); // ← CP: 持久实例
        instance.setWeight(1.0);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("mode", "CP");
        metadata.put("protocol", "Raft");
        metadata.put("registeredAt", String.valueOf(System.currentTimeMillis()));
        instance.setMetadata(metadata);

        namingService.registerInstance(serviceName, instance);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "registered");
        result.put("mode", "CP (Persistent)");
        result.put("protocol", "Raft");
        result.put("service", serviceName);
        result.put("instance", realIp + ":" + realPort);
        result.put("behavior", "强一致性写入，多数节点故障 (>N/2) 后写入失败，但数据持久化不丢失");
        return result;
    }

    /**
     * 注销实例
     */
    @DeleteMapping("/test/deregister")
    public Map<String, Object> deregisterInstance(
            @RequestParam String serviceName,
            @RequestParam(required = false) String ip,
            @RequestParam(defaultValue = "0") int port) throws Exception {

        String realIp = (ip != null && !ip.isEmpty()) ? ip : getLocalIp();
        int realPort = port > 0 ? port : serverPort;

        namingService.deregisterInstance(serviceName, realIp, realPort);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "deregistered");
        result.put("service", serviceName);
        result.put("instance", realIp + ":" + realPort);
        return result;
    }

    /**
     * AP vs CP 对比查询: 分别查询两种服务的实例状态
     * 用于观察节点故障后的一致性差异
     */
    @GetMapping("/test/ap-vs-cp")
    public Map<String, Object> compareAPvsCP(
            @RequestParam(defaultValue = "ap-test-service") String apService,
            @RequestParam(defaultValue = "cp-test-service") String cpService) {

        Map<String, Object> result = new LinkedHashMap<>();

        // AP 服务实例
        Map<String, Object> apResult = new HashMap<>();
        List<ServiceInstance> apInstances = discoveryClient.getInstances(apService);
        apResult.put("service", apService);
        apResult.put("protocol", "Distro (AP)");
        apResult.put("count", apInstances.size());
        apResult.put("instances", apInstances.stream().map(this::instanceToMap).collect(Collectors.toList()));
        apResult.put("expected", "节点故障后实例仍可见 (内存复制，最终一致)");

        // CP 服务实例
        Map<String, Object> cpResult = new HashMap<>();
        List<ServiceInstance> cpInstances = discoveryClient.getInstances(cpService);
        cpResult.put("service", cpService);
        cpResult.put("protocol", "Raft (CP)");
        cpResult.put("count", cpInstances.size());
        cpResult.put("instances", cpInstances.stream().map(this::instanceToMap).collect(Collectors.toList()));
        cpResult.put("expected", "多数节点存活时数据强一致，写入需 Leader 确认");

        result.put("AP", apResult);
        result.put("CP", cpResult);
        result.put("testSteps", Arrays.asList(
                "1. POST /test/register/ap?serviceName=ap-test-service",
                "2. POST /test/register/cp?serviceName=cp-test-service",
                "3. GET /test/ap-vs-cp → 确认两种实例都已注册",
                "4. docker stop nacos3 → 停掉1个节点 (剩余2/3，多数仍存活)",
                "5. GET /test/ap-vs-cp → AP实例可能短暂不可见，CP实例仍然一致",
                "6. docker stop nacos2 → 再停1个节点 (仅剩1/3，失去多数)",
                "7. POST /test/register/cp → CP 写入失败 (无 Leader)",
                "8. POST /test/register/ap → AP 仍可写入 (仅当前节点内存)"
        ));
        return result;
    }

    /**
     * 批量注册测试实例 (方便快速测试)
     */
    @PostMapping("/test/batch-register")
    public Map<String, Object> batchRegister(
            @RequestParam(defaultValue = "3") int apCount,
            @RequestParam(defaultValue = "3") int cpCount) throws Exception {

        Map<String, Object> result = new HashMap<>();
        List<String> apRegistered = new ArrayList<>();
        List<String> cpRegistered = new ArrayList<>();
        String localIp = getLocalIp();

        // 批量注册 AP 实例
        for (int i = 1; i <= apCount; i++) {
            int port = 10000 + i;
            Instance inst = new Instance();
            inst.setIp(localIp);
            inst.setPort(port);
            inst.setEphemeral(true);
            Map<String, String> meta = new HashMap<>();
            meta.put("mode", "AP");
            meta.put("index", String.valueOf(i));
            inst.setMetadata(meta);
            namingService.registerInstance("ap-test-service", inst);
            apRegistered.add(localIp + ":" + port);
        }

        // 批量注册 CP 实例
        for (int i = 1; i <= cpCount; i++) {
            int port = 20000 + i;
            Instance inst = new Instance();
            inst.setIp(localIp);
            inst.setPort(port);
            inst.setEphemeral(false);
            Map<String, String> meta = new HashMap<>();
            meta.put("mode", "CP");
            meta.put("index", String.valueOf(i));
            inst.setMetadata(meta);
            namingService.registerInstance("cp-test-service", inst);
            cpRegistered.add(localIp + ":" + port);
        }

        result.put("apRegistered", apRegistered);
        result.put("cpRegistered", cpRegistered);
        result.put("message", "注册完成，可通过 GET /test/ap-vs-cp 查看对比结果");
        return result;
    }

    // ==================== 工具方法 ====================

    private String getLocalIp() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
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
