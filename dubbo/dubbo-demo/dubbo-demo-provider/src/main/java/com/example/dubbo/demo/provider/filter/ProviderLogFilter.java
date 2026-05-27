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

/**
 * <h3>Provider 端自定义过滤器 — 演示 Dubbo Filter 拦截链机制</h3>
 *
 * <p>
 * Dubbo 的 <b>Filter 链</b> 是典型的 <b>责任链模式</b> 应用。
 * 每个 Filter 在请求处理的前后执行自定义逻辑，类似于 Servlet Filter 或 Spring Interceptor。
 * </p>
 *
 * <h4>Filter 执行流程</h4>
 * <pre>
 * Consumer                                     Provider
 *    |                                            |
 *    |-- RPC 请求 --------------------------------→|
 *    |                                            |
 *    |                                  ┌─────────────────────┐
 *    |                                  │ Filter1.invoke()    │  ← 前置处理
 *    |                                  │   └→ Filter2.invoke()│
 *    |                                  │       └→ Filter3.... │
 *    |                                  │           └→ 业务方法 │
 *    |                                  │       ←  Filter3.... │  ← 后置处理
 *    |                                  │   ←  Filter2.invoke()│
 *    |                                  │ ←  Filter1.invoke()  │
 *    |                                  └─────────────────────┘
 *    |                                            |
 *    |←-- RPC 响应 --------------------------------|
 * </pre>
 *
 * <h4>@Activate 注解说明</h4>
 * <table border="1">
 *   <tr><th>属性</th><th>含义</th></tr>
 *   <tr>
 *     <td>{@code group = CommonConstants.PROVIDER}</td>
 *     <td>仅在 Provider 端激活（也可指定 {@code CONSUMER} 或两者）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code order = 100}</td>
 *     <td>执行顺序 — 数值越小越先执行（越小越外层）</td>
 *   </tr>
 * </table>
 *
 * <h4>Dubbo SPI 知识点</h4>
 * <p>
 * 要让此 Filter 生效，还需要在
 * {@code META-INF/dubbo/org.apache.dubbo.rpc.Filter} 文件中声明：
 * <pre>
 * providerLogFilter=com.example.dubbo.demo.provider.filter.ProviderLogFilter
 * </pre>
 * Dubbo 的 {@link org.apache.dubbo.common.extension.ExtensionLoader}
 * 会在启动时扫描此文件并自动加载。
 * </p>
 *
 * @author Dubbo Demo Team
 * @version 1.0.0
 * @see Filter
 * @see org.apache.dubbo.common.extension.Activate
 */
@Activate(
        group = CommonConstants.PROVIDER,  // 仅 Provider 端激活
        order = 100                        // 执行顺序
)
public class ProviderLogFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ProviderLogFilter.class);

    /**
     * 拦截每一次 RPC 调用。
     *
     * @param invoker     服务实现类的 Invoker 包装
     * @param invocation  RPC 调用信息（方法名、参数类型、参数值、attachments 等）
     * @return 调用结果
     * @throws RpcException 调用过程中的异常
     */
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // ---- 前置处理 ----
        long startTime = System.currentTimeMillis();

        // 从 RpcContext 获取 Consumer 端信息（隐式传参）
        String consumerIp = RpcContext.getContext().getRemoteHost();
        String consumerApp = RpcContext.getContext().getAttachment("consumerApp");

        log.info("[Filter-前置] 收到调用: service={}, method={}, consumerIp={}, consumerApp={}",
                invocation.getServiceName(),
                invocation.getMethodName(),
                consumerIp,
                consumerApp);

        // ---- 调用下一个 Filter / 最终业务方法 ----
        Result result;
        try {
            result = invoker.invoke(invocation);
        } catch (RpcException e) {
            log.error("[Filter] 调用异常: {}", e.getMessage(), e);
            throw e;
        }

        // ---- 后置处理 ----
        long elapsed = System.currentTimeMillis() - startTime;

        log.info("[Filter-后置] 调用完成: method={}, 耗时={}ms, 结果={}",
                invocation.getMethodName(),
                elapsed,
                result.getValue());

        return result;
    }
}
