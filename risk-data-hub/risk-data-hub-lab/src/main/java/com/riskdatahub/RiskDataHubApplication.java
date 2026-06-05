package com.riskdatahub;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.riskdatahub.config.HubDataSourceProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动类。
 * <p>
 * <b>@EnableConfigurationProperties</b><br>
 * 显式注册 {@link HubDataSourceProperties}，将 {@code application.yml} 中
 * {@code hub.datasource.*} 的配置绑定到对应的 @ConfigurationProperties 类上。
 * </p>
 * <p>
 * <b>MybatisPlusInterceptor</b><br>
 * 注册分页插件（{@link PaginationInnerInterceptor}）。同步代码中使用
 * {@code .last("limit " + pageSize)} 游标翻页而非 MyBatis-Plus Page 分页，
 * 分页插件预留供后续管理后台的条件查询使用。
 * </p>
 * <p>
 * <b>@MapperScan</b><br>
 * 扫描 {@code com.riskdatahub.**.mapper} 下所有 MyBatis Mapper 接口。
 * </p>
 *
 * @author risk-data-hub
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.riskdatahub.**.mapper")
@EnableConfigurationProperties({HubDataSourceProperties.class})
public class RiskDataHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskDataHubApplication.class, args);
    }

    /**
     * 注册 MyBatis-Plus 分页插件。
     *
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
