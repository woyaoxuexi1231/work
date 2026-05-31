package com.example.mybatis.databaseid;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

/**
 * DatabaseId配置 - 含底层原理详解
 *
 * 【核心问题】MyBatis怎么知道当前数据库是MySQL还是Oracle？
 *
 * 答案：通过 JDBC 连接的 DatabaseMetaData 获取数据库厂商信息。
 *
 * 【底层原理】
 *
 * 1. 启动阶段（初始化DatabaseIdProvider时）：
 *    ┌─────────────────────────────────────────────────────────────────┐
 *    │  DataSource.getConnection()                                     │
 *    │       ↓                                                         │
 *    │  Connection.getMetaData()  → 获取 DatabaseMetaData 对象          │
 *    │       ↓                                                         │
 *    │  DatabaseMetaData.getDatabaseProductName()  → "MySQL"            │
 *    │       ↓                                                         │
 *    │  在properties中查找 "MySQL" → "mysql"                            │
 *    │       ↓                                                         │
 *    │  返回 databaseId = "mysql"                                      │
 *    └─────────────────────────────────────────────────────────────────┘
 *
 * 2. 解析阶段（解析Mapper XML时）：
 *    ┌─────────────────────────────────────────────────────────────────┐
 *    │  <select id="selectByPage" databaseId="mysql">                  │
 *    │      SELECT * FROM t LIMIT #{offset}, #{limit}                  │
 *    │  </select>                                                      │
 *    │                                                                 │
 *    │  <select id="selectByPage" databaseId="oracle">                 │
 *    │      SELECT * FROM (SELECT t.*, ROWNUM rn FROM ...) WHERE ...   │
 *    │  </select>                                                      │
 *    │                                                                 │
 *    │  MyBatis解析时：                                                │
 *    │  - 如果当前databaseId="mysql"，只注册第一个SQL                   │
 *    │  - 如果当前databaseId="oracle"，只注册第二个SQL                  │
 *    │  - 如果没有databaseId属性，作为默认SQL（兜底）                   │
 *    └─────────────────────────────────────────────────────────────────┘
 *
 * 3. 执行阶段（调用Mapper方法时）：
 *    ┌─────────────────────────────────────────────────────────────────┐
 *    │  Mapper接口调用 → 查找MappedStatement                           │
 *    │       ↓                                                         │
 *    │  找到匹配当前databaseId的SQL                                     │
 *    │       ↓                                                         │
 *    │  执行SQL                                                        │
 *    └─────────────────────────────────────────────────────────────────┘
 *
 * 【关键API】
 * - java.sql.DatabaseMetaData.getDatabaseProductName() → 返回数据库产品名
 *   - MySQL: "MySQL"
 *   - Oracle: "Oracle"
 *   - PostgreSQL: "PostgreSQL"
 *   - SQL Server: "Microsoft SQL Server"
 *   - 达梦: "DM DBMS"
 *   - 人大金仓: "KingbaseES"
 *
 * @author example
 * @date 2024-01-01
 */
@Slf4j
@Configuration
public class DatabaseIdConfig {

    /**
     * 【核心配置】DatabaseIdProvider
     *
     * VendorDatabaseIdProvider 的工作流程：
     *
     * 1. 调用 DataSource.getConnection() 获取数据库连接
     * 2. 通过 Connection.getMetaData() 获取 DatabaseMetaData
     * 3. 调用 DatabaseMetaData.getDatabaseProductName() 获取数据库产品名
     * 4. 在配置的 properties 中查找对应的别名
     * 5. 返回别名作为 databaseId
     *
     * 源码位置：org.apache.ibatis.mapping.VendorDatabaseIdProvider
     */
    @Bean
    public DatabaseIdProvider databaseIdProvider(DataSource dataSource) {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();

        // 配置数据库别名映射
        // key: 数据库厂商名称（getDatabaseProductName()的返回值）
        // value: 自定义的databaseId（在SQL语句中使用）
        Properties properties = new Properties();

        // MySQL → getDatabaseProductName() 返回 "MySQL"
        properties.setProperty("MySQL", "mysql");

        // Oracle → getDatabaseProductName() 返回 "Oracle"
        properties.setProperty("Oracle", "oracle");

        // PostgreSQL → getDatabaseProductName() 返回 "PostgreSQL"
        properties.setProperty("PostgreSQL", "postgresql");

        // SQL Server → getDatabaseProductName() 返回 "Microsoft SQL Server"
        properties.setProperty("Microsoft SQL Server", "sqlserver");

        // 达梦数据库 → getDatabaseProductName() 返回 "DM DBMS"
        properties.setProperty("DM DBMS", "dm");

        // 人大金仓 → getDatabaseProductName() 返回 "KingbaseES"
        properties.setProperty("KingbaseES", "kingbase");

        provider.setProperties(properties);

        // 【演示】查看当前数据库信息
        showDatabaseInfo(dataSource);

        return provider;
    }

    /**
     * 演示：通过JDBC获取数据库信息
     *
     * 这就是MyBatis获取数据库类型的底层实现
     */
    private void showDatabaseInfo(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            String productName = metaData.getDatabaseProductName();
            String productVersion = metaData.getDatabaseProductVersion();
            String driverName = metaData.getDriverName();
            String driverVersion = metaData.getDriverVersion();
            String url = metaData.getURL();
            String userName = metaData.getUserName();

            log.info("========== 数据库信息（通过JDBC DatabaseMetaData获取） ==========");
            log.info("数据库产品名: {}", productName);        // MySQL
            log.info("数据库版本: {}", productVersion);        // 8.0.33
            log.info("驱动名称: {}", driverName);              // MySQL Connector/J
            log.info("驱动版本: {}", driverVersion);           // 8.0.33
            log.info("连接URL: {}", url);
            log.info("用户名: {}", userName);
            log.info("=============================================================");

            // 【关键】这就是MyBatis判断数据库类型的依据
            log.info("【关键】getDatabaseProductName() 返回: \"{}\"", productName);
            log.info("【关键】在properties中查找 \"{}\" → 对应的databaseId", productName);

        } catch (SQLException e) {
            log.error("获取数据库信息失败", e);
        }
    }

    /**
     * 【VendorDatabaseIdProvider 源码解析】
     *
     * 核心方法：getDatabaseId(DataSource dataSource)
     *
     * ```java
     * public String getDatabaseId(DataSource dataSource) {
     *     if (dataSource == null) {
     *         throw new RuntimeException("...");
     *     }
     *     try {
     *         // 1. 获取数据库连接
     *         Connection connection = dataSource.getConnection();
     *         try {
     *             // 2. 获取DatabaseMetaData
     *             DatabaseMetaData metaData = connection.getMetaData();
     *
     *             // 3. 获取数据库产品名
     *             String productName = metaData.getDatabaseProductName();
     *
     *             // 4. 在properties中查找匹配的databaseId
     *             //    遍历所有配置的key，找到productName中包含的key
     *             return getDatabaseId(productName);
     *         } finally {
     *             connection.close();
     *         }
     *     } catch (SQLException e) {
     *         log.error("...", e);
     *     }
     *     return null;
     * }
     * ```
     *
     * 【重要细节】
     * - 这个方法只在启动时调用一次，结果会被缓存
     * - 不是每次执行SQL都去获取，不会有性能问题
     * - 使用String.contains()进行模糊匹配，所以"MySQL"能匹配到"MySQL xxx"
     */
}
