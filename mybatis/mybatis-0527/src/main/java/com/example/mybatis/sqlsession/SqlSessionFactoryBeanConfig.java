package com.example.mybatis.sqlsession;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import javax.sql.DataSource;

/**
 * SqlSessionFactoryBean 配置演示
 *
 * 【重要说明】
 * 在Spring Boot项目中，通常不需要手动配置SqlSessionFactoryBean，
 * 因为MyBatis-Spring-Boot-Starter会自动帮我们装配。
 * 这里仅作演示，帮助理解SqlSessionFactoryBean的作用和可配置项。
 *
 * 【SqlSessionFactoryBean的作用】
 * SqlSessionFactoryBean是MyBatis与Spring整合的核心类，它负责：
 * 1. 创建SqlSessionFactory实例
 * 2. 配置MyBatis的各种属性
 * 3. 加载Mapper接口和XML映射文件
 * 4. 设置插件（拦截器）
 * 5. 配置类型别名、类型处理器等
 *
 * 【常见的配置项说明】
 * 1. dataSource - 数据源（必须配置）
 * 2. mapperLocations - Mapper XML文件位置
 * 3. configLocation - MyBatis配置文件位置
 * 4. typeAliasesPackage - 类型别名包
 * 5. plugins - 插件（拦截器）
 * 6. typeHandlersPackage - 类型处理器包
 * 7. configurationProperties - 配置属性
 *
 * @author example
 * @date 2024-01-01
 */
@Slf4j
@Configuration
public class SqlSessionFactoryBeanConfig {

    /**
     * 【方式一：使用原生的SqlSessionFactoryBean】
     * 适用于纯MyBatis项目
     *
     * @param dataSource 数据源
     * @return SqlSessionFactory
     */
    // @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        log.info("【SqlSessionFactoryBean配置】开始创建SqlSessionFactory...");

        // 1. 创建SqlSessionFactoryBean
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();

        // 2. 设置数据源（必须）
        factoryBean.setDataSource(dataSource);

        // 3. 设置Mapper XML文件位置
        // 支持Ant风格的路径匹配
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        factoryBean.setMapperLocations(
                resolver.getResources("classpath*:mapper/**/*.xml")
        );

        // 4. 设置MyBatis配置文件位置（可选）
        // factoryBean.setConfigLocation(resolver.getResource("classpath:mybatis-config.xml"));

        // 5. 设置类型别名包（可选）
        // 该包下的所有类都会自动注册为别名，别名默认是类名（首字母小写）
        factoryBean.setTypeAliasesPackage("com.example.mybatis.interceptor.entity");

        // 6. 设置插件（拦截器）（可选）
        // factoryBean.setPlugins(new Interceptor[]{...});

        // 7. 设置类型处理器包（可选）
        // factoryBean.setTypeHandlersPackage("com.example.mybatis.typehandler");

        // 8. 设置Configuration属性（可选）
        // 可以通过代码方式设置MyBatis的Configuration
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        // 开启驼峰命名转换
        configuration.setMapUnderscoreToCamelCase(true);
        // 开启延迟加载
        configuration.setLazyLoadingEnabled(true);
        // 设置执行器类型
        // configuration.setDefaultExecutorType(ExecutorType.BATCH);
        factoryBean.setConfiguration(configuration);

        // 9. 设置全局配置（可选）
        // factoryBean.setGlobalConfig(...);

        // 10. 创建SqlSessionFactory
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        log.info("【SqlSessionFactoryBean配置】SqlSessionFactory创建成功！");
        return sqlSessionFactory;
    }

    /**
     * 【方式二：使用MybatisSqlSessionFactoryBean】
     * 适用于MyBatis-Plus项目，支持更多Plus特性
     *
     * @param dataSource 数据源
     * @return SqlSessionFactory
     */
    // @Bean
    public SqlSessionFactory mybatisPlusSqlSessionFactory(DataSource dataSource) throws Exception {
        log.info("【MybatisSqlSessionFactoryBean配置】开始创建SqlSessionFactory...");

        // MybatisSqlSessionFactoryBean是MyBatis-Plus扩展的工厂类
        // 它继承了SqlSessionFactoryBean，增加了更多配置项
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();

        // 设置数据源
        factoryBean.setDataSource(dataSource);

        // 设置Mapper位置
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        factoryBean.setMapperLocations(
                resolver.getResources("classpath*:mapper/**/*.xml")
        );

        // 【MyBatis-Plus特有配置】
        // 设置全局配置
        // MybatisConfiguration mybatisConfiguration = new MybatisConfiguration();
        // mybatisConfiguration.setMapUnderscoreToCamelCase(true);
        // factoryBean.setConfiguration(mybatisConfiguration);

        // 设置MyBatis-Plus全局配置
        // GlobalConfig globalConfig = new GlobalConfig();
        // globalConfig.setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO));
        // factoryBean.setGlobalConfig(globalConfig);

        // 设置分页插件
        // factoryBean.setPlugins(new Interceptor[]{new PaginationInnerInterceptor()});

        return factoryBean.getObject();
    }

    /**
     * 【SqlSessionTemplate】
     * SqlSessionTemplate是SqlSession的线程安全实现，
     * 它会自动管理SqlSession的生命周期（获取、关闭、提交、回滚）。
     *
     * 在Spring Boot自动装配中，也会自动创建SqlSessionTemplate。
     *
     * @param sqlSessionFactory SqlSessionFactory
     * @return SqlSessionTemplate
     */
    // @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        log.info("【SqlSessionTemplate配置】创建SqlSessionTemplate...");
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    /**
     * 【MapperScan注解说明】
     *
     * @MapperScan用于自动扫描Mapper接口，并将它们注册为Spring Bean。
     * 它会扫描指定包下的所有接口，并为每个接口创建代理对象。
     *
     * 常用属性：
     * 1. value/basePackages - 扫描的包路径
     * 2. sqlSessionFactoryRef - 指定使用的SqlSessionFactory（多数据源时使用）
     * 3. annotationClass - 只扫描带有指定注解的接口
     * 4. markerInterface - 只扫描实现了指定接口的接口
     *
     * 示例：
     * @MapperScan(
     *     value = "com.example.mybatis.interceptor.mapper",
     *     sqlSessionFactoryRef = "sqlSessionFactory",
     *     annotationClass = Mapper.class
     * )
     */
}
