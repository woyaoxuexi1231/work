package work.N15kafka.day1;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class HelloConsumer {

    @KafkaListener(topics = "hello-topic", groupId = "my-group")
    public void listen(String message) {
        System.out.println("Received: " + message);
    }
}