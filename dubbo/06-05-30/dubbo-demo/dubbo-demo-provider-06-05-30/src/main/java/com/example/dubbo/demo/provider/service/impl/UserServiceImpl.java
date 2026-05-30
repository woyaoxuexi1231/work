package com.example.dubbo.demo.provider.service.impl;

import com.example.dubbo.demo.api.model.User;
import com.example.dubbo.demo.api.service.UserService;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * <h3>用户服务实现</h3>
 * <p>通过 {@code @DubboService} 暴露为远程 RPC 服务，注册到 Nacos，等待 Consumer 调用。</p>
 *
 * <hr>
 *
 * <h4>📋 @DubboService 全部参数详解 &amp; 端间优先级</h4>
 *
 * <p><b>端间优先级规则：</b>Consumer 端 &gt; Provider 端 —— Consumer 是调用发起者，
 * 它决定超时、重试等策略。Provider 端的值只作为"服务端建议默认值"。</p>
 *
 * <table border="1" cellpadding="6" style="border-collapse:collapse" summary="DubboService 全参数表">
 *   <tr style="background:#f0f0f0">
 *     <th>参数</th><th>类型</th><th>默认值</th>
 *     <th>作用</th><th>使用场景</th>
 *     <th>端间谁赢</th>
 *   </tr>
 *   <tr>
 *     <td>{@code version}</td><td>String</td><td>""</td>
 *     <td><b>服务版本号。</b>多版本共存时 Consumer 按 version 选择调用哪个版本。</td>
 *     <td>灰度发布：v1 稳定版对老用户，v2 灰度版对新用户。两端 version 必须一致才能匹配。</td>
 *     <td>两端必须一致</td>
 *   </tr>
 *   <tr>
 *     <td>{@code group}</td><td>String</td><td>""</td>
 *     <td><b>服务分组。</b>同一接口按不同逻辑隔离，Consumer 选择调哪一组。</td>
 *     <td>UserService 拆 "online"（线上用户）和 "vip"（VIP 用户），配置中心下发路由规则。</td>
 *     <td>两端必须一致</td>
 *   </tr>
 *   <tr>
 *     <td>{@code timeout}</td><td>int</td><td>0（由 provider 全局配置决定）</td>
 *     <td><b>方法调用超时（毫秒）。</b>超过此时间没收到响应则抛超时异常。</td>
 *     <td>简单查表 3s，复杂报表 10s，调用第三方 30s。优先在 Consumer 端配，因为 Consumer 知道自己能等多久。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code retries}</td><td>int</td><td>2</td>
 *     <td><b>失败重试次数。</b>不含第 1 次。retries=2 表示最多总请求 3 次。</td>
 *     <td>读接口（查用户/查商品）可重试 2 次；写接口（下单/扣款/发券）<b>必须设为 0</b>，否则可能重复扣款。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code loadbalance}</td><td>String</td><td>"random"</td>
 *     <td><b>负载均衡策略。</b>决定请求发到哪台 Provider。</td>
 *     <td>
 *       random=随机+权重，通用场景；roundrobin=轮询，请求均匀分配；
 *       leastactive=最少活跃数，慢机器自动少接请求；
 *       consistenthash=一致性哈希，同参数走同机器，利好 Provider 端本地缓存。
 *     </td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code cluster}</td><td>String</td><td>"failover"</td>
 *     <td><b>集群容错策略。</b>调用失败时的处理方式。</td>
 *     <td>
 *       failover=换台机器重试（读用）；failfast=抛异常（写用）；
 *       failsafe=吞异常返回空（日志用）；failback=后台定时重发（通知用）；
 *       forking=并行调多台（实时要求极高）；broadcast=广播所有（刷新缓存）。
 *     </td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code weight}</td><td>int</td><td>100</td>
 *     <td><b>负载均衡权重。</b>权重越大被分配到的请求越多。</td>
 *     <td>4C8G 机器 weight=100，8C16G 机器 weight=200，高配机器承接更多流量。</td>
 *     <td><b>Provider 独有</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code executes}</td><td>int</td><td>0（不限）</td>
 *     <td><b>服务端最大并发执行数。</b>超过此数的请求排队或抛异常。Provider 端硬限流。</td>
 *     <td>保护数据库连接池：接口 SQL 查询耗时 200ms，设 executes=50 防止 500 个请求同时打崩数据库。</td>
 *     <td><b>Provider 独有</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code actives}</td><td>int</td><td>0（不限）</td>
 *     <td><b>每个 Consumer 的每方法最大并发数。</b>防止单个 Consumer 突发流量打满线程池。</td>
 *     <td>某 Consumer 调用 getUserById 的并发限制为 20，超过的请求阻塞等待。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code connections}</td><td>int</td><td>0（自动）</td>
 *     <td><b>每个 Consumer 到此 Provider 的最大连接数。</b>Dubbo 协议长连接，1 个足矣。</td>
 *     <td>极少数情况需要调大：HTTP 协议短连接可能需要多个连接池。Dubbo 协议一般不动。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code delay}</td><td>int</td><td>0</td>
 *     <td><b>延迟暴露（毫秒）。</b>-1 = Spring 容器初始化完成后暴露。推荐 -1。</td>
 *     <td>服务依赖数据库连接池/Redis 连接等资源，要等这些 Bean 初始化完再暴露，避免 Consumer 调过来就报错。</td>
 *     <td><b>Provider 独有</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code token}</td><td>String</td><td>""</td>
 *     <td><b>令牌验证。</b>"true"/"false"或自定义值。Consumer 请求需携带匹配 token。</td>
 *     <td>内网安全要求高，只允许带特定 token 的应用调用，防止未授权的 Consumer 直接发现并调用。</td>
 *     <td><b>Provider 独有</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code validation}</td><td>String</td><td>""</td>
 *     <td><b>参数验证。</b>"true"=开启 JSR303 Bean Validation，实体类加 @NotNull/@Min 等注解生效。</td>
 *     <td>UserService.createUser 的参数 User 标注了 @NotNull，Consumer 传 null 时 Dubbo 拒绝调用并抛 ConstraintViolationException。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code cache}</td><td>String</td><td>""</td>
 *     <td><b>结果缓存策略。</b>lru=LRU 淘汰 / threadlocal=线程缓存 / jcache=JCache。</td>
 *     <td>省市区列表、字典数据等频繁查、不常改、允许短暂不一致的数据，在 Provider 端缓存结果。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code stub}</td><td>String</td><td>""</td>
 *     <td><b>本地存根类名。</b>实现同一接口，在 Consumer 端 RPC 前执行预处理（如参数校验、本地缓存命中）。</td>
 *     <td>Controller调用→Stub校验参数→RPC→Provider。无效参数不发起网络调用。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code mock}</td><td>String</td><td>""</td>
 *     <td><b>服务降级。</b>"force:return null"=不调 RPC 直接返回 null / "fail:return null"=失败返回 null。</td>
 *     <td>推荐/广告等非核心服务挂了，不能影响商品详情主流程。force=彻底降级，fail=失败才降级。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code tag}</td><td>String</td><td>""</td>
 *     <td><b>标签路由。</b>灰度发布流量控制。给 Provider 打标签，Consumer 指定 tag 访问灰度版本。</td>
 *     <td>新版本 Provider 打 tag="gray-v2"，灰度 Consumer 指定 tag="gray-v2" 只访问新版。Dubbo 3 标签路由特性。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code protocol}</td><td>String[]</td><td>{}</td>
 *     <td><b>指定使用的协议。</b>多协议场景（dubbo + tri + rest）时选择用哪个。</td>
 *     <td>同时暴露 dubbo:// 和 tri://，@DubboService(protocol="dubbo") 用 dubbo 暴露。</td>
 *     <td><b>Provider 独有</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code async}</td><td>boolean</td><td>false</td>
 *     <td><b>是否异步执行。</b>true=方法内部另开线程执行，立即返回 null，Consumer 通过 CompletableFuture 获取结果。</td>
 *     <td>Provider 处理耗时 5s，设为异步释放 IO 线程，不影响处理其他请求。</td>
 *     <td>两端都有，Consumer 赢</td>
 *   </tr>
 * </table>
 *
 * <hr>
 *
 * <h4>💡 快速选型指南</h4>
 * <ul>
 *   <li><b>读接口（查询）：</b>timeout=3000, retries=2, cluster=failover</li>
 *   <li><b>写接口（下单/扣款）：</b>timeout=5000, retries=0, cluster=failfast</li>
 *   <li><b>非核心（日志/通知）：</b>timeout=1000, retries=1, cluster=failsafe, mock="return null"</li>
 *   <li><b>灰度发布：</b>version 区分 v1/v2 + tag 标签路由控制流量</li>
 *   <li><b>防止打崩 DB：</b>executes=50, actives=20</li>
 * </ul>
 */
@DubboService(
        version = "1.0.0",            // [两端一致] 服务版本，灰度时按版本分流
        group = "demo",               // [两端一致] 服务分组，同接口不同实现隔离
        timeout = 3000,               // [Consumer赢] 超时 3 秒，查库足够
        retries = 2,                  // [Consumer赢] 读操作可重试 2 次（写操作必须 0）
        weight = 100,                 // [Provider独有] 默认权重 100，高配机器调大
        loadbalance = "random",       // [Consumer赢] 随机+权重，均匀分配
        cluster = "failover",         // [Consumer赢] 失败后换机器重试
        executes = 100,               // [Provider独有] 最大并发 100，保护数据库
        actives = 0,                  // [Consumer赢] 不限制每连接并发
        connections = 1,              // [Consumer赢] 长连接 1 个就够
        delay = -1,                   // [Provider独有] -1 = Spring 容器就绪后暴露
        validation = "true",          // [Consumer赢] 开启参数验证
        cache = "lru",                // [Consumer赢] LRU 结果缓存
        mock = "force:return null",   // [Consumer赢] 彻底降级返回 null
        token = "true",                 // [Provider独有] 开启令牌验证（String 类型 "true"/"false"/自定义值）
        tag = "gray",                 // [Consumer赢] 标签路由（灰度）
        protocol = {"dubbo"}          // [Provider独有] 使用 dubbo 协议暴露
)
@Component
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Override
    public User getUserById(Long id) {
        log.info(">>> [Provider] getUserById({})", id);
        User user = new User(id, "用户" + id, "user" + id + "@example.com");
        log.info("<<< [Provider] 返回: {}", user);
        return user;
    }
}
