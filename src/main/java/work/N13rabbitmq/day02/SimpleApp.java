package work.N13rabbitmq.day02;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Day 02 地狱实战：工业级具备韧性的生产者应用
 * 重点：自动重连监控、ShutdownHook、优雅关闭
 */
public class SimpleApp {

    private static final String QUEUE_NAME = "day02_simple_queue";

    public static void main(String[] args) {
        Connection connection = null;
        Channel channel = null;

        try {
            // 1. 获取连接 (已封装自动恢复配置)
            connection = RabbitConnectionFactory.getConnection();

            // 2. 监听连接关闭信号 (用于诊断网络/集群问题)
            connection.addShutdownListener(new ShutdownListener() {
                @Override
                public void shutdownCompleted(ShutdownSignalException cause) {
                    if (cause.isHardError()) {
                        System.err.println("❌ [硬错误] 连接由于网络故障或服务器关闭而中断！原因：" + cause.getMessage());
                    } else {
                        System.out.println("✅ [正常关闭] 连接由于程序主动关闭而结束。");
                    }
                }
            });

            // 3. 创建 Channel (严禁在多线程间共享！)
            channel = connection.createChannel();

            // 4. 声明队列 (即使连接恢复后，RabbitMQ 客户端会自动根据拓扑恢复策略重新声明)
            channel.queueDeclare(QUEUE_NAME, true, false, false, null);

            // 5. 注册优雅关闭 Hook
            final Connection finalConnection = connection;
            final Channel finalChannel = channel;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("⚠️ JVM 正在退出，执行优雅关闭逻辑...");
                try {
                    if (finalChannel != null && finalChannel.isOpen()) {
                        finalChannel.close();
                    }
                    if (finalConnection != null && finalConnection.isOpen()) {
                        finalConnection.close();
                    }
                    System.out.println("✅ 资源已完全释放。");
                } catch (IOException | TimeoutException e) {
                    System.err.println("❌ 关闭资源时出错：" + e.getMessage());
                }
            }));

            // 6. 模拟持续生产消息，演示自动重连过程
            int count = 0;
            System.out.println("🚀 开始发送消息，尝试停止容器测试重连机制...");
            while (true) {
                String message = "Day 02 消息序列: " + count++;
                try {
                    // 发送消息
                    channel.basicPublish("", QUEUE_NAME, null, message.getBytes(StandardCharsets.UTF_8));
                    System.out.println("📤 [SENT] " + message);
                } catch (IOException e) {
                    // 只有在 Automatic Recovery 无法瞬间恢复且发送缓冲区满时才会抛出
                    System.err.println("❌ 发送失败，正在等待自动恢复中...");
                }

                TimeUnit.SECONDS.sleep(2);
            }

        } catch (IOException | TimeoutException | InterruptedException e) {
            System.err.println("🔥 启动失败或发生不可逆异常: " + e.getMessage());
        }
    }
}
