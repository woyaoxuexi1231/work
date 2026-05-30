package com.example.dubbo.demo.provider.filter;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * <h3>自定义 Provider 端过滤器</h3>
 *
 * <p>Dubbo Filter 类似 Servlet Filter 或 Spring Interceptor——可以在 RPC 调用
 * 的前后插入自定义逻辑。多个 Filter 组成<b>责任链</b>，依次执行。</p>
 *
 * <p><b>Filter 能做什么：</b></p>
 * <ul>
 *   <li>记录每次 RPC 调用的耗时和参数</li>
 *   <li>检查 Consumer 的 IP 是否在白名单内</li>
 *   <li>统计调用次数（QPS 监控）</li>
 *   <li>在请求上下文中注入公共数据（如 traceId）</li>
 *   <li>全局异常处理——把特定异常转成友好信息</li>
 * </ul>
 *
 * <p><b>Filter 执行链路（Provider 端）：</b></p>
 * <pre>
 * Consumer 请求到达
 *   │
 *   ▼
 * Filter1.invoke()   ← 前置处理（日志、鉴权、限流）
 *   │
 *   ▼
 * Filter2.invoke()   ← 前置处理
 *   │
 *   ▼
 * 业务方法（UserServiceImpl.getUserById）
 *   │
 *   ▼
 * Filter2.invoke()   ← 后置处理（耗时统计、结果包装）
 *   │
 *   ▼
 * Filter1.invoke()   ← 后置处理
 *   │
 *   ▼
 * 响应返回 Consumer
 * </pre>
 *
 * <p><b>注册方式：</b></p>
 * <ol>
 *   <li>实现 {@link Filter} 接口</li>
 *   <li>标注 {@code @Activate(group = CommonConstants.PROVIDER)} 指定生效端</li>
 *   <li>在 {@code META-INF/dubbo/org.apache.dubbo.rpc.Filter} 文件中声明</li>
 * </ol>
 *
 * <p>Dubbo 的 SPI 机制会在启动时扫描该文件，自动加载 Filter。</p>
 */
@Activate(
        group = CommonConstants.PROVIDER,  // 在 Provider 端生效
        order = -1000                       // order 越小越靠外（越先执行前置，越后执行后置）
)
public class CustomProviderFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(CustomProviderFilter.class);

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {

        // ═════════════════════════════════════════════════════
        // 前置处理：调用前
        // ═════════════════════════════════════════════════════
        long startTime = System.currentTimeMillis();

        // 获取调用方信息
        String consumerIp = RpcContext.getContext().getRemoteHost();
        String methodName = invocation.getMethodName();
        String serviceName = invocation.getServiceName();
        Object[] args = invocation.getArguments();

        log.info("【Filter-前置】Consumer={} | 服务={} | 方法={} | 参数={}",
                consumerIp, serviceName, methodName, Arrays.toString(args));

        // 这里可以做：IP 白名单校验、QPS 计数、traceId 注入等
        // if (!ipWhitelist.contains(consumerIp)) {
        //     throw new RpcException("IP " + consumerIp + " 不在白名单内");
        // }

        // ═════════════════════════════════════════════════════
        // 执行后续 Filter 链 → 最终调用业务方法
        // ═════════════════════════════════════════════════════
        Result result;
        try {
            result = invoker.invoke(invocation);
        } catch (RpcException e) {
            log.error("【Filter-异常】方法={} | 错误={}", methodName, e.getMessage());
            throw e;
        }

        // ═════════════════════════════════════════════════════
        // 后置处理：调用后
        // ═════════════════════════════════════════════════════
        long elapsed = System.currentTimeMillis() - startTime;

        log.info("【Filter-后置】方法={} | 耗时={}ms | 结果类型={}",
                methodName, elapsed,
                result.getValue() != null ? result.getValue().getClass().getSimpleName() : "null");

        // 如果耗时超过阈值，可以告警
        if (elapsed > 2000) {
            log.warn("【Filter-慢调用告警】方法={} | 耗时={}ms", methodName, elapsed);
        }

        return result;
    }
}
