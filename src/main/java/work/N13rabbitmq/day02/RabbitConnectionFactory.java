package work.N13rabbitmq.day02;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * 工业级 RabbitMQ 连接工厂封装
 * 重点：心跳设置、线程池自定义、自动重连配置
 */
public class RabbitConnectionFactory {

    private static final String HOST = "localhost";
    private static final int PORT = 5672;
    private static final String USER = "guest";
    private static final String PASS = "guest";

    /**
     * 获取连接工厂，配置生产环境参数
     */
    public static ConnectionFactory getFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setPort(PORT);
        factory.setUsername(USER);
        factory.setPassword(PASS);

        // 1. 设置心跳时间 (单位：秒)
        // 建议 10-30s，太长会导致连接失效感知慢，太短会增加网络负担
        factory.setRequestedHeartbeat(30);

        // 2. 启用自动连接恢复 (Automatic Recovery)
        // 当网络抖动或 MQ 重启时，客户端会自动尝试重新连接
        factory.setAutomaticRecoveryEnabled(true);
        // 设置重连间隔时间 (5秒)
        factory.setNetworkRecoveryInterval(5000);
        // 自动恢复拓扑 (Queue, Exchange, Binding 会在重连后自动重新声明)
        factory.setTopologyRecoveryEnabled(true);

        // 3. 设置连接超时时间 (单位：毫秒)
        factory.setConnectionTimeout(60000);

        return factory;
    }

    /**
     * 创建连接，并关联自定义线程池
     * 工业级要求：不要使用默认线程池，避免消费者回调阻塞影响连接管理
     */
    public static Connection getConnection() throws IOException, TimeoutException {
        ConnectionFactory factory = getFactory();
        
        // 自定义线程池，建议核心线程数根据业务并发量定
        ExecutorService es = Executors.newFixedThreadPool(10);
        
        return factory.newConnection(es);
    }
}
