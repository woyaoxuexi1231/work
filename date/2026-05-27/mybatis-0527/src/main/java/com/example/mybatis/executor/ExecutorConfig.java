package com.example.mybatis.executor;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;

import javax.annotation.Resource;

/**
 * Executor配置演示
 *
 * 【如何自定义Executor？】
 *
 * 在Spring Boot中，可以通过配置SqlSessionFactory来使用自定义Executor。
 * 但通常情况下，我们更多是通过插件（Interceptor）来扩展Executor的功能，
 * 而不是直接替换Executor。
 *
 * 【Executor类型选择】
 *
 * 1. SimpleExecutor（默认）
 *    - 每次执行创建新的Statement
 *    - 适用于简单场景
 *
 * 2. ReuseExecutor
 *    - 重用Statement
 *    - 适用于相同SQL频繁执行的场景
 *
 * 3. BatchExecutor
 *    - 批量执行SQL
 *    - 适用于批量插入、更新场景
 *
 * 【配置方式】
 * 方式1：在application.yml中配置
 * mybatis:
 *   configuration:
 *     default-executor-type: batch
 *
 * 方式2：通过代码配置
 */
@Slf4j
@org.springframework.context.annotation.Configuration
public class ExecutorConfig {

    @Resource
    private SqlSessionFactory sqlSessionFactory;

    /**
     * 【演示】查看当前Executor类型
     */
    public void showExecutorType() {
        Configuration configuration = sqlSessionFactory.getConfiguration();
        ExecutorType executorType = configuration.getDefaultExecutorType();
        log.info("当前默认Executor类型: {}", executorType);

        // ExecutorType枚举值：
        // SIMPLE - SimpleExecutor
        // REUSE - ReuseExecutor
        // BATCH - BatchExecutor
    }

    /**
     * 【演示】如何在代码中使用BatchExecutor
     *
     * 注意：这种方式会创建一个新的SqlSession，使用指定的Executor类型
     */
    public void demonstrateBatchExecutor() {
        // 使用BatchExecutor创建SqlSession
        // SqlSession batchSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        //
        // try {
        //     LogMapper mapper = batchSession.getMapper(LogMapper.class);
        //
        //     // 批量插入
        //     for (int i = 0; i < 1000; i++) {
        //         Log log = new Log();
        //         log.setMessage("批量日志" + i);
        //         mapper.insert(log);
        //     }
        //
        //     // 提交批量操作
        //     batchSession.flushStatements();
        //     batchSession.commit();
        // } finally {
        //     batchSession.close();
        // }

        log.info("BatchExecutor演示完成");
    }
}
