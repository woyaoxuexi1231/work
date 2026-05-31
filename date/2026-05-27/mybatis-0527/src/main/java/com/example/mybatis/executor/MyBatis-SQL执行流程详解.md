# MyBatis SQL 执行流程详解

## 1. 概述

本文档详细说明从调用 Mapper 接口方法开始，到 SQL 发送到数据库，中间经过的核心步骤。

## 2. 核心组件

MyBatis 的 SQL 执行涉及四大核心组件：

```
+------------------+------------------+------------------+------------------+
|    Executor      | StatementHandler | ParameterHandler |  ResultSetHandler|
+------------------+------------------+------------------+------------------+
| SQL执行器        | 语句处理器       | 参数处理器        | 结果集处理器      |
| 管理缓存和事务   | 创建Statement    | 设置参数          | 处理返回结果      |
+------------------+------------------+------------------+------------------+
```

## 3. 完整执行流程

```
调用Mapper接口方法
        ↓
MapperProxy.invoke()
        ↓
MapperMethod.execute()
        ↓
SqlSessionTemplate代理
        ↓
SqlSessionInterceptor.invoke()
        ↓
DefaultSqlSession.selectList()/insert()/update()/delete()
        ↓
Executor.query()/update()
        ↓
CachingExecutor（如果开启二级缓存）
        ↓
BaseExecutor（管理一级缓存）
        ↓
SimpleExecutor/ReuseExecutor/BatchExecutor
        ↓
StatementHandler.prepare()
        ↓
Connection.prepareStatement()
        ↓
ParameterHandler.setParameters()
        ↓
PreparedStatement.execute()
        ↓
ResultSetHandler.handleResultSets()
        ↓
返回结果
```

## 4. 详细步骤说明

### 4.1 MapperProxy.invoke()

```java
// Mapper接口的代理对象
public class MapperProxy<T> implements InvocationHandler {
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 1. 判断是否是Object类的方法
        if (Object.class.equals(method.getDeclaringClass())) {
            return method.invoke(this, args);
        }
        
        // 2. 获取MapperMethod
        MapperMethod mapperMethod = cachedMapperMethod(method);
        
        // 3. 执行方法
        return mapperMethod.execute(sqlSession, args);
    }
}
```

### 4.2 MapperMethod.execute()

```java
public class MapperMethod {
    
    public Object execute(SqlSession sqlSession, Object[] args) {
        Object result;
        
        // 根据SQL类型执行不同操作
        switch (command.getType()) {
            case INSERT:
                result = sqlSession.insert(command.getName(), param);
                break;
            case UPDATE:
                result = sqlSession.update(command.getName(), param);
                break;
            case DELETE:
                result = sqlSession.delete(command.getName(), param);
                break;
            case SELECT:
                if (method.returnsMany()) {
                    result = sqlSession.selectList(command.getName(), param);
                } else {
                    result = sqlSession.selectOne(command.getName(), param);
                }
                break;
        }
        
        return result;
    }
}
```

### 4.3 DefaultSqlSession

```java
public class DefaultSqlSession implements SqlSession {
    
    private final Executor executor;
    
    @Override
    public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
        try {
            // 1. 获取MappedStatement
            MappedStatement ms = configuration.getMappedStatement(statement);
            
            // 2. 委托给Executor执行
            return executor.query(ms, parameter, rowBounds, Executor.NO_RESULT_HANDLER);
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error querying database.", e);
        }
    }
}
```

### 4.4 Executor（核心）

```java
public abstract class BaseExecutor implements Executor {
    
    // 一级缓存
    protected PerpetualCache localCache = new PerpetualCache("LocalCache");
    
    @Override
    public <E> List<E> query(MappedStatement ms, Object parameter, 
                             RowBounds rowBounds, ResultHandler resultHandler) {
        // 1. 获取BoundSql
        BoundSql boundSql = ms.getBoundSql(parameter);
        
        // 2. 创建缓存Key
        CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
        
        // 3. 查询一级缓存
        return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
    }
    
    @Override
    public <E> List<E> query(MappedStatement ms, Object parameter,
                             RowBounds rowBounds, ResultHandler resultHandler,
                             CacheKey key, BoundSql boundSql) {
        // 1. 先查一级缓存
        List<E> list = (List<E>) localCache.getObject(key);
        
        if (list != null) {
            // 缓存命中
            handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
        } else {
            // 缓存未命中，查询数据库
            list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
        }
        
        return list;
    }
    
    private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter,
                                          RowBounds rowBounds, ResultHandler resultHandler,
                                          CacheKey key, BoundSql boundSql) {
        List<E> list;
        
        // 1. 放入占位符，防止循环引用
        localCache.putObject(key, EXECUTION_PLACEHOLDER);
        
        try {
            // 2. 调用子类的doQuery方法
            list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
        } finally {
            // 3. 移除占位符
            localCache.removeObject(key);
        }
        
        // 4. 放入一级缓存
        localCache.putObject(key, list);
        
        return list;
    }
}
```

### 4.5 SimpleExecutor.doQuery()

```java
public class SimpleExecutor extends BaseExecutor {
    
    @Override
    public <E> List<E> doQuery(MappedStatement ms, Object parameter,
                               RowBounds rowBounds, ResultHandler resultHandler,
                               BoundSql boundSql) throws SQLException {
        Statement stmt = null;
        try {
            // 1. 获取Configuration
            Configuration configuration = ms.getConfiguration();
            
            // 2. 创建StatementHandler
            StatementHandler handler = configuration.newStatementHandler(
                wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
            
            // 3. 准备Statement（核心步骤）
            stmt = prepareStatement(handler, ms.getStatementLog());
            
            // 4. 执行查询
            return handler.query(stmt, resultHandler);
        } finally {
            // 5. 关闭Statement
            closeStatement(stmt);
        }
    }
    
    private Statement prepareStatement(StatementHandler handler, Log statementLog) 
            throws SQLException {
        Statement stmt;
        Connection connection = getConnection(statementLog);
        
        // 创建Statement
        stmt = handler.prepare(connection, transaction.getTimeout());
        
        // 设置参数
        handler.parameterize(stmt);
        
        return stmt;
    }
}
```

### 4.6 StatementHandler（核心）

```java
public class RoutingStatementHandler implements StatementHandler {
    
    private final StatementHandler delegate;
    
    @Override
    public Statement prepare(Connection connection, Integer transactionTimeout) 
            throws SQLException {
        // 委托给具体的StatementHandler
        return delegate.prepare(connection, transactionTimeout);
    }
    
    @Override
    public void parameterize(Statement statement) throws SQLException {
        // 设置参数
        delegate.parameterize(statement);
    }
    
    @Override
    public <E> List<E> query(Statement statement, ResultHandler resultHandler) 
            throws SQLException {
        // 执行查询
        PreparedStatement ps = (PreparedStatement) statement;
        ps.execute();
        
        // 处理结果集
        return resultSetHandler.handleResultSets(ps);
    }
}
```

### 4.7 PreparedStatementHandler

```java
public class PreparedStatementHandler extends BaseStatementHandler {
    
    @Override
    public Statement prepare(Connection connection, Integer transactionTimeout) 
            throws SQLException {
        // 1. 获取PreparedStatement
        String sql = boundSql.getSql();
        
        // 2. 根据是否需要生成主键，选择不同的prepare方法
        if (ms.getKeyGenerator() instanceof Jdbc3KeyGenerator) {
            stmt = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
        } else {
            stmt = connection.prepareStatement(sql);
        }
        
        // 3. 设置超时时间
        setStatementTimeout(stmt, transactionTimeout);
        
        // 4. 设置fetchSize
        setFetchSize(stmt);
        
        return stmt;
    }
    
    @Override
    public void parameterize(Statement statement) throws SQLException {
        // 使用ParameterHandler设置参数
        parameterHandler.setParameters((PreparedStatement) statement);
    }
    
    @Override
    public <E> List<E> query(Statement statement, ResultHandler resultHandler) 
            throws SQLException {
        PreparedStatement ps = (PreparedStatement) statement;
        
        // 1. 执行SQL
        ps.execute();
        
        // 2. 处理结果集
        return resultSetHandler.handleResultSets(ps);
    }
}
```

### 4.8 ParameterHandler

```java
public class DefaultParameterHandler implements ParameterHandler {
    
    @Override
    public void setParameters(PreparedStatement ps) {
        // 1. 获取参数映射列表
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        
        if (parameterMappings != null) {
            for (int i = 0; i < parameterMappings.size(); i++) {
                ParameterMapping parameterMapping = parameterMappings.get(i);
                
                // 2. 获取参数值
                Object value;
                if (parameterMapping.getMode() != ParameterMode.OUT) {
                    value = getParameterValue(parameterMapping);
                }
                
                // 3. 使用TypeHandler设置参数
                TypeHandler typeHandler = parameterMapping.getTypeHandler();
                typeHandler.setParameter(ps, i + 1, value, parameterMapping.getJdbcType());
            }
        }
    }
}
```

### 4.9 ResultSetHandler

```java
public class DefaultResultSetHandler implements ResultSetHandler {
    
    @Override
    public List<Object> handleResultSets(Statement stmt) throws SQLException {
        final List<Object> multipleResults = new ArrayList<>();
        
        ResultSet rs = null;
        try {
            // 1. 获取ResultSet
            rs = ((PreparedStatement) stmt).getResultSet();
            
            // 2. 处理每个ResultSet
            while (rs != null) {
                // 获取ResultMap
                ResultMap resultMap = resultMaps.get(resultMapIndex);
                
                // 处理ResultSet
                handleResultSet(rs, resultMap, multipleResults, parentMapping);
                
                // 获取下一个ResultSet
                rs = getNextResultSet(stmt);
            }
            
            return collapseSingleResultList(multipleResults);
        } finally {
            closeResultSet(rs);
        }
    }
    
    private void handleResultSet(ResultSet rs, ResultMap resultMap,
                                 List<Object> multipleResults, ResultMapping parentMapping) {
        while (rs.next()) {
            // 1. 创建结果对象
            Object rowValue = createResultObject(rs, resultMap, null);
            
            // 2. 自动映射
            if (shouldApplyAutomaticMappings(resultMap, null)) {
                applyAutomaticMappings(rs, resultMap, null, rowValue);
            }
            
            // 3. 手动映射
            applyResultMappings(rs, resultMap, null, rowValue);
            
            // 4. 添加到结果集
            multipleResults.add(rowValue);
        }
    }
}
```

## 5. 流程图

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Mapper接口调用                                │
│                     UserMapper.selectById(1L)                        │
└─────────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────────┐
│                        MapperProxy.invoke()                         │
│                    创建MapperMethod并执行                            │
└─────────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────────┐
│                      SqlSessionTemplate代理                          │
│                  获取SqlSession并执行操作                            │
└─────────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────────┐
│                    DefaultSqlSession.selectOne()                     │
│                   获取MappedStatement并委托Executor                  │
└─────────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────────┐
│                         Executor.query()                             │
│              检查一级缓存 → 未命中 → queryFromDatabase()              │
└─────────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────────┐
│                     SimpleExecutor.doQuery()                         │
│                    创建StatementHandler并执行                         │
└─────────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────────┐
│                    StatementHandler.prepare()                        │
│              创建PreparedStatement（获取数据库连接）                  │
└─────────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────────┐
│                  ParameterHandler.setParameters()                    │
│                    设置SQL参数（?占位符替换）                         │
└─────────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────────┐
│                  PreparedStatement.execute()                         │
│                      执行SQL到数据库                                 │
└─────────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────────┐
│               ResultSetHandler.handleResultSets()                    │
│              处理ResultSet映射为Java对象                              │
└─────────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────────┐
│                          返回结果                                    │
│                   User对象返回给调用方                                │
└─────────────────────────────────────────────────────────────────────┘
```

## 6. 总结

| 步骤 | 组件 | 作用 |
|------|------|------|
| 1 | MapperProxy | 代理Mapper接口，拦截方法调用 |
| 2 | MapperMethod | 解析方法信息，确定SQL类型 |
| 3 | SqlSessionTemplate | 线程安全的SqlSession代理 |
| 4 | DefaultSqlSession | MyBatis会话，委托Executor执行 |
| 5 | Executor | SQL执行器，管理缓存和事务 |
| 6 | StatementHandler | 创建和管理Statement |
| 7 | ParameterHandler | 设置SQL参数 |
| 8 | PreparedStatement | 执行SQL到数据库 |
| 9 | ResultSetHandler | 处理结果集映射 |

## 7. 自定义Executor的使用场景

1. **SQL执行监控**：记录每条SQL的执行时间
2. **统一审计字段**：自动填充create_time、update_time
3. **数据权限**：自动添加租户条件
4. **软删除**：自动将DELETE转为UPDATE
5. **数据加密**：查询时解密，插入时加密
6. **多数据源路由**：根据条件选择不同数据源
