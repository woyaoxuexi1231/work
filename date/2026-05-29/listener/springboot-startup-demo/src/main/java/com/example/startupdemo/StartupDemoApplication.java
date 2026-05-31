package com.example.startupdemo;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

import java.io.PrintStream;

@SpringBootApplication
public class StartupDemoApplication {

    public static void main(String[] args) {

        // ============================================================
        // 方式一（默认）：classpath: banner.txt
        // 在 src/main/resources/banner.txt 放一个文本文件即可。
        // Spring Boot 自动加载，支持 ${spring-boot.version} 等占位符
        // 和 ${AnsiColor.*} 颜色控制。
        // ============================================================

        // ============================================================
        // 方式二：指定自定义路径
        // spring.banner.location=classpath:my-banner.txt   ← application.properties
        // spring.banner.image.location=classpath:banner.png ← 图片转 ASCII
        // ============================================================

        // ============================================================
        // 方式三：程序式 Banner（优先级最高）
        // 通过 SpringApplication.setBanner() 设置一个 Banner 实例。
        // 如果同时存在 banner.txt，程序式覆盖文件式。
        // ============================================================
        SpringApplication app = new SpringApplication(StartupDemoApplication.class);
        app.setBanner(new Banner() {
            @Override
            public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
                out.println();
                out.println("  ╔══════════════════════════════════════╗");
                out.println("  ║  ʚɞ  自定义 Banner — 程序式方式  ʚɞ  ║");
                out.println("  ╠══════════════════════════════════════╣");
                out.println("  ║  Spring Boot: " + environment.getProperty("spring-boot.version", "?") + "     ║");
                out.println("  ║  Profile:    " + String.join(", ", environment.getActiveProfiles()) + "        ║");
                out.println("  ╚══════════════════════════════════════╝");
                out.println();
            }
        });
        app.setBannerMode(Banner.Mode.CONSOLE); // LOG / CONSOLE / OFF
        app.run(args);

        // ============================================================
        // 方式四：关闭 Banner
        // app.setBannerMode(Banner.Mode.OFF);
        // 或者 application.properties 中：
        // spring.main.banner-mode=off
        // ============================================================
    }

    /**
     * 如果你只是想纯代码方式（不依赖 banner.txt），也可以继承
     * SpringBootApplication 后直接配。上面已经是实际生效方式了。
     *
     * 三种配置的优先级：
     *   程序式 setBanner()  >  banner.txt  >  Spring Boot 默认 Banner
     */
}
