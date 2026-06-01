package com.example.rabbitmq0601.common;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 连接与核心配置
 * - 连接到 3 节点集群 (addresses)
 * - 开启 publisher confirm / returns
 * - Jackson JSON 序列化
 */
@Configuration
public class RabbitConfig {

    @Value("${spring.rabbitmq.addresses}")
    private String addresses;

    @Value("${spring.rabbitmq.username}")
    private String username;

    @Value("${spring.rabbitmq.password}")
    private String password;

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setAddresses(addresses);
        factory.setUsername(username);
        factory.setPassword(password);
        // 【重点】开启 publisher confirm —— 生产者确认消息是否到达 Broker
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        // 【重点】开启 publisher returns —— 消息无法路由时回调
        factory.setPublisherReturns(true);
        return factory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());

        // 【重点】mandatory=true：消息无法路由到队列时，触发 ReturnCallback
        template.setMandatory(true);
        return template;
    }

    // ========== 通用交换器 ==========

    /** 订单主题交换器 —— 多个 q 包共用 */
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange("ex.order", true, false);
    }

    /** 死信交换器 —— q7 使用 */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange("ex.dead", true, false);
    }
}
