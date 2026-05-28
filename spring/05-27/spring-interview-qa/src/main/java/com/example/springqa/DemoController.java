package com.example.springqa;

import com.example.springqa.Q01_BeanLifecycle.BeanLifecycleDemo;
import com.example.springqa.Q02_CircularDependency.CircularDependencyDemo;
import com.example.springqa.Q03_DependencyInjection.DependencyInjectionDemo;
import com.example.springqa.Q04_FactoryBean.FactoryBeanDemo;
import com.example.springqa.Q05_Scope.ScopeDemo;
import com.example.springqa.Q06_BeanOverride.BeanOverrideDemo;
import com.example.springqa.Q07_ProxySelection.ProxySelectionDemo;
import com.example.springqa.Q08_InternalCallFailure.InternalCallFailureDemo;
import com.example.springqa.Q09_InterceptorChain.InterceptorChainDemo;
import com.example.springqa.Q10_AopPrinciple.AopPrincipleDemo;
import com.example.springqa.Q11_CustomAop.CustomAopDemo;
import com.example.springqa.Q12_TransactionPropagation.TransactionPropagationDemo;
import com.example.springqa.Q13_TransactionFailure.TransactionFailureDemo;
import com.example.springqa.Q14_TransactionPrinciple.TransactionPrincipleDemo;
import com.example.springqa.Q15_DistributedTransaction.DistributedTransactionDemo;
import com.example.springqa.Q16_MvcRequestFlow.MvcRequestFlowDemo;
import com.example.springqa.Q17_ParameterBinding.ParameterBindingDemo;
import com.example.springqa.Q18_InterceptorFilter.InterceptorFilterDemo;
import com.example.springqa.Q19_ExceptionHandling.ExceptionHandlingDemo;
import com.example.springqa.Q20_AsyncRequest.AsyncRequestDemo;
import com.example.springqa.Q21_AutoConfiguration.AutoConfigurationDemo;
import com.example.springqa.Q22_CustomStarter.CustomStarterDemo;
import com.example.springqa.Q23_StartupFlow.StartupFlowDemo;
import com.example.springqa.Q24_ConfigPriority.ConfigPriorityDemo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 统一 REST 入口 —— 每个面试题一个端点。
 *
 * <p>启动后访问：</p>
 * <ul>
 *   <li>GET /                   — 列出所有 24 个端点</li>
 *   <li>GET /q01 ~ GET /q24     — 触发对应面试题的演示</li>
 *   <li>GET /all                — 一次性运行所有演示（慎用）</li>
 * </ul>
 */
@RestController
public class DemoController {

    private final Map<String, Supplier<String>> demos = new LinkedHashMap<>();

    public DemoController(
            BeanLifecycleDemo q01, CircularDependencyDemo q02,
            DependencyInjectionDemo q03, FactoryBeanDemo q04,
            ScopeDemo q05, BeanOverrideDemo q06,
            ProxySelectionDemo q07, InternalCallFailureDemo q08,
            InterceptorChainDemo q09, AopPrincipleDemo q10,
            CustomAopDemo q11, TransactionPropagationDemo q12,
            TransactionFailureDemo q13, TransactionPrincipleDemo q14,
            DistributedTransactionDemo q15, MvcRequestFlowDemo q16,
            ParameterBindingDemo q17, InterceptorFilterDemo q18,
            ExceptionHandlingDemo q19, AsyncRequestDemo q20,
            AutoConfigurationDemo q21, CustomStarterDemo q22,
            StartupFlowDemo q23, ConfigPriorityDemo q24) {

        demos.put("q01", q01::runDemo); demos.put("q02", q02::runDemo);
        demos.put("q03", q03::runDemo); demos.put("q04", q04::runDemo);
        demos.put("q05", q05::runDemo); demos.put("q06", q06::runDemo);
        demos.put("q07", q07::runDemo); demos.put("q08", q08::runDemo);
        demos.put("q09", q09::runDemo); demos.put("q10", q10::runDemo);
        demos.put("q11", q11::runDemo); demos.put("q12", q12::runDemo);
        demos.put("q13", q13::runDemo); demos.put("q14", q14::runDemo);
        demos.put("q15", q15::runDemo); demos.put("q16", q16::runDemo);
        demos.put("q17", q17::runDemo); demos.put("q18", q18::runDemo);
        demos.put("q19", q19::runDemo); demos.put("q20", q20::runDemo);
        demos.put("q21", q21::runDemo); demos.put("q22", q22::runDemo);
        demos.put("q23", q23::runDemo); demos.put("q24", q24::runDemo);
    }

    @GetMapping("/")
    public String index() {
        StringBuilder sb = new StringBuilder();
        sb.append("Spring 面试 24 问 — 代码答题项目\n");
        sb.append("========================================\n\n");
        sb.append("端点列表（GET 请求）：\n\n");
        sb.append("  /q01  Bean 生命周期\n");
        sb.append("  /q02  循环依赖 + 三级缓存\n");
        sb.append("  /q03  @Autowired vs @Resource\n");
        sb.append("  /q04  FactoryBean vs BeanFactory\n");
        sb.append("  /q05  作用域 + @Lookup\n");
        sb.append("  /q06  Bean 覆盖与冲突\n");
        sb.append("  /q07  JDK 动态代理 vs CGLIB\n");
        sb.append("  /q08  内部调用失效 + 三种解法\n");
        sb.append("  /q09  拦截链 + @Order\n");
        sb.append("  /q10  Advisor / Advice / Pointcut\n");
        sb.append("  /q11  自研 AOP 引擎\n");
        sb.append("  /q12  REQUIRED / REQUIRES_NEW / NESTED\n");
        sb.append("  /q13  @Transactional 失效 5+ 场景\n");
        sb.append("  /q14  TransactionSynchronization + afterCommit\n");
        sb.append("  /q15  TCC + 本地消息表\n");
        sb.append("  /q16  DispatcherServlet 请求流程\n");
        sb.append("  /q17  HttpMessageConverter 机制\n");
        sb.append("  /q18  HandlerInterceptor vs Filter\n");
        sb.append("  /q19  @ControllerAdvice 统一异常处理\n");
        sb.append("  /q20  DeferredResult / Callable / SSE\n");
        sb.append("  /q21  spring.factories + 条件注解\n");
        sb.append("  /q22  自定义 Starter 双模块\n");
        sb.append("  /q23  SpringApplication.run() 流程\n");
        sb.append("  /q24  外部化配置优先级 + 多环境\n\n");
        sb.append("/all  一次性运行所有演示\n");
        return sb.toString();
    }

    @GetMapping("/q{number}")
    public String demo(@PathVariable int number) {
        String key = String.format("q%02d", number);
        Supplier<String> demo = demos.get(key);
        if (demo == null) {
            return "没有这个面试题。范围: 1-24";
        }
        return demo.get();
    }

    @GetMapping("/all")
    public String all() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 24; i++) {
            String key = String.format("q%02d", i);
            sb.append(demos.get(key).get()).append("\n\n");
        }
        return sb.toString();
    }
}
