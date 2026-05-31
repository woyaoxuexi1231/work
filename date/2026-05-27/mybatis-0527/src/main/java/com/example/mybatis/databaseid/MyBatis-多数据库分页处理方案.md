# MyBatis 多数据库分页处理方案

## 1. 问题背景

不同数据库的分页语法存在差异：

| 数据库   | 分页语法                                          |
|----------|--------------------------------------------------|
| MySQL    | `LIMIT offset, limit`                            |
| Oracle   | `ROWNUM` 或 `ROW_NUMBER() OVER()`                |
| PostgreSQL | `LIMIT limit OFFSET offset`                    |
| SQL Server | `OFFSET offset ROWS FETCH NEXT limit ROWS ONLY` |
| DB2      | `FETCH FIRST limit ROWS ONLY`                    |

## 2. 解决方案

### 2.1 方案一：使用databaseId（MyBatis原生特性）

**原理**：为同一个Mapper方法编写多个版本的SQL，MyBatis根据数据库类型自动选择。

**配置步骤**：

```java
@Configuration
public class DatabaseIdConfig {
    
    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties properties = new Properties();
        properties.setProperty("MySQL", "mysql");
        properties.setProperty("Oracle", "oracle");
        properties.setProperty("PostgreSQL", "postgresql");
        provider.setProperties(properties);
        return provider;
    }
}
```

**Mapper XML**：

```xml
<!-- MySQL版本 -->
<select id="selectByPage" resultType="Article" databaseId="mysql">
    SELECT * FROM t_article LIMIT #{offset}, #{limit}
</select>

<!-- Oracle版本 -->
<select id="selectByPage" resultType="Article" databaseId="oracle">
    SELECT * FROM (
        SELECT t.*, ROWNUM rn FROM (
            SELECT * FROM t_article ORDER BY create_time DESC
        ) t WHERE ROWNUM &lt;= #{offset} + #{limit}
    ) WHERE rn &gt; #{offset}
</select>

<!-- PostgreSQL版本 -->
<select id="selectByPage" resultType="Article" databaseId="postgresql">
    SELECT * FROM t_article LIMIT #{limit} OFFSET #{offset}
</select>
```

**优点**：
- MyBatis原生支持，无需额外依赖
- 配置简单，易于理解

**缺点**：
- 每条SQL都需要编写多个版本
- 维护成本高
- SQL重复度高

---

### 2.2 方案二：使用MyBatis-Plus分页插件（推荐）

**原理**：MyBatis-Plus内置了分页插件，自动处理不同数据库的分页语法差异。

**配置步骤**：

```java
@Configuration
public class MybatisPlusConfig {
    
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        
        // 指定数据库类型
        interceptor.addInnerInterceptor(
            new PaginationInnerInterceptor(DbType.MYSQL)
        );
        
        // 或者自动识别数据库类型
        // interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        
        return interceptor;
    }
}
```

**使用方式**：

```java
// Service层
public IPage<Article> getArticles(int pageNum, int pageSize) {
    // 创建分页对象
    Page<Article> page = new Page<>(pageNum, pageSize);
    
    // 执行分页查询（无需编写分页SQL）
    return articleMapper.selectPage(page, null);
}
```

**优点**：
- 自动处理数据库方言差异
- 使用简单，代码量少
- 支持多种数据库

**缺点**：
- 需要引入MyBatis-Plus依赖
- 复杂分页可能需要自定义SQL

**支持的数据库**：

| 数据库       | DbType           |
|-------------|------------------|
| MySQL       | DbType.MYSQL     |
| Oracle      | DbType.ORACLE    |
| PostgreSQL  | DbType.POSTGRE_SQL |
| SQL Server  | DbType.SQL_SERVER |
| 达梦        | DbType.DM        |
| 人大金仓    | DbType.KINGBASE_ES |

---

### 2.3 方案三：使用动态SQL

**原理**：通过传入数据库类型参数，使用`<choose>`动态选择SQL。

**Mapper XML**：

```xml
<select id="selectByPage" resultType="Article">
    SELECT * FROM t_article
    ORDER BY create_time DESC
    <choose>
        <when test="dbType == 'mysql'">
            LIMIT #{offset}, #{limit}
        </when>
        <when test="dbType == 'oracle'">
            <!-- Oracle分页 -->
        </when>
        <otherwise>
            LIMIT #{offset}, #{limit}
        </otherwise>
    </choose>
</select>
```

**优点**：
- 灵活性高

**缺点**：
- 需要手动传递数据库类型参数
- SQL可读性差
- 维护成本高

---

### 2.4 方案四：使用JSqlParser自定义分页插件

**原理**：通过解析SQL AST，自动添加分页语句。

**实现示例**：

```java
@Intercepts({
    @Signature(type = StatementHandler.class, 
               method = "prepare", 
               args = {Connection.class, Integer.class})
})
public class CustomPaginationInterceptor implements Interceptor {
    
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(handler);
        
        // 获取原始SQL
        BoundSql boundSql = handler.getBoundSql();
        String originalSql = boundSql.getSql();
        
        // 判断是否需要分页
        if (needPagination(originalSql)) {
            // 使用JSqlParser解析SQL
            Statement statement = CCJSqlParserUtil.parse(originalSql);
            
            if (statement instanceof Select) {
                Select select = (Select) statement;
                
                // 根据数据库类型添加分页语句
                String dbType = getDbType();
                String paginationSql = addPagination(select, dbType);
                
                // 修改SQL
                metaObject.setValue("delegate.boundSql.sql", paginationSql);
            }
        }
        
        return invocation.proceed();
    }
    
    private String addPagination(Select select, String dbType) {
        // 根据数据库类型添加不同的分页语法
        switch (dbType) {
            case "mysql":
                // 添加LIMIT
                break;
            case "oracle":
                // 添加ROWNUM
                break;
            // ...
        }
    }
}
```

**优点**：
- 统一处理，无需修改业务代码
- 灵活性高

**缺点**：
- 实现复杂
- 需要额外依赖JSqlParser

---

### 2.5 方案五：使用PageHelper分页插件

**原理**：PageHelper是另一个流行的分页插件，支持多种数据库。

**配置步骤**：

```yaml
# application.yml
pagehelper:
  helper-dialect: mysql
  reasonable: true
  support-methods-arguments: true
```

**使用方式**：

```java
public List<Article> getArticles(int pageNum, int pageSize) {
    // 设置分页参数
    PageHelper.startPage(pageNum, pageSize);
    
    // 执行查询（自动添加分页）
    return articleMapper.selectAll();
}
```

**优点**：
- 使用简单
- 支持多种数据库
- 社区活跃

**缺点**：
- 需要额外依赖
- 与MyBatis-Plus可能冲突

---

## 3. 方案对比

| 方案 | 实现难度 | 维护成本 | 灵活性 | 推荐度 |
|------|---------|---------|--------|--------|
| databaseId | 低 | 高 | 低 | ⭐⭐ |
| MyBatis-Plus分页插件 | 低 | 低 | 中 | ⭐⭐⭐⭐⭐ |
| 动态SQL | 中 | 高 | 中 | ⭐⭐ |
| 自定义分页插件 | 高 | 中 | 高 | ⭐⭐⭐ |
| PageHelper | 低 | 低 | 中 | ⭐⭐⭐⭐ |

## 4. 推荐方案

### 4.1 新项目（推荐）

使用 **MyBatis-Plus分页插件**：

```java
@Configuration
public class MybatisPlusConfig {
    
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

### 4.2 已有项目（使用MyBatis原生）

使用 **databaseId + 分页封装**：

```java
// 封装分页工具类
public class PageHelper {
    
    public static PageBounds of(int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        return new PageBounds(offset, pageSize);
    }
}

// Mapper接口
List<Article> selectByPage(@Param("offset") int offset, @Param("limit") int limit);
```

### 4.3 需要支持多数据库

使用 **MyBatis-Plus + 自定义DatabaseIdProvider**：

```java
@Configuration
public class MultiDatabaseConfig {
    
    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties properties = new Properties();
        properties.setProperty("MySQL", "mysql");
        properties.setProperty("Oracle", "oracle");
        provider.setProperties(properties);
        return provider;
    }
    
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 不指定DbType，自动识别
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
```

## 5. 总结

| 场景 | 推荐方案 |
|------|---------|
| 新项目，单数据库 | MyBatis-Plus分页插件 |
| 新项目，多数据库 | MyBatis-Plus + DatabaseIdProvider |
| 已有项目，单数据库 | MyBatis-Plus分页插件 或 PageHelper |
| 已有项目，多数据库 | databaseId + 分页封装 |
| 需要高度定制 | 自定义分页插件 |
