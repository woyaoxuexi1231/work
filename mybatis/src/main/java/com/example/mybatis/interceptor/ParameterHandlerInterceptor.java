package com.example.mybatis.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.plugin.*;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.util.Properties;

/**
 * ParameterHandler 拦截器示例
 *
 * 【拦截对象】ParameterHandler
 * 【拦截方法】setParameters - 设置SQL参数时
 *
 * 【适用场景】
 * 1. 参数脱敏：如手机号、身份证号在日志中脱敏显示
 * 2. 参数加密：如密码字段加密后再存入数据库
 * 3. 参数校验：在参数设置前进行合法性校验
 * 4. 参数审计：记录所有SQL的参数值，用于审计追踪
 * 5. 自动填充：如自动添加 createBy、updateBy 等字段
 *
 * 【拦截时机】
 * StatementHandler.prepare() 之后
 * PreparedStatement.execute() 之前
 *
 * @author example
 * @date 2024-01-01
 */
@Slf4j
@Component
@Intercepts({
        @Signature(
                type = ParameterHandler.class,
                method = "setParameters",
                args = {PreparedStatement.class}
        )
})
public class ParameterHandlerInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        ParameterHandler parameterHandler = (ParameterHandler) invocation.getTarget();

        // 获取参数对象
        Object parameterObject = parameterHandler.getParameterObject();
        log.info("【ParameterHandler拦截器】参数对象类型: {}",
                parameterObject != null ? parameterObject.getClass().getName() : "null");
        log.info("【ParameterHandler拦截器】参数值: {}", parameterObject);

        // 【场景1】参数脱敏示例
        // 如果参数包含手机号，可以在日志中脱敏显示
        if (parameterObject instanceof String) {
            String param = (String) parameterObject;
            if (param.matches("\\d{11}")) {
                log.info("【参数脱敏】手机号: {} -> {}****{}",
                        param, param.substring(0, 3), param.substring(7));
            }
        }

        // 【场景2】参数加密示例
        // 如果是密码字段，可以在这里加密
        // 实际项目中需要判断当前操作的表和字段
        // if (isPasswordParameter(parameterObject)) {
        //     String encrypted = encrypt(parameterObject.toString());
        //     parameterHandler.setParameterObject(encrypted);
        // }

        // 继续执行原方法（设置参数）
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 可以从配置中读取属性
    }
}
