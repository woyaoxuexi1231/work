package com.example.redis.c16_security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 16. 安全性
 * <p>
 * Redis 提供多层安全机制。
 * <p>
 * 密码认证：
 * - requirepass: 设置密码
 * - AUTH password: 客户端认证
 * - Redis 6.0+ 支持双密码（用于密码轮换）
 * <p>
 * ACL（Access Control List，Redis 6.0+）：
 * - ACL SETUSER: 创建/修改用户
 * - ACL DELUSER: 删除用户
 * - ACL LIST: 列出所有用户
 * - ACL WHOAMI: 当前用户
 * - ACL GETUSER: 查看用户权限
 * <p>
 * 用户权限包括：
 * - 命令权限: +@read, +@write, -@admin
 * - 键权限: ~pattern（允许访问的键模式）
 * - 频道权限: &pattern（允许订阅的频道模式）
 * - 密码: >password
 * <p>
 * 网络安全：
 * - bind: 绑定监听地址
 * - protected-mode: 保护模式（无密码且无 bind 时拒绝外部连接）
 * <p>
 * 命令重命名/禁用：
 * - rename-command: 重命名危险命令（如 FLUSHALL、KEYS）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityDemo {

    private final StringRedisTemplate redisTemplate;

    /**
     * ACL 命令说明
     * <p>
     * ACL 是 Redis 6.0 引入的细粒度权限控制。
     * 每个用户可以有独立的命令权限、键权限和密码。
     */
    public String aclCommands() {
        String aclGuide = """
                ACL 命令与用法：

                # 列出所有用户
                ACL LIST

                # 查看当前用户
                ACL WHOAMI

                # 创建用户
                ACL SETUSER alice on >password123 ~cache:* +@read +@write

                # 创建只读用户
                ACL SETUSER readonly on >read123 ~* +@read -@write -@admin

                # 创建管理员
                ACL SETUSER admin on >admin123 ~* &* +@all

                # 删除用户
                ACL DELUSER alice

                # 查看用户权限
                ACL GETUSER alice

                # 保存 ACL 配置
                ACL SAVE

                # 权限类别：
                # +@read     读命令
                # +@write    写命令
                # +@admin    管理命令
                # +@dangerous 危险命令
                # +@all      所有命令
                # -@all      拒绝所有命令

                # 键模式：
                # ~*         所有键
                # ~cache:*   cache: 开头的键
                # ~user:*    user: 开头的键

                # 频道模式：
                # &*         所有频道
                # &news:*    news: 开头的频道
                """;

        log.info("[ACL]\n{}", aclGuide);
        return "ACL 指南已输出";
    }

    /**
     * 危险命令重命名
     * <p>
     * 生产环境应禁用或重命名以下命令：
     * - FLUSHALL / FLUSHDB: 清空数据
     * - KEYS: 全量扫描
     * - DEBUG: 调试命令
     * - CONFIG: 配置修改
     * - SHUTDOWN: 关闭服务器
     */
    public String dangerousCommands() {
        String guide = """
                危险命令处理：

                # 在 redis.conf 中重命名命令
                rename-command FLUSHALL ""
                rename-command FLUSHDB ""
                rename-command KEYS ""
                rename-command DEBUG ""
                rename-command CONFIG "CONFIG_b93c4e2a"

                # 或重命名为自定义名称
                # rename-command SHUTDOWN SHUTDOWN_custom

                # 需要注意：
                # 1. Sentinel 和 Cluster 需要 CONFIG 命令
                # 2. 重命名后 Sentinel/Cluster 配置也要更新
                # 3. 空字符串表示禁用该命令

                # 其他安全建议：
                # 1. 设置 requirepass
                # 2. bind 绑定内网地址
                # 3. protected-mode yes
                # 4. 使用 ACL 限制权限
                # 5. 防火墙限制访问 IP
                # 6. 禁用危险命令
                """;

        log.info("[安全配置]\n{}", guide);
        return "安全指南已输出";
    }

    /**
     * 网络安全配置
     * <p>
     * Redis 的网络安全配置是第一道防线。
     */
    public String networkSecurity() {
        String guide = """
                网络安全配置：

                # 1. 绑定地址（只监听内网）
                bind 127.0.0.1 192.168.1.100

                # 2. 保护模式（无密码且无 bind 时拒绝外部连接）
                protected-mode yes

                # 3. 端口（可修改默认端口）
                port 6379

                # 4. TLS/SSL（Redis 6.0+）
                tls-port 6380
                tls-cert-file /path/to/redis.crt
                tls-key-file /path/to/redis.key
                tls-ca-cert-file /path/to/ca.crt

                # 5. 连接限制
                maxclients 10000
                timeout 300

                # 6. 输入缓冲区限制（防止客户端发送过大命令）
                client-output-buffer-limit normal 0 0 0
                client-output-buffer-limit replica 256mb 64mb 60
                client-output-buffer-limit pubsub 32mb 8mb 60

                # 7. 防火墙规则（示例）
                # iptables -A INPUT -p tcp --dport 6379 -s 192.168.1.0/24 -j ACCEPT
                # iptables -A INPUT -p tcp --dport 6379 -j DROP
                """;

        log.info("[网络安全]\n{}", guide);
        return "网络安全指南已输出";
    }
}
