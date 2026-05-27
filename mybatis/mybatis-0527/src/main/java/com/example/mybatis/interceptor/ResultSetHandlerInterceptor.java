package com.example.mybatis.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.plugin.*;
import org.springframework.stereotype.Component;

import java.sql.Statement;
import java.util.List;
import java.util.Properties;

/**
 * ResultSetHandler 拦截器示例
 *
 * 【拦截对象】ResultSetHandler
 * 【拦截方法】handleResultSets - 处理结果集时
 *
 * 【适用场景】
 * 1. 结果解密：如手机号、身份证号从数据库取出后解密
 * 2. 结果脱敏：如手机号显示为 138****1234
 * 3. 结果转换：如将数据库的code转换为中文描述
 * 4. 结果审计：记录查询返回的数据量
 * 5. 数据权限后置过滤：在结果返回前进行二次过滤
 * 6. 字段填充：如计算字段、关联字段的填充
 *
 * 【拦截时机】
 * PreparedStatement.execute() 之后
 * 数据返回给调用方之前
 *
 * @author example
 * @date 2024-01-01
 */
@Slf4j
@Component
@Intercepts({
        @Signature(
                type = ResultSetHandler.class,
                method = "handleResultSets",
                args = {Statement.class}
        )
})
public class ResultSetHandlerInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 执行原方法，获取结果集
        Object result = invocation.proceed();

        // 【场景1】记录查询返回的数据量
        if (result instanceof List) {
            List<?> resultList = (List<?>) result;
            log.info("【ResultSetHandler拦截器】查询返回 {} 条数据", resultList.size());
        } else if (result != null) {
            log.info("【ResultSetHandler拦截器】查询返回单条数据: {}", result.getClass().getName());
        } else {
            log.info("【ResultSetHandler拦截器】查询返回 null");
        }

        // 【场景2】结果脱敏示例
        // 遍历结果集，对敏感字段进行脱敏
        // if (result instanceof List) {
        //     for (Object item : (List<?>) result) {
        //         if (item instanceof User) {
        //             User user = (User) item;
        //             // 手机号脱敏
        //             String phone = user.getPhone();
        //             if (phone != null && phone.length() == 11) {
        //                 user.setPhone(phone.substring(0, 3) + "****" + phone.substring(7));
        //             }
        //         }
        //     }
        // }

        // 【场景3】结果解密示例
        // 如果存储时加密了，取出时需要解密
        // if (result instanceof List) {
        //     for (Object item : (List<?>) result) {
        //         decryptSensitiveFields(item);
        //     }
        // }

        return result;
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
