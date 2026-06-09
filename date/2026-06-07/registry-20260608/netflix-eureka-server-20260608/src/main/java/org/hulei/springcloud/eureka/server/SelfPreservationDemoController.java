package org.hulei.springcloud.eureka.server;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Eureka Server 内部诊断控制器
 * 直接读取 Server 内部状态，用于验证 peer 互联和自我保护
 */
@RestController
@RequestMapping("/demo")
public class SelfPreservationDemoController {

    @Autowired
    private PeerAwareInstanceRegistry registry;

    /**
     * 核心诊断: 续约统计 + 自我保护状态
     * 直接读 Eureka Server 内部数据，最准确
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 续约统计 (Server 内部真实数据)
        long threshold = (long) registry.getNumOfRenewsPerMinThreshold();
        long actual = (long) registry.getNumOfRenewsInLastMin();
        boolean selfPreservation = registry.isSelfPreservationModeEnabled();

        int totalInstances = 0;
        for (Application app : registry.getSortedApplications()) {
            totalInstances += app.getInstances().size();
        }

        result.put("totalInstances", totalInstances);
        result.put("renewalThreshold", threshold);
        result.put("actualRenewals", actual);

        double rate = threshold > 0 ? (double) actual / threshold * 100 : 100.0;
        result.put("renewalRate", String.format("%.1f%%", rate));
        result.put("triggerThreshold", "85%");
        result.put("selfPreservationActive", selfPreservation);

        // 所有已注册的应用和实例
        List<Map<String, Object>> apps = new ArrayList<>();
        for (Application app : registry.getSortedApplications()) {
            Map<String, Object> appInfo = new LinkedHashMap<>();
            appInfo.put("name", app.getName());
            appInfo.put("count", app.getInstances().size());
            appInfo.put("instances", app.getInstances().stream().map(inst -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("instanceId", inst.getInstanceId());
                m.put("hostname", inst.getHostName());
                m.put("ip", inst.getIPAddr());
                m.put("port", inst.getPort());
                m.put("status", inst.getStatus().name());
                return m;
            }).collect(Collectors.toList()));
            apps.add(appInfo);
        }
        result.put("applications", apps);

        // 判断
        if (selfPreservation) {
            result.put("verdict", ">>> 自我保护已激活! Dashboard 应显示红色 EMERGENCY 告警 <<<");
        } else if (rate < 85) {
            result.put("verdict", "续约率低于85%，等待下一轮检查(每分钟一次)");
        } else {
            result.put("verdict", "正常 - 续约率健康，自我保护未激活");
        }

        return result;
    }
}
