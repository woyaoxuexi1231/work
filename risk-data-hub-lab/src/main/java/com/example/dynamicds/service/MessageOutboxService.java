package com.example.dynamicds.service;

import com.example.dynamicds.datasource.RoutingJdbcExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class MessageOutboxService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RoutingJdbcExecutor jdbcExecutor;
    private final LeafSegmentService leafSegmentService;

    public MessageOutboxService(RoutingJdbcExecutor jdbcExecutor, LeafSegmentService leafSegmentService) {
        this.jdbcExecutor = jdbcExecutor;
        this.leafSegmentService = leafSegmentService;
    }

    public long publish(String topic, String bizKey, String payload) {
        long messageId = leafSegmentService.nextId("event_message");
        jdbcExecutor.run(PlatformBootstrapService.DS_WAREHOUSE, jdbc -> jdbc.update(
                "insert into event_message(message_id, topic, biz_key, payload, status, created_at) values (?,?,?,?,?,?)",
                messageId, topic, bizKey, payload, "NEW", LocalDateTime.now().format(FORMATTER)));
        return messageId;
    }

    public List<Map<String, Object>> recentMessages() {
        return jdbcExecutor.query(PlatformBootstrapService.DS_WAREHOUSE, jdbc ->
                jdbc.queryForList("select message_id, topic, biz_key, status, created_at, payload from event_message order by message_id desc limit 20"));
    }
}
