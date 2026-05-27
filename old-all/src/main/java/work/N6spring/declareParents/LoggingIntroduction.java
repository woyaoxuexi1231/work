package work.N6spring.declareParents;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.DeclareParents;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingIntroduction {

    // 核心配置：将 Loggable 接口引入到所有 PayService 实现类上
    /*
    @DeclareParents 注解声明了 PayService 接口及其实现类都将 "混入" Loggable 接口。
    value = "com.example.service.PayService+"：其中 + 号表示匹配 PayService 接口本身及其所有实现类。
    defaultImpl = DefaultLoggableImpl.class：指明 Loggable 接口的方法调用将委托给此默认实现类。
    public static Loggable loggable：该静态字段的类型 Loggable 告诉 Spring AOP 要引入的目标接口。
     */
    @DeclareParents(value = "work.N6spring.declareParents.PayService+", defaultImpl = DefaultLoggableImpl.class)
    public static Loggable loggable; // 静态字段类型决定了要引入的接口
}