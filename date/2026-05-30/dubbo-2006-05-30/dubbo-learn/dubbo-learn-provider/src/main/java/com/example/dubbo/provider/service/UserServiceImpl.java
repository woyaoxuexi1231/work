package com.example.dubbo.provider.service;

import com.example.dubbo.api.UserService;
import com.example.dubbo.api.model.ComplexType;
import com.example.dubbo.api.model.User;

import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户服务实现。
 *
 * =====================================================================
 * 【面试核心③：Dubbo 超时时间 — 提供者设还是消费者设？】
 * =====================================================================
 *
 * 答案：消费者优先，提供者兜底。
 *
 * ┌──────────── 超时配置优先级（从高到低）────────────┐
 * │                                                    │
 * │  1. 方法级（消费者） @DubboReference(methods=...)  │
 * │  2. 接口级（消费者） @DubboReference(timeout)       │
 * │  3. 方法级（提供者） @DubboService(methods=...)    │
 * │  4. 接口级（提供者） @DubboService(timeout)        │
 * │  5. 全局（消费者）   dubbo.consumer.timeout        │
 * │  6. 全局（提供者）   dubbo.provider.timeout        │
 * │                                                    │
 * └────────────────────────────────────────────────────┘
 *
 * 为什么消费者优先？
 *
 *   ① 消费者最清楚自己的 SLA — Web 接口 3s，后台任务 30s，各不相同
 *   ② 同一服务被不同场景调，超时容忍度不同
 *   ③ 提供者超时只是兜底 — 防止消费者忘记设超时导致线程堆积
 *
 * 注意：不要设 timeout=0（永不超时），一旦服务卡死线程永远不释放，
 * 最终线程池耗尽 → 雪崩。
 */
@DubboService(
    version = "1.0.0",
    timeout = 3000,         // 提供者兜底 3 秒
    retries = 2,            // 失败重试 2 次（不含首次）
    delay = -1              // 容器就绪后才暴露
)
@Component
public class UserServiceImpl implements UserService {

    private final Map<Long, User> userDb = new ConcurrentHashMap<>();

    public UserServiceImpl() {
        userDb.put(1L, new User(1L, "张三", 28));
        userDb.put(2L, new User(2L, "李四", 35));
        userDb.put(3L, new User(3L, "王五", 22));
        userDb.get(1L).setEmail("zhangsan@example.com");
        userDb.get(2L).setEmail("lisi@example.com");
        userDb.get(3L).setEmail("wangwu@example.com");
    }

    @Override
    public User getUserById(Long id) {
        System.out.println("[Provider] getUserById(" + id + ") → " + userDb.get(id));
        return userDb.get(id);
    }

    @Override
    public List<User> listAll() {
        System.out.println("[Provider] listAll() → " + userDb.size() + " 条记录");
        return new ArrayList<>(userDb.values());
    }

    @Override
    public ComplexType getComplexData() {
        System.out.println("[Provider] getComplexData()");

        ComplexType data = new ComplexType();

        // ===== 嵌套泛型（Hessian2 可能丢类型信息）=====
        Map<String, List<User>> deptUsers = new HashMap<>();
        deptUsers.put("研发部", Arrays.asList(userDb.get(1L), userDb.get(3L)));
        deptUsers.put("产品部", Collections.singletonList(userDb.get(2L)));
        data.setDepartmentUsers(deptUsers);

        // ===== 深层嵌套泛型（最容易出问题）=====
        List<Map<String, Object>> extra = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("level", "A");
        item.put("score", 95);
        extra.add(item);
        data.setExtraAttributes(extra);

        // ===== 推荐做法：具体 POJO =====
        ComplexType.Department dev = new ComplexType.Department();
        dev.setName("研发部");
        dev.setMembers(Arrays.asList(userDb.get(1L), userDb.get(3L)));
        ComplexType.Department prod = new ComplexType.Department();
        prod.setName("产品部");
        prod.setMembers(Collections.singletonList(userDb.get(2L)));
        data.setDepartments(Arrays.asList(dev, prod));

        return data;
    }

    @Override
    public User slowQuery(Long id, long millis) {
        System.out.println("[Provider] slowQuery(" + id + ", " + millis + "ms) — 开始");
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("[Provider] slowQuery 完成 → " + userDb.get(id));
        return userDb.get(id);
    }
}
