// CustomRebalanceListener.java
package com.example.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.springframework.kafka.support.KafkaUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CustomRebalanceListener implements ConsumerAwareRebalanceListener {

    private static final Logger log = LoggerFactory.getLogger(CustomRebalanceListener.class);
    // 用于在外部存储中追踪每个分区的偏移量，这里用本地 Map 模拟
    private final Map<TopicPartition, Long> offsetStore = new ConcurrentHashMap<>();

    @Override
    public void onPartitionsRevokedBeforeCommit(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        log.info("分区即将撤销，在提交偏移量之前执行。Partitions: {}", partitions);
    }

    @Override
    public void onPartitionsRevokedAfterCommit(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        log.info("分区撤销完成，待提交偏移量已提交。Partitions: {}", partitions);
        // 在这里将所有待提交的偏移量保存到外部存储
        for (TopicPartition partition : partitions) {
            long currentPosition = consumer.position(partition);
            offsetStore.put(partition, currentPosition);
            log.info("Saved offset {} for partition {}", currentPosition, partition);
        }
    }

    @Override
    public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        log.info("新分区已分配。Partitions: {}", partitions);
        for (TopicPartition partition : partitions) {
            Long savedOffset = offsetStore.get(partition);
            if (savedOffset != null) {
                log.info("Restoring to saved offset {} for partition {}", savedOffset, partition);
                consumer.seek(partition, savedOffset);
            } else {
                log.info("No saved offset found for partition {}. Starting from configured policy.", partition);
            }
        }
    }

    @Override
    public void onPartitionsLost(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        log.warn("分区已丢失，这是非自愿的再均衡导致的。Partitions: {}", partitions);
        // 在分区丢失（例如由于超时）时，通常无需立即提交偏移量
    }
}