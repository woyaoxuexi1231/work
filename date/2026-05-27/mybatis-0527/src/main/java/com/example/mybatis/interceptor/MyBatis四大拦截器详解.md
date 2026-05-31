# MyBatis 四大拦截器详解

## 1. 概述

MyBatis 允许拦截以下四大核心对象的方法：

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         SQL 执行流程                                     │
├─────────────────────────────────────────────────────────────────────────┤
│  Mapper 方法调用                                                        │
│       ↓                                                                 │
│  ┌─────────────┐                                                        │
│  │  Executor    │ ← 【拦截点1】SQL执行入口，管理缓存和事务                │
│  └─────────────┘                                                        │
│       ↓                                                                 │
│  ┌─────────────────────┐                                                │
│  │  StatementHandler   │ ← 【拦截点2】SQL预编译，创建Statement            │
│  └─────────────────────┘                                                │
│       ↓                                                                 │
│  ┌─────────────────────┐                                                │
│  │  ParameterHandler   │ ← 【拦截点3】参数设置，将Java参数设置到SQL       │
│  └─────────────────────┘                                                │
│       ↓                                                                 │
│  ┌─────────────────────┐                                                │
│  │  PreparedStatement  │ ← 执行SQL到数据库                              │
│  └─────────────────────┘                                                │
│       ↓                                                                 │
│  ┌─────────────────────┐                                                │
│  │  ResultSetHandler   │ ← 【拦截点4】结果集处理，将ResultSet映射为Java对象│
│  └─────────────────────┘                                                │
│       ↓                                                                 │
│  返回结果                                                               │
└─────────────────────────────────────────────────────────────────────────┘
```

## 2. 四大拦截器对比

| 拦截对象 | 可拦截方法 | 拦截时机 | 典型场景 |
|---------|-----------|---------|---------|
| **Executor** | update, query, queryCursor, commit, rollback, close | SQL执行最顶层 | SQL监控、二级缓存、事务管理 |
| **StatementHandler** | prepare, parameterize, batch, update, query | SQL预编译阶段 | **修改SQL**、分页、添加条件 |
| **ParameterHandler** | setParameters | 参数设置阶段 | 参数加密、脱敏、校验 |
| **ResultSetHandler** | handleResultSets | 结果处理阶段 | 结果解密、脱敏、转换 |

## 3. 各拦截器详解

### 3.1 Executor 拦截器

**拦截对象**：`org.apache.ibatis.executor.Executor`

**可拦截方法**：
- `update(MappedStatement, Object)` - INSERT/UPDATE/DELETE
- `query(MappedStatement, Object, RowBounds, ResultHandler)` - SELECT
- `query(MappedStatement, Object, RowBounds, ResultHandler, CacheKey, BoundSql)` - SELECT（带缓存Key）
- `queryCursor(MappedStatement, Object, RowBounds)` - 游标查询
- `commit(boolean)` - 提交事务
- `rollback(boolean)` - 回滚事务
- `close(boolean)` - 关闭

**适用场景**：
1. SQL执行监控和统计
2. 多租户数据隔离（在Executor层添加条件）
3. 自动填充审计字段
4. 软删除处理

**示例代码**：`MyExecutorInterceptor.java`

```java
@Intercepts({
    @Signature(
        type = Executor.class,
        method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}
    )
})
```

---

### 3.2 StatementHandler 拦截器

**拦截对象**：`org.apache.ibatis.executor.statement.StatementHandler`

**可拦截方法**：
- `prepare(Connection, Integer)` - 准备Statement
- `parameterize(Statement)` - 参数化
- `batch(Statement)` - 批量处理
- `update(Statement)` - 更新
- `query(Statement, ResultHandler)` - 查询

**适用场景**：
1. **修改SQL**（最常用）：添加WHERE条件、分页
2. SQL注入防护
3. 读写分离（在prepare阶段判断并路由）

**示例代码**：`DataPermissionInterceptor.java`

```java
@Intercepts({
    @Signature(
        type = StatementHandler.class,
        method = "prepare",
        args = {Connection.class, Integer.class}
    )
})
```

**为什么修改SQL要拦截StatementHandler？**

此时 `BoundSql` 已经生成，可以直接获取和修改SQL字符串：
```java
BoundSql boundSql = statementHandler.getBoundSql();
String sql = boundSql.getSql();  // 获取SQL
metaObject.setValue("delegate.boundSql.sql", newSql);  // 修改SQL
```

---

### 3.3 ParameterHandler 拦截器

**拦截对象**：`org.apache.ibatis.executor.parameter.ParameterHandler`

**可拦截方法**：
- `setParameters(PreparedStatement)` - 设置SQL参数

**适用场景**：
1. 参数加密（如密码加密后再存入数据库）
2. 参数脱敏（日志中隐藏敏感信息）
3. 参数校验（在设置前校验合法性）
4. 自动填充参数

**示例代码**：`ParameterHandlerInterceptor.java`

```java
@Intercepts({
    @Signature(
        type = ParameterHandler.class,
        method = "setParameters",
        args = {PreparedStatement.class}
    )
})
```

**注意事项**：
- 此时SQL已经预编译，无法再修改SQL结构
- 只能操作参数值

---

### 3.4 ResultSetHandler 拦截器

**拦截对象**：`org.apache.ibatis.executor.resultset.ResultSetHandler`

**可拦截方法**：
- `handleResultSets(Statement)` - 处理结果集

**适用场景**：
1. 结果解密（如手机号、身份证号解密后返回）
2. 结果脱敏（手机号显示为 138****1234）
3. 结果转换（code转中文描述）
4. 记录查询返回的数据量
5. 数据权限后置过滤

**示例代码**：`ResultSetHandlerInterceptor.java`

```java
@Intercepts({
    @Signature(
        type = ResultSetHandler.class,
        method = "handleResultSets",
        args = {Statement.class}
    )
})
```

**注意事项**：
- 此时SQL已执行完毕，只能操作返回结果
- 调用 `invocation.proceed()` 获取结果后再处理

## 4. 拦截器执行顺序

```
Executor.query()
    │
    ├─ 【Executor拦截器】intercept()
    │       ↓
    │   调用 invocation.proceed()
    │       ↓
    ├─ 【检查一级缓存】
    │       ↓
    ├─ 【StatementHandler.prepare()】
    │       │
    │       ├─ 【StatementHandler拦截器】intercept()  ← 修改SQL的最佳时机
    │       │       ↓
    │       │   调用 invocation.proceed()
    │       │       ↓
    │       │   创建 PreparedStatement
    │       │
    │       ├─ 【StatementHandler.parameterize()】
    │       │       │
    │       │       ├─ 【ParameterHandler拦截器】intercept()  ← 参数处理
    │       │       │       ↓
    │       │       │   设置参数
    │       │       ↓
    │       ↓
    ├─ 【PreparedStatement.execute()】
    │       ↓
    ├─ 【ResultSetHandler.handleResultSets()】
    │       │
    │       ├─ 【ResultSetHandler拦截器】intercept()  ← 结果处理
    │       │       ↓
    │       │   处理结果集
    │       ↓
    ↓
返回结果
```

## 5. 如何选择拦截哪个对象？

| 需求 | 推荐拦截对象 | 原因 |
|------|-------------|------|
| 添加WHERE条件（如租户隔离） | StatementHandler | 此时可以修改SQL字符串 |
| 分页查询 | StatementHandler | 需要在SQL末尾添加LIMIT |
| 参数加密 | ParameterHandler | 在参数设置时加密 |
| 结果脱敏 | ResultSetHandler | 在结果返回前脱敏 |
| SQL执行统计 | Executor | 最顶层，能获取完整执行时间 |
| 读写分离 | StatementHandler | 在prepare阶段判断并路由 |
| 软删除 | StatementHandler | 将DELETE转为UPDATE |

## 6. 注意事项

1. **拦截器顺序**：多个拦截器可能拦截同一个方法，顺序由 `@Intercepts` 注解和插件注册顺序决定
2. **性能影响**：拦截器会在每次SQL执行时调用，避免在拦截器中做耗时操作
3. **异常处理**：拦截器中的异常会导致SQL执行失败，需要妥善处理
4. **ThreadLocal清理**：如果使用ThreadLocal存储数据，务必在finally中清理
