package com.example.dubbo.demo.provider.service.impl;

import com.example.dubbo.demo.api.model.User;
import com.example.dubbo.demo.api.service.UserService;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * <h3>用户服务实现</h3>
 * <p>通过 {@code @DubboService} 暴露为远程 RPC 服务。</p>
 *
 * <hr>
 *
 * <h4>📋 @DubboService 全部参数详解 &amp; 端间优先级</h4>
 *
 * <p><b>端间规则：</b>同名参数 Consumer 端值覆盖 Provider 端值。
 * Consumer 是调用发起方，它决定超时、重试等策略。</p>
 *
 * <table border="1" cellpadding="6" style="border-collapse:collapse" summary="DubboService 全参数表">
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
 *     <td><b>服务版本号。</b>多版本共存时 Consumer 按 version 选调哪个版。</td>
 *     <td>灰度发布：v1 稳定版对老用户，v2 灰度版对新用户。两端必须一致。</td>
 *     <td>两端必须一致</td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code group}</td><td nowrap>String</td><td nowrap>""</td>
 *     <td><b>服务分组。</b>同接口不同实现逻辑隔离。</td>
 *     <td>UserService 拆 "online" 和 "vip" 两组，Consumer 选调哪组。</td>
 *     <td>两端必须一致</td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code timeout}</td><td nowrap>int</td><td nowrap>0（由全局决定）</td>
 *     <td><b>调用超时（毫秒）。</b>超过此时间没响应就抛超时异常。</td>
 *     <td>简单查表 3s，复杂报表 10s，第三方 30s。Consumer 端配更合理。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code retries}</td><td nowrap>int</td><td nowrap>2</td>
 *     <td><b>失败重试次数。</b>不含第 1 次。retries=2 = 最多总请求 3 次。</td>
 *     <td>读操作可重试；写操作（下单/扣款）<b>必须 0</b>，否则可能重复扣款。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code loadbalance}</td><td nowrap>String</td><td nowrap>"random"</td>
 *     <td><b>负载均衡策略。</b>请求发到哪台 Provider。</td>
 *     <td>random=随机+权重（通用）；roundrobin=轮询均分；leastactive=慢机器少接；consistenthash=同参同机器。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code cluster}</td><td nowrap>String</td><td nowrap>"failover"</td>
 *     <td><b>集群容错。</b>调用失败后的处理方式。</td>
 *     <td>failover=换机器重试（读）；failfast=抛异常（写）；failsafe=吞异常（日志）；forking=并行调多台。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code weight}</td><td nowrap>int</td><td nowrap>100</td>
 *     <td><b>负载权重。</b>权重越大分配到的请求越多。</td>
 *     <td>4C8G 机器 weight=100，8C16G 机器 weight=200，高配多接流量。</td>
 *     <td><b>Provider 独有</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code executes}</td><td nowrap>int</td><td nowrap>0（不限）</td>
 *     <td><b>服务端最大并发。</b>Provider 端硬限流，超限请求排队。</td>
 *     <td>保护数据库：设 executes=50 防 500 个请求同时打崩 DB 连接池。</td>
 *     <td><b>Provider 独有</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code actives}</td><td nowrap>int</td><td nowrap>0（不限）</td>
 *     <td><b>每方法最大并发。</b>防单个 Consumer 突发流量打满线程池。</td>
 *     <td>某 Consumer 调 getUserById 并发限制 20，超限阻塞等待。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code delay}</td><td nowrap>int</td><td nowrap>0</td>
 *     <td><b>延迟暴露。</b>-1 = Spring 容器初始化完再暴露。推荐 -1。</td>
 *     <td>服务要等 DB/Redis 连接初始化完再暴露，避免 Consumer 调过来就报错。</td>
 *     <td><b>Provider 独有</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code token}</td><td nowrap>String</td><td nowrap>""</td>
 *     <td><b>令牌验证。</b>"true"/自定义值，Consumer 请求需携带匹配 token。</td>
 *     <td>安全要求高时，只允许带特定 token 的应用调用，防未授权 Consumer 直连。</td>
 *     <td><b>Provider 独有</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code validation}</td><td nowrap>String</td><td nowrap>""</td>
 *     <td><b>参数验证。</b>"true"=开启 JSR303，实体类加 @NotNull/@Min 生效。</td>
 *     <td>参数无效时 Dubbo 拒绝调用并抛 ConstraintViolationException。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code cache}</td><td nowrap>String</td><td nowrap>""</td>
 *     <td><b>结果缓存。</b>lru=LRU / threadlocal=线程缓存 / jcache=JCache。</td>
 *     <td>省市区列表等频繁查、不常改的数据，在 Provider 端缓存结果。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code stub}</td><td nowrap>String</td><td nowrap>""</td>
 *     <td><b>本地存根类名。</b>Consumer 端 RPC 前预处理。</td>
 *     <td>Controller→Stub(参数校验/本地缓存)→RPC→Provider。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code mock}</td><td nowrap>String</td><td nowrap>""</td>
 *     <td><b>服务降级。</b>"force:return null"=不调 RPC 直接返回。</td>
 *     <td>推荐/广告挂了不能影响商品详情主流程。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code tag}</td><td nowrap>String</td><td nowrap>""</td>
 *     <td><b>标签路由。</b>灰度流量控制。</td>
 *     <td>新版打 tag="gray-v2"，灰度 Consumer 指定 tag="gray-v2" 只访新版。</td>
 *     <td><b>Consumer 赢</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code protocol}</td><td nowrap>String[]</td><td nowrap>{}</td>
 *     <td><b>指定协议。</b>多协议时选 dubbo / tri / rest 哪个暴露。</td>
 *     <td>同时暴露 dubbo:// 和 tri://，protocol="dubbo" 用 dubbo 暴露。</td>
 *     <td><b>Provider 独有</b></td>
 *   </tr>
 *   <tr>
 *     <td nowrap>{@code async}</td><td nowrap>boolean</td><td nowrap>false</td>
 *     <td><b>是否异步。</b>true=另开线程执行，立即返回，Consumer 靠 CompletableFuture 取结果。</td>
 *     <td>Provider 处理耗时 5s，异步释放 IO 线程。</td>
 *     <td>两端都有，Consumer 赢</td>
 *   </tr>
 * </table>
 *
 * <hr>
 *
 * <p><b>实战选型速查</b> —— 读接口 timeout=3000, retries=2, cluster=failover
 * | 写接口 timeout=5000, retries=0, cluster=failfast
 * | 非核心 mock="return null" | 灰度 tag / version | 防打崩 executes=50</p>
 */

// ========== 注解仅保留最核心参数，完整参数表见上方 Javadoc ==========
@DubboService(version = "1.0.0", group = "demo")
@Component
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Value("${dubbo.protocols.dubbo.port}")
    private String port;

    @Override
    public User getUserById(Long id) {
        log.info(">>> [Provider] getUserById({})", id);
        User user = new User(id, "用户" + id + "-" + port, "user" + id + "@example.com");
        // try { Thread.sleep(3100); } catch (InterruptedException e) {}
        log.info("<<< [Provider] 返回: {}", user);
        return user;
    }
}
