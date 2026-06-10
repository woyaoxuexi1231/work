package com.example.gc20260609;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * <h2>线上接口偶发性突然很慢 —— GC 场景复现项目</h2>
 *
 * <h3>🎯 模拟场景</h3>
 * <p>
 * 面试官问：线上某个接口偶发性突然很慢（从 50ms 跳到 2~5s），怎么排查？<br>
 * 本项目专门模拟 <strong>GC（Full GC STW）</strong> 导致的偶发性毛刺。
 * </p>
 *
 * <h3>🔥 问题根因链路</h3>
 * <pre>
 *   MemoryLeakService 里有一个“本地缓存” HashMap（SoftReference 包装，120 条×10MB=1200MB）
 *       ↓ 每 1 秒写入 10MB，SoftReference 保护对象不被 Young GC 回收
 *       ↓ 缓存填满后老年代 1200MB 全是活对象
 *       ↓ Full GC（SerialGC 单线程）扫描全部 1200MB 活对象 → STW 1.5~3s（毛刺！）
 *       ↓ 扫描后 JVM 清除 SoftReference → 回收 1200MB
 *       ↓ 缓存自动失效 → 重新填充 → 循环…（不会 OOM）
 * </pre>
 *
 * <h3>🚀 启动方式</h3>
 * <pre>
 * # 方式一：Maven（推荐，JVM 参数已配置在 application.yml 注释中）
 * mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xms2g -Xmx2g -XX:+UseSerialGC -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:./gc.log"
 *
 * # 方式二：先打包再运行
 * mvn clean package -DskipTests
 * java -Xms2g -Xmx2g -XX:+UseSerialGC -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:./gc.log -jar target/gc-20260609-1.0.0.jar
 *
 * # 方式三：不开启 GC 日志（模拟没有 GC 日志的生产环境）
 * java -Xms2g -Xmx2g -XX:+UseSerialGC -jar target/gc-20260609-1.0.0.jar
 * </pre>
 *
 * <h3>📡 测试接口</h3>
 * <ul>
 *   <li>{@code GET /api/orders}          — 订单列表（正常 ~10ms，Full GC 时 ~2-5s）</li>
 *   <li>{@code GET /api/orders/{id}}     — 订单详情</li>
 *   <li>{@code GET /api/orders/search}   — 订单搜索（模拟稍复杂的业务逻辑）</li>
 * </ul>
 * <p>⚠️ 注意：项目不暴露任何诊断接口，所有排查操作通过 JDK 命令行工具完成（jstat、jmap、jcmd 等）</p>
 *
 * <h3>📋 排查步骤见 {@code 排查指南.md}</h3>
 */
@SpringBootApplication
@EnableScheduling
public class GcApplication {

    public static void main(String[] args) {
        SpringApplication.run(GcApplication.class, args);
        System.out.println("=======================================================");
        System.out.println("  GC 场景模拟项目已启动");
        System.out.println("  接口地址: http://localhost:8200/api/orders");
        System.out.println("  排查全靠 JDK 命令行工具（jstat / jmap / jcmd）");
        System.out.println("  等待 Full GC 毛刺...（约 2 分钟后出现，之后循环发生）");
        System.out.println("=======================================================");
    }
}
