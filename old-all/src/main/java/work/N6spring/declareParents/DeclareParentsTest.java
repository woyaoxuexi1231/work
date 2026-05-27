package work.N6spring.declareParents;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * @author hulei
 * @since 2026/5/27 15:03
 */

@Component
public class DeclareParentsTest implements ApplicationContextAware {

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // 1. 获取支付服务Bean
        PayService payService = applicationContext.getBean(PayService.class);

        // 2. 调用原有的支付功能
        payService.pay();

        // 3. 将支付服务Bean强制转换为Loggable接口，并调用日志功能
        if (payService instanceof Loggable) {
            Loggable loggable = (Loggable) payService;
            loggable.log("用户完成一笔支付宝支付。");
        }
    }
}
