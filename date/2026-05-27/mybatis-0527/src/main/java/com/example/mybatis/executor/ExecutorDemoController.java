package com.example.mybatis.executor;

import com.example.mybatis.executor.entity.Log;
import com.example.mybatis.executor.mapper.LogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Executor演示Controller
 *
 * 演示自定义Executor的作用和MyBatis SQL执行流程
 */
@Slf4j
@RestController
@RequestMapping("/executor")
@RequiredArgsConstructor
public class ExecutorDemoController {

    private final LogMapper logMapper;
    private final ExecutorConfig executorConfig;
    private final SqlSessionFactory sqlSessionFactory;

    /**
     * 添加日志 - 会经过自定义Executor
     */
    @PostMapping("/log")
    public String addLog(@RequestBody Log log) {
        logMapper.insert(log);
        return "success";
    }

    /**
     * 查询所有日志
     */
    @GetMapping("/logs")
    public List<Log> listLogs() {
        return logMapper.selectList(null);
    }

    /**
     * 查看当前Executor类型
     */
    @GetMapping("/type")
    public String getExecutorType() {
        executorConfig.showExecutorType();
        return "当前Executor类型: " + sqlSessionFactory.getConfiguration().getDefaultExecutorType();
    }

    /**
     * 【演示】批量插入 - 使用BatchExecutor
     */
    @PostMapping("/batch/{count}")
    public String batchInsert(@PathVariable int count) {
        long startTime = System.currentTimeMillis();

        // 使用默认Executor逐条插入
        for (int i = 1; i <= count; i++) {
            Log log = new Log();
            log.setLevel("INFO");
            log.setMessage("普通插入日志" + i);
            log.setOperator("user");
            log.setIp("127.0.0.1");
            logMapper.insert(log);
        }

        long costTime = System.currentTimeMillis() - startTime;
        return "普通插入" + count + "条日志，耗时: " + costTime + "ms";
    }

    /**
     * 【说明】MyBatis SQL执行流程
     *
     * 从调用Mapper方法到SQL执行，经过以下核心步骤：
     *
     * 1. MapperProxy.invoke()
     *    - Mapper接口的代理对象拦截方法调用
     *
     * 2. MapperMethod.execute()
     *    - 解析方法信息，确定SQL类型（SELECT/INSERT/UPDATE/DELETE）
     *
     * 3. SqlSessionTemplate代理
     *    - 获取线程安全的SqlSession
     *
     * 4. DefaultSqlSession
     *    - 获取MappedStatement，委托给Executor执行
     *
     * 5. Executor（核心）
     *    - 检查一级缓存
     *    - 缓存未命中则查询数据库
     *    - 管理事务
     *
     * 6. StatementHandler
     *    - 创建PreparedStatement
     *    - 设置SQL超时时间
     *    - 设置fetchSize
     *
     * 7. ParameterHandler
     *    - 将Java参数设置到SQL的?占位符
     *    - 使用TypeHandler进行类型转换
     *
     * 8. PreparedStatement.execute()
     *    - 执行SQL到数据库
     *
     * 9. ResultSetHandler
     *    - 处理ResultSet，映射为Java对象
     *    - 处理关联查询、集合映射等
     *
     * 10. 返回结果
     *    - 将结果返回给调用方
     *
     * 【四大核心组件】
     * - Executor：SQL执行器，管理缓存和事务
     * - StatementHandler：语句处理器，创建Statement
     * - ParameterHandler：参数处理器，设置SQL参数
     * - ResultSetHandler：结果集处理器，处理返回结果
     */
    @GetMapping("/flow")
    public String getExecutionFlow() {
        return "请查看 MyBatis-SQL执行流程详解.md 文档";
    }
}
