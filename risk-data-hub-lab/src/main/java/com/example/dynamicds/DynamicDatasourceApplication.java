package com.example.dynamicds;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.example.dynamicds.config.HubDataSourceProperties;
import com.example.dynamicds.config.MarketstackProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * 应用启动类。
 * <p>
 * <b>@EnableConfigurationProperties 的作用</b><br>
 * 显式注册 {@link HubDataSourceProperties} 和 {@link MarketstackProperties}，
 * 让 Spring Boot 将 application.yml 中 {@code hub.datasource.*} 和 {@code marketstack.*}
 * 的配置绑定到对应的 @ConfigurationProperties 类上。
 * 如果不加这个注解，需要在 Properties 类上额外加 @Component 才能生效。
 * <p>
 * <b>MybatisPlusInterceptor 的配置</b><br>
 * 这里注册了分页插件（{@code PaginationInnerInterceptor}），但同步代码中实际使用
 * {@code .last("limit " + pageSize)} 而不是 MyBatis-Plus 的 {@code Page} 分页。
 * 原因：同步场景下我们只需要"基于游标（ID）取下一页"，不需要 Page 对象的
 * total/records 等额外字段。last("limit ...") 直接拼接 SQL 更轻量。
 * 分页插件保留在此是为了给后续管理后台的"条件查询 + 分页"场景预留。
 * <p>
 * <b>RestClient.Builder — 为什么注入 Builder 而不是 RestClient？</b><br>
 * 注入 {@code RestClient.Builder} 可以得到 Spring Boot 自动配置的实例
 * （如默认的超时设置、消息转换器、拦截器等）。
 * 在 MarketstackService 中再用 {@code .baseUrl()} 等按需定制，避免重复创建 RestClient。
 */
@SpringBootApplication
@MapperScan("com.example.dynamicds.mapper")
@EnableConfigurationProperties({HubDataSourceProperties.class, MarketstackProperties.class})
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

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
