# SqlSessionFactoryBean 配置详解

## 1. 概述

`SqlSessionFactoryBean` 是 MyBatis 与 Spring 整合的核心类，它负责创建 `SqlSessionFactory` 实例。
在 Spring Boot 中，通常使用 `mybatis-spring-boot-starter` 自动装配，不需要手动配置。

## 2. 核心配置项

### 2.1 dataSource（必须）
```java
factoryBean.setDataSource(dataSource);
```
设置数据源，支持任何 `javax.sql.DataSource` 实现（如 HikariCP、Druid、DBCP 等）。

### 2.2 mapperLocations
```java
factoryBean.setMapperLocations(
    resolver.getResources("classpath*:mapper/**/*.xml")
);
```
设置 Mapper XML 文件的位置，支持 Ant 风格的路径匹配：
- `classpath:mapper/*.xml` - classpath 下 mapper 目录中的 XML
- `classpath*:mapper/**/*.xml` - 所有 classpath 下 mapper 目录及子目录中的 XML

### 2.3 configLocation
```java
factoryBean.setConfigLocation(
    resolver.getResource("classpath:mybatis-config.xml")
);
```
设置 MyBatis 配置文件的位置。配置文件示例：
```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE configuration
  PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
  "http://mybatis.org/dtd/mybatis-3-config.dtd">
<configuration>
  <settings>
    <setting name="mapUnderscoreToCamelCase" value="true"/>
    <setting name="lazyLoadingEnabled" value="true"/>
  </settings>
</configuration>
```

### 2.4 typeAliasesPackage
```java
factoryBean.setTypeAliasesPackage("com.example.entity");
```
设置类型别名包，该包下的所有类会自动注册为别名（默认类名首字母小写）。

### 2.5 plugins
```java
factoryBean.setPlugins(new Interceptor[]{
    new DataPermissionInterceptor(),
    new SqlCostInterceptor()
});
```
设置插件（拦截器），用于在 SQL 执行前后进行拦截处理。

### 2.6 typeHandlersPackage
```java
factoryBean.setTypeHandlersPackage("com.example.typehandler");
```
设置类型处理器包，用于自定义 Java 类型与 JDBC 类型的转换。

### 2.7 configuration
```java
org.apache.ibatis.session.Configuration configuration = 
    new org.apache.ibatis.session.Configuration();
configuration.setMapUnderscoreToCamelCase(true);
factoryBean.setConfiguration(configuration);
```
通过代码方式设置 MyBatis 的 Configuration，优先级高于 configLocation。

## 3. MyBatis-Plus 扩展配置

使用 `MybatisSqlSessionFactoryBean`（继承自 SqlSessionFactoryBean）：

```java
MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
factoryBean.setDataSource(dataSource);
factoryBean.setMapperLocations(resolver.getResources("classpath*:mapper/**/*.xml"));

// MyBatis-Plus 特有配置
GlobalConfig globalConfig = new GlobalConfig();
globalConfig.setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO));
factoryBean.setGlobalConfig(globalConfig);

// 分页插件
factoryBean.setPlugins(new Interceptor[]{new PaginationInnerInterceptor()});
```

## 4. Spring Boot 自动装配

在 Spring Boot 中，引入 `mybatis-spring-boot-starter` 后，会自动：
1. 创建 DataSource
2. 创建 SqlSessionFactory
3. 创建 SqlSessionTemplate
4. 扫描 Mapper 接口

只需在 `application.yml` 中配置即可：

```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto
```

## 5. 多数据源配置

当有多个数据源时，需要手动配置 SqlSessionFactory：

```java
@Configuration
@MapperScan(basePackages = "com.example.mapper.db1", 
            sqlSessionFactoryRef = "db1SqlSessionFactory")
public class DataSource1Config {
    
    @Bean("db1DataSource")
    @ConfigurationProperties(prefix = "spring.datasource.db1")
    public DataSource db1DataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean("db1SqlSessionFactory")
    public SqlSessionFactory db1SqlSessionFactory(
            @Qualifier("db1DataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
            new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/db1/*.xml"));
        return factoryBean.getObject();
    }
}
```

## 6. 总结

| 配置项 | 作用 | 是否必须 |
|--------|------|----------|
| dataSource | 数据源 | 是 |
| mapperLocations | Mapper XML 位置 | 否（Spring Boot 自动配置） |
| configLocation | MyBatis 配置文件 | 否 |
| typeAliasesPackage | 类型别名包 | 否 |
| plugins | 插件/拦截器 | 否 |
| typeHandlersPackage | 类型处理器包 | 否 |
| configuration | Configuration 对象 | 否 |
