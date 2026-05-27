package com.example.mybatis.databaseid;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * DatabaseId配置
 *
 * 【什么是databaseId？】
 * databaseId是MyBatis提供的一个特性，用于处理不同数据库之间的SQL差异。
 * 通过databaseId，可以为同一个Mapper方法编写多个版本的SQL，
 * MyBatis会根据当前数据库类型自动选择对应的SQL。
 *
 * 【使用场景】
 * 1. 不同数据库的分页语法不同（MySQL用LIMIT，Oracle用ROWNUM）
 * 2. 不同数据库的函数不同（MySQL用NOW()，Oracle用SYSDATE）
 * 3. 不同数据库的语法差异（MySQL支持INSERT IGNORE，Oracle不支持）
 *
 * 【配置步骤】
 * 1. 创建DatabaseIdProvider Bean
 * 2. 配置数据库别名映射
 * 3. 在SQL语句上添加databaseId属性
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
     * VendorDatabaseIdProvider是MyBatis内置的DatabaseIdProvider实现，
     * 它通过DataSource的DatabaseMetaData获取数据库厂商信息，
     * 然后根据配置的别名映射返回对应的databaseId。
     *
     * @return DatabaseIdProvider
     */
    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();

        // 配置数据库别名映射
        // key: 数据库厂商名称（通过DatabaseMetaData.getDatabaseProductName()获取）
        // value: 自定义的databaseId（在SQL语句中使用）
        Properties properties = new Properties();

        // MySQL
        properties.setProperty("MySQL", "mysql");

        // Oracle
        properties.setProperty("Oracle", "oracle");

        // PostgreSQL
        properties.setProperty("PostgreSQL", "postgresql");

        // SQL Server
        properties.setProperty("Microsoft SQL Server", "sqlserver");

        // 达梦数据库
        properties.setProperty("DM DBMS", "dm");

        // 人大金仓
        properties.setProperty("KingbaseES", "kingbase");

        provider.setProperties(properties);

        log.info("【DatabaseId配置】DatabaseIdProvider初始化完成");
        return provider;
    }

    /**
     * 【使用说明】
     *
     * 在Mapper XML中使用databaseId：
     *
     * <!-- MySQL版本 -->
     * <select id="selectByPage" databaseId="mysql">
     *     SELECT * FROM t_article LIMIT #{offset}, #{limit}
     * </select>
     *
     * <!-- Oracle版本 -->
     * <select id="selectByPage" databaseId="oracle">
     *     SELECT * FROM (
     *         SELECT t.*, ROWNUM rn FROM (
     *             SELECT * FROM t_article
     *         ) t WHERE ROWNUM &lt;= #{offset} + #{limit}
     *     ) WHERE rn &gt; #{offset}
     * </select>
     *
     * 【注意事项】
     * 1. 如果没有指定databaseId的SQL语句，会被当作默认SQL
     * 2. 如果指定了databaseId但没有匹配到，会使用默认SQL
     * 3. databaseId的值必须与DatabaseIdProvider返回的值一致
     */
}
