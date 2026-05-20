package com.example.dynamicds;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.example.dynamicds.config.HubDataSourceProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 应用启动类。
 * <p>
 * <b>@EnableConfigurationProperties</b><br>
 * 显式注册 {@link HubDataSourceProperties}，将 application.yml 中
 * {@code hub.datasource.*} 的配置绑定到对应的 @ConfigurationProperties 类上。
 * <p>
 * <b>MybatisPlusInterceptor 的配置</b><br>
 * 注册分页插件（{@code PaginationInnerInterceptor}），同步代码中使用
 * {@code .last("limit " + pageSize)} 游标翻页而非 MyBatis-Plus Page 分页，
 * 分页插件预留供后续管理后台的条件查询使用。
 */
@SpringBootApplication
@MapperScan("com.example.dynamicds.mapper")
@EnableConfigurationProperties({HubDataSourceProperties.class})
public class DynamicDatasourceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DynamicDatasourceApplication.class, args);
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
