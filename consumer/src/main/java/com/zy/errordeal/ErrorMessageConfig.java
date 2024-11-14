package com.zy.errordeal;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: zy
 * @Date: 2024-11-14-13:57
 * @Description: 处理失败消息的【交换机和队列】
 * 位置：在consumer服务中的配置类config/ErrorMessageConfig.java
 * 作用：声明交换机、队列、绑定关系
 * <p>
 * 绑定关系：交换机error.direct–routingkey（error）–》error.queue
 * <p>
 * 失败策略：
 * 1、参数rabbitTemplate（Spring容器自动注入）
 * 2、通过rabbitTemplate将消息发送到（处理失败消息的）交换机（routingKey为error）
 * <p>
 * 这里的RepublishMessageRecoverer的作用：
 * 当消费者的消息失败重试次数用尽后，将失败的消息【丢弃给指定的error交换机的error队列】
 */
@Configuration
public class ErrorMessageConfig {
    @Bean
    public DirectExchange errorMessageExchange() {
        return new DirectExchange("error.direct");
    }

    @Bean
    public Queue errorQueue() {
        return new Queue("error.queue", true);
    }

    @Bean
    public Binding errorBinding(Queue errorQueue, DirectExchange errorMessageExchange) {
        return BindingBuilder.bind(errorQueue).to(errorMessageExchange).with("error");
    }

    /**
     * 参数 Spring自动注入的rabbitTemplate
     *
     * @param rabbitTemplate
     * @return
     *
     * Republishing failed message to
     * exchange 'error.direct' with routing key error
     */
    @Bean
    public MessageRecoverer republishMessageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate, "error.direct", "error");
    }
}

