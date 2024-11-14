package com.zy.common;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: zy
 * @Date: 2024-11-14-14:30
 * @Description: 声明交换机、队列，绑定关系、为队列指定TTL超时时间
 * <p>
 * 新建一个配置类（便于管理），配置类记得添加@Configuration
 * 位置：consumer/config/TTLMessageConfig类
 * 要给队列设置超时时间，需要在声明队列时配置ttl属性
 */
@Configuration
public class TTLMessageConfig {

    //声明队列ttl.queue，设置超时时间
    @Bean
    public Queue ttlQueue() {
        return QueueBuilder.durable("1-ttl.queue") // 指定队列名称，并持久化
            .ttl(10000) // 设置队列的超时时间，10秒
            .deadLetterExchange("1-dl.ttl.direct") // 队列指定死信交换机，即消息超时就投到这个交换机
            .deadLetterRoutingKey("1-dl")//消息到死信交换机的RoutingKey
            .build();
    }

    //正常的声明交换机ttl.direct
    @Bean
    public DirectExchange ttlExchange() {
        return new DirectExchange("1-ttl.direct");
    }

    //正常的绑定交换机和队列，routingkey为ttl
    @Bean
    public Binding ttlBinding() {
        return BindingBuilder.bind(ttlQueue()).to(ttlExchange()).with("1-ttl");
    }
}

