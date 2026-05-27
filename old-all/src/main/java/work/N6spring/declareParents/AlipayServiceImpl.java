package work.N6spring.declareParents;

import org.springframework.stereotype.Service;

// 支付服务实现类
@Service
public class AlipayServiceImpl implements PayService {
    @Override
    public void pay() {
        System.out.println("支付宝支付成功...");
    }
}