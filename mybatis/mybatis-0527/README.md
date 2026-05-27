# MyBatis Plus 演示项目

## 项目简介

本项目演示MyBatis的各种核心特性和使用场景，包括：

1. **Interceptor（拦截器）** - 数据权限拦截器和SQL监控拦截器
2. **SqlSessionFactoryBean（配置演示）** - MyBatis与Spring整合的核心配置
3. **Cursor（游标查询）** - 大数据量场景下的流式查询
4. **Executor（执行器）** - 自定义Executor和SQL执行流程
5. **DatabaseId（多数据库分页）** - 处理不同数据库的分页语法差异

## 技术栈

- Spring Boot 2.7.18
- MyBatis Plus 3.5.5
- MySQL 8.0
- Java 8

## 数据库配置

```
地址：192.168.3.100:3306
用户名：root
密码：123456
数据库：mybatis_demo
```

## 快速开始

### 1. 初始化数据库

执行 `src/main/resources/schema.sql` 初始化数据库和表结构。

### 2. 启动项目

```bash
mvn spring-boot:run
```

### 3. 访问接口

访问 http://localhost:8080/api/overview 查看项目总览。

## 功能模块

### 1. Interceptor（拦截器）

**数据权限拦截器**：自动在SQL中添加租户条件，实现多租户数据隔离。

```bash
# 查询用户列表（自动添加tenant_id条件）
GET /interceptor/users
```

**SQL监控拦截器**：记录SQL执行时间，慢SQL告警。

```bash
# 模拟慢SQL
GET /interceptor/slow-sql
```

### 2. SqlSessionFactoryBean（配置演示）

查看源码了解配置项：
- `src/main/java/.../sqlsession/SqlSessionFactoryBeanConfig.java`
- `src/main/java/.../sqlsession/SqlSessionFactoryBean说明文档.md`

### 3. Cursor（游标查询）

游标查询适用于大数据量场景，不会一次性加载所有数据到内存。

```bash
# 批量添加商品（用于测试）
POST /cursor/product/batch/1000

# 演示1：使用Cursor对象遍历
GET /cursor/demo1

# 演示2：使用ResultHandler处理
GET /cursor/demo2

# 演示3：导出数据到文件
GET /cursor/demo3
```

### 4. Executor（执行器）

```bash
# 查看当前Executor类型
GET /executor/type

# 批量插入演示
POST /executor/batch/100
```

查看文档了解SQL执行流程：
- `src/main/java/.../executor/MyBatis-SQL执行流程详解.md`

### 5. DatabaseId（多数据库分页）

```bash
# 使用databaseId分页
GET /databaseid/page/databaseid?pageNum=1&pageSize=10

# 使用分页插件分页
GET /databaseid/page/plugin?pageNum=1&pageSize=10

# 查看数据库信息
GET /databaseid/info
```

## 项目结构

```
src/main/java/com/example/mybatis/
├── MybatisDemoApplication.java          # 启动类
├── MybatisDemoController.java           # 总览Controller
│
├── interceptor/                         # 拦截器模块
│   ├── DataPermissionInterceptor.java   # 数据权限拦截器
│   ├── SqlCostInterceptor.java          # SQL监控拦截器
│   ├── InterceptorDemoController.java   # 演示Controller
│   ├── entity/                          # 实体类
│   └── mapper/                          # Mapper接口
│
├── sqlsession/                          # SqlSessionFactoryBean配置
│   ├── SqlSessionFactoryBeanConfig.java # 配置类
│   └── SqlSessionFactoryBean说明文档.md  # 说明文档
│
├── cursor/                              # 游标查询模块
│   ├── CursorDemoService.java           # 演示Service
│   ├── CursorDemoController.java        # 演示Controller
│   ├── entity/                          # 实体类
│   └── mapper/                          # Mapper接口
│
├── executor/                            # Executor模块
│   ├── CustomBatchExecutor.java         # 自定义Executor
│   ├── ExecutorConfig.java              # 配置类
│   ├── ExecutorDemoController.java      # 演示Controller
│   ├── MyBatis-SQL执行流程详解.md        # 执行流程文档
│   ├── entity/                          # 实体类
│   └── mapper/                          # Mapper接口
│
└── databaseid/                          # DatabaseId模块
    ├── DatabaseIdConfig.java            # 配置类
    ├── DatabaseIdDemoController.java    # 演示Controller
    ├── MyBatis-多数据库分页处理方案.md    # 方案文档
    ├── entity/                          # 实体类
    └── mapper/                          # Mapper接口
```

## 学习建议

1. **拦截器**：理解MyBatis的插件机制，掌握自定义拦截器的编写
2. **SqlSessionFactoryBean**：了解MyBatis与Spring整合的原理
3. **游标查询**：掌握大数据量场景下的查询优化
4. **Executor**：深入理解MyBatis的SQL执行流程
5. **DatabaseId**：学会处理多数据库兼容性问题

## 相关文档

- [MyBatis官方文档](https://mybatis.org/mybatis-3/zh/index.html)
- [MyBatis-Plus官方文档](https://baomidou.com/)
- [Spring Boot官方文档](https://spring.io/projects/spring-boot)
