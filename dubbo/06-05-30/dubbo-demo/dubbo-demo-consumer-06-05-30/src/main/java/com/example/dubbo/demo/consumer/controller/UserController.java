package com.example.dubbo.demo.consumer.controller;

import com.example.dubbo.demo.api.model.User;
import com.example.dubbo.demo.api.service.UserService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h3>用户查询 HTTP 接口</h3>
 * <p>通过 {@code @DubboReference} 远程调用 Provider 的 {@code UserService} 服务。
 * 对外暴露 {@code GET /user/{id}} REST 端点。</p>
 *
 * <hr>
 *
 * <h4>📋 @DubboReference 全部参数详解 &amp; 端间优先级</h4>
 *
 * <p><b>核心原则：</b>Consumer 端参数覆盖 Provider 端同名参数。
 * Consumer 是调用发起方，它决定超时多久、重试几次、用什么策略。</p>
 *
 * <table border="1" cellpadding="6" style="border-collapse:collapse" summary="DubboReference 全参数表">
 *   <tr style="background:#f0f0f0">
 *     <th>参数</th><th>类型</th><th>默认值</th>
 *     <th>作用</th><th>使用场景</th>
 *     <th>端间谁赢</th>
 *   </tr>
 *   <tr>
 *     <td>{@code version}</td><td>String</td><td>""</td>
 *     <td><b>服务版本号。</b>必须与 {@code @DubboService(version=...)} 一致，否则匹配不上。</td>
 *     <td>灰度时 Provider 同时部署 v1+v2，Consumer 指定 version="2.0.0" 调用新版。</td>
 *     <td>两端必须一致</td>
 *   </tr>
 *   <tr>
 *     <td>{@code group}</td><td>String</td><td>""</td>
 *     <td><b>服务分组。</b>必须与 {@code @DubboService(group=...)} 一致。</td>
 *     <td>同接口拆 "online" / "vip" 两组，Consumer 指定 group 决定调哪组。</td>
 *     <td>两端必须一致</td>
 *   </tr>
 *   <tr>
 *     <td>{@code url}</td><td>String</td><td>""</td>
 *     <td><b>直连地址。</b>绕过注册中心，直接调指定 Provider。</td>
 *     <td>本地开发排查问题：url="dubbo://192.168.1.10:20880" 直接连一台 Provider 调试。</td>
 *     <td>Consumer 独有</td>
 *   </tr>
 *   <tr>
 *     <td>{@code timeout}</td><td>int</td><td>0（由 consumer 全局配置决定）</td>
 *     <td><b>方法调用超时（毫秒）。</b>Consumer 说等多久就等多久，Provider 端的 timeout 不生效。</td>
 *     <td>普通查表 3s，聚合报表 10s，第三方慢接口 30s。Consumer 配的值完全覆盖 Provider。</td>
 *     <td><b>Consumer 赢 ☆</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code retries}</td><td>int</td><td>2</td>
 *     <td><b>失败重试次数。</b>不含首次。Consumer 决定失败后换不换机器重试。</td>
 *     <td>读操作 retries=1~2 提高成功率；<b>写操作必须 retries=0</b>，否则重复扣款无法接受。</td>
 *     <td><b>Consumer 赢 ☆</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code loadbalance}</td><td>String</td><td>"random"</td>
 *     <td><b>负载均衡策略。</b>Consumer 决定按什么规则把请求分配到多台 Provider。</td>
 *     <td>
 *       random=均匀分散；roundrobin=严格轮询；
 *       leastactive=跳过硬机器（自适应）；
 *       consistenthash=同参数走同机器（利好 Provider 本地缓存）。
 *     </td>
 *     <td><b>Consumer 赢 ☆</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code cluster}</td><td>String</td><td>"failover"</td>
 *     <td><b>集群容错策略。</b>调用失败时 Consumer 怎么处理。</td>
 *     <td>failover=换机器重试（读）；failfast=抛异常终止（写）；failsafe=吞异常（日志）；forking=并行调最快返回。</td>
 *     <td><b>Consumer 赢 ☆</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code check}</td><td>boolean</td><td>true</td>
 *     <td><b>启动时检查 Provider 是否可用。</b>true=必须找到 Provider 否则启动失败。</td>
 *     <td>生产 true（尽早发现问题），开发 false（允许先启 Consumer 再启 Provider）。</td>
 *     <td>Consumer 独有</td>
 *   </tr>
 *   <tr>
 *     <td>{@code lazy}</td><td>boolean</td><td>false</td>
 *     <td><b>延迟建立连接。</b>true=首次真正 RPC 调用时才连接 Provider，false=启动时就连。</td>
 *     <td>Consumer 有几十个远程服务但不全用到，lazy=true 减少启动耗时和连接数。</td>
 *     <td>Consumer 独有</td>
 *   </tr>
 *   <tr>
 *     <td>{@code sticky}</td><td>boolean</td><td>false</td>
 *     <td><b>粘滞连接。</b>true=尽量把请求发到上次调用的同台 Provider。</td>
 *     <td>Provider 端有本地缓存（如 Caffeine），sticky=true 提高缓存命中率，减少 cache miss。</td>
 *     <td>Consumer 独有</td>
 *   </tr>
 *   <tr>
 *     <td>{@code async}</td><td>boolean</td><td>false</td>
 *     <td><b>异步调用。</b>true=不阻塞，立即返回 null，调用方通过 {@code RpcContext.getContext().getFuture()} 获取结果。</td>
 *     <td>同时调 3 个无依赖的远程服务：并行发出 → 等全部返回 → 聚合结果，总耗时 = 最慢那个。</td>
 *     <td>Consumer 独有</td>
 *   </tr>
 *   <tr>
 *     <td>{@code sent}</td><td>boolean</td><td>true</td>
 *     <td><b>是否等待消息确认。</b>true=等 Provider 收到并处理完才返回；false=发完就返回（发后即忘）。</td>
 *     <td>日志上报、非关键统计 = false（丢了不心疼）；核心交易 = true（必须确认成功）。</td>
 *     <td>Consumer 独有</td>
 *   </tr>
 *   <tr>
 *     <td>{@code connections}</td><td>int</td><td>0（自动）</td>
 *     <td><b>每 Provider 的最大连接数。</b>Dubbo 协议 TCP 长连接复用，1 个已够。</td>
 *     <td>基本不动。HTTP/REST 协议短连接场景可能需要调大。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code actives}</td><td>int</td><td>0（不限）</td>
 *     <td><b>每方法最大并发调用数。</b>超过则阻塞等待。</td>
 *     <td>防 Consumer 自身突发流量打满 Provider：actives=20 限制 getUserById 最多同时 20 个进行中。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code validation}</td><td>String</td><td>""</td>
 *     <td><b>参数验证。</b>"true"=开启 JSR303，参数无效时不发 RPC。</td>
 *     <td>无效参数在 Consumer 端提前拦截（不发起网络调用），节省一次 RPC 开销。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code cache}</td><td>String</td><td>""</td>
 *     <td><b>结果缓存。</b>lru=LRU 自动淘汰，重复请求直接返回缓存。</td>
 *     <td>短时间内频繁查同 ID（如热点用户），cache=lru 省掉重复 RPC。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code stub}</td><td>String</td><td>""</td>
 *     <td><b>本地存根类名。</b>Consumer 端本地预处理类，需实现同一接口。</td>
 *     <td>调用链：Controller → Stub（参数校验/本地缓存） → RPC 代理 → Provider。无效参数不发起网络调用。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code mock}</td><td>String</td><td>""</td>
 *     <td><b>服务降级。</b>"force:return null"=不调 RPC 直接返回；"fail:return null"=RPC 失败返回。</td>
 *     <td>推荐/广告挂了不能拖垮商品详情主流程。force=彻底降级（省一次网络开销），fail=被动降级。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code tag}</td><td>String</td><td>""</td>
 *     <td><b>标签路由。</b>灰度流量控制，同 {@code @DubboService(tag=...)} 匹配。</td>
 *     <td>Consumer 配 tag="gray-v2"，只访问打了 gray-v2 标签的 Provider。新版灰度控制。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code protocol}</td><td>String[]</td><td>{}</td>
 *     <td><b>指定调用协议。</b>多协议场景选择用 dubbo/tri/rest 哪个调用。</td>
 *     <td>Provider 同时暴露 dubbo + tri，Consumer 设 protocol="tri" 走 HTTP/2 调用。</td>
 *     <td><b>Consumer 赢（2.7.x Consumer 注解可能不支持，用 YAML dubbo.consumer.protocol 替代）</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code init}</td><td>boolean</td><td>true</td>
 *     <td><b>是否 Spring 启动时就初始化代理。</b>true=立即创建代理对象注入到字段。</td>
 *     <td>不用改。false 的话首次调用才建代理，第一次会慢。</td>
 *     <td>Consumer 独有</td>
 *   </tr>
 * </table>
 *
 * <hr>
 *
 * <h4>💡 快速选型指南</h4>
 * <ul>
 *   <li><b>读接口：</b>timeout=3000, retries=2, loadbalance=leastactive, cluster=failover</li>
 *   <li><b>写接口：</b>timeout=5000, retries=0, cluster=failfast</li>
 *   <li><b>非核心：</b>timeout=1000, retries=1, mock="return null", cluster=failsafe</li>
 *   <li><b>本机调试：</b>url="dubbo://192.168.1.10:20880"（跳过注册中心直连）</li>
 *   <li><b>多个远程服务：</b>lazy=true（减少启动耗时），async=true（并行调用）</li>
 * </ul>
 */
@RestController
public class UserController {

    @DubboReference(
            version = "1.0.0",             // [两端一致] 必须匹配 Provider 版本
            group = "demo",                // [两端一致] 必须匹配 Provider 分组
            timeout = 5000,                // [Consumer赢] 超时 5 秒，覆盖 Provider 的 3 秒
            retries = 1,                   // [Consumer赢] 重试 1 次，读操作够用
            loadbalance = "random",        // [Consumer赢] 随机+权重，均匀分散
            cluster = "failover",          // [Consumer赢] 失败换机器重试
            check = false,                 // [Consumer独有] 开发环境不检查 Provider 可用
            lazy = false,                  // [Consumer独有] 不延迟建连
            sticky = false,                // [Consumer独有] 不粘滞
            async = false,                 // [Consumer独有] 同步阻塞等待
            sent = true,                   // [Consumer独有] 等待响应确认
            connections = 1,               // [Consumer赢] 长连接 1 个
            actives = 20,                  // [Consumer赢] 每方法最大并发 20
            validation = "true",           // [Consumer赢] 开启参数验证
            cache = "lru",                 // [Consumer赢] LRU 结果缓存
            mock = "force:return null",    // [Consumer赢] 彻底降级（不回 RPC）
            tag = "gray"                   // [Consumer赢] 标签路由
    )
    private UserService userService;

    @GetMapping("/user/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getUserById(id);
    }
}
