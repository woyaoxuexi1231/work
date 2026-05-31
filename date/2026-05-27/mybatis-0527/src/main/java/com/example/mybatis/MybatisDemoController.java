package com.example.mybatis;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MyBatis演示项目 - 总览Controller
 */
@RestController
@RequestMapping("/api")
public class MybatisDemoController {

    /**
     * 项目总览
     */
    @GetMapping("/overview")
    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();

        overview.put("项目名称", "MyBatis Plus 演示项目");
        overview.put("项目说明", "演示MyBatis的各种核心特性和使用场景");

        Map<String, String> modules = new LinkedHashMap<>();

        // 1. Interceptor模块
        modules.put("1. Interceptor（拦截器）", "/interceptor/**");
        modules.put("   - 数据权限拦截器", "GET /interceptor/users - 自动添加租户条件");
        modules.put("   - SQL监控拦截器", "GET /interceptor/slow-sql - 记录SQL执行时间");

        // 2. SqlSessionFactoryBean模块
        modules.put("2. SqlSessionFactoryBean（配置演示）", "查看源码注释了解配置项");
        modules.put("   - 配置说明", "src/main/java/.../sqlsession/SqlSessionFactoryBeanConfig.java");

        // 3. Cursor模块
        modules.put("3. Cursor（游标查询）", "/cursor/**");
        modules.put("   - 批量添加商品", "POST /cursor/product/batch/{count}");
        modules.put("   - Cursor对象遍历", "GET /cursor/demo1");
        modules.put("   - ResultHandler处理", "GET /cursor/demo2");
        modules.put("   - 导出数据到文件", "GET /cursor/demo3");

        // 4. Executor模块
        modules.put("4. Executor（执行器）", "/executor/**");
        modules.put("   - 查看Executor类型", "GET /executor/type");
        modules.put("   - 批量插入演示", "POST /executor/batch/{count}");
        modules.put("   - SQL执行流程说明", "查看源码注释和文档");

        // 5. DatabaseId模块
        modules.put("5. DatabaseId（多数据库分页）", "/databaseid/**");
        modules.put("   - databaseId分页", "GET /databaseid/page/databaseid");
        modules.put("   - 分页插件分页", "GET /databaseid/page/plugin");
        modules.put("   - 查看数据库信息", "GET /databaseid/info");

        overview.put("功能模块", modules);

        overview.put("数据库配置", "MySQL 192.168.3.100:3306");

        return overview;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
