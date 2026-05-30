package com.example.dubbo.demo.consumer.controller;

import com.example.dubbo.demo.api.model.User;
import com.example.dubbo.demo.api.service.UserService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h3>用户查询 HTTP 接口</h3>
 * <p>通过 {@code @DubboReference} 远程调用 Provider。</p>
 *
 * <hr>
 *
 * <h4>📋 @DubboReference 全部参数详解 &amp; 端间优先级</h4>
 *
 * <p><b>核心原则：</b>Consumer 端参数覆盖 Provider 端同名参数。
 * Consumer 是调用发起方，它决定超时、重试等策略。</p>
 *
 * <table border="1" cellpadding="6" style="border-collapse:collapse" summary="DubboReference 全参数表">
 *   <colgroup>
 *     <col width="100">
 *     <col width="70">
 *     <col width="90">
 *   </colgroup>
 *   <tr style="background:#f0f0f0">
 *     <th nowrap>参数</th><th nowrap>类型</th><th nowrap>默认值</th>
 *     <th>作用</th><th>使用场景</th>
 *     <th>端间谁赢</th>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code version}</td><td nowrap>String</td><td nowrap>""</td>
 *     <td><b>服务版本号。</b>必须与 {@code @DubboService(version)} 一致。</td>
 *     <td>灰度：v1+v2 同时在线，Consumer 指定 version="2.0.0" 调新版。</td>
 *     <td>两端必须一致</td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code group}</td><td nowrap>String</td><td nowrap>""</td>
 *     <td><b>服务分组。</b>必须与 {@code @DubboService(group)} 一致。</td>
 *     <td>同接口拆 "online"/"vip" 两组，Consumer 选调哪组。</td>
 *     <td>两端必须一致</td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code url}</td><td nowrap>String</td><td nowrap>""</td>
 *     <td><b>直连地址。</b>绕过注册中心直接调指定 Provider。</td>
 *     <td>本地开发排查：url="dubbo://192.168.1.10:20880"</td>
 *     <td>Consumer 独有</td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code timeout}</td><td nowrap>int</td><td nowrap>0（由全局决定）</td>
 *     <td><b>超时（毫秒）。</b>Consumer 决定等多久，覆盖 Provider 端。</td>
 *     <td>普通查表 3s，聚合报表 10s，第三方慢接口 30s。</td>
 *     <td><b>Consumer 赢 ☆</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code retries}</td><td nowrap>int</td><td nowrap>2</td>
 *     <td><b>重试次数。</b>不含首次。Consumer 决定失败后换不换机器重试。</td>
 *     <td>读操作 1~2；<b>写操作必须 0</b>，否则重复扣款。</td>
 *     <td><b>Consumer 赢 ☆</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code loadbalance}</td><td nowrap>String</td><td nowrap>"random"</td>
 *     <td><b>负载均衡。</b>Consumer 决定请求分配规则。</td>
 *     <td>random=均匀；roundrobin=轮询；leastactive=跳过慢机器；consistenthash=同参同机。</td>
 *     <td><b>Consumer 赢 ☆</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code cluster}</td><td nowrap>String</td><td nowrap>"failover"</td>
 *     <td><b>集群容错。</b>调用失败后怎么处理。</td>
 *     <td>failover=换机器（读）；failfast=抛异常（写）；failsafe=吞异常（日志）。</td>
 *     <td><b>Consumer 赢 ☆</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code check}</td><td nowrap>boolean</td><td nowrap>true</td>
 *     <td><b>启动检查。</b>true=找不到 Provider 启动失败。</td>
 *     <td>生产 true（早发现）；开发 false（先启 Consumer 再启 Provider）。</td>
 *     <td>Consumer 独有</td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code lazy}</td><td nowrap>boolean</td><td nowrap>false</td>
 *     <td><b>延迟连接。</b>true=首次调用才建连，false=启动就建。</td>
 *     <td>有几十个远程服务但不全用，lazy=true 减少启动耗时。</td>
 *     <td>Consumer 独有</td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code sticky}</td><td nowrap>boolean</td><td nowrap>false</td>
 *     <td><b>粘滞连接。</b>true=尽量发同一台 Provider。</td>
 *     <td>Provider 有本地缓存时开，提高命中率。</td>
 *     <td>Consumer 独有</td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code async}</td><td nowrap>boolean</td><td nowrap>false</td>
 *     <td><b>异步调用。</b>true=不阻塞立即返回，通过 Future 取结果。</td>
 *     <td>同时调多个无依赖服务，并行等待聚合。</td>
 *     <td>Consumer 独有</td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code sent}</td><td nowrap>boolean</td><td nowrap>true</td>
 *     <td><b>等待确认。</b>true=等响应；false=发后即忘。</td>
 *     <td>日志上报用 false（丢了不心疼）；核心交易用 true。</td>
 *     <td>Consumer 独有</td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code actives}</td><td nowrap>int</td><td nowrap>0（不限）</td>
 *     <td><b>每方法最大并发。</b>超限阻塞。</td>
 *     <td>actives=20 防 Consumer 自身突发流量打满 Provider。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code validation}</td><td nowrap>String</td><td nowrap>""</td>
 *     <td><b>参数验证。</b>"true"=开启，无效参数不发 RPC。</td>
 *     <td>Consumer 端提前拦截无效参数，省一次网络开销。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code cache}</td><td nowrap>String</td><td nowrap>""</td>
 *     <td><b>结果缓存。</b>lru=LRU，重复请求直接返回。</td>
 *     <td>短时频繁查同 ID，缓存省掉重复 RPC。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code stub}</td><td nowrap>String</td><td nowrap>""</td>
 *     <td><b>本地存根。</b>Consumer 端预处理类。</td>
 *     <td>Controller→Stub(校验/缓存)→RPC→Provider。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code mock}</td><td nowrap>String</td><td nowrap>""</td>
 *     <td><b>服务降级。</b>"force:return null"=不调 RPC 直接返回。</td>
 *     <td>非核心服务挂了不影响主流程。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code tag}</td><td nowrap>String</td><td nowrap>""</td>
 *     <td><b>标签路由。</b>灰度控制。</td>
 *     <td>tag="gray-v2" 只访问打了同样标签的 Provider。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 * </table>
 *
 * <hr>
 *
 * <p><b>实战选型速查</b> —— 读接口 timeout+retries+leastactive
 * | 写接口 retries=0+failfast | 非核心 mock | 本机调试 url 直连</p>
 */
@RestController
public class UserController {

    // ========== 注解仅保留最核心参数，完整参数表见上方 Javadoc ==========
    @DubboReference(version = "1.0.0", group = "demo", check = false)
    private UserService userService;

    @GetMapping("/user/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getUserById(id);
    }
}
