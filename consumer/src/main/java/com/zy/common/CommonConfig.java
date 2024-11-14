package com.zy.common;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: zy
 * @Date: 2024-11-14-13:23
 * @Description: 声明完队列和交换机，可以在RabbitMQ控制台看到持久化的交换机和队列都会带上D的标示：
 */
@Configuration
public class CommonConfig {
    /**
     * 三个参数：交换机名称、是否持久化、
     * 当没有queue与其绑定时是否自动删除
     * (事实上，默认情况下，由SpringAMQP声明的交换机和队列都是持久化的)
     */
    @Bean
    public DirectExchange simpleDirect() {

        /*
         * 默认为return new DirectExchange("simple.direct",true,false);
         */
        return new DirectExchange("simple.direct");
    }

    /**
     * 使用QueueBuilder构建队列，durable就是持久化的
     */
    @Bean
    public Queue simpleQueue() {
        //new Queue("");默认代码为public Queue(String name) { this(name, true, false, false);}
        return QueueBuilder.durable("simple.queue").build();
    }

    // 【声明普通的 simple.queue队列，并且为其指定死信交换机：dl.direct】  死信配置
    @Bean
    public Queue simpleQueue2() {
        return QueueBuilder.durable("simple.dl.queue") // 指定队列名称，并持久化
            .deadLetterExchange("dl.direct") // 【指定死信交换机】
            .build();
    }

    // 声明死信交换机 dl.direct
    @Bean
    public DirectExchange dlExchange() {
        return new DirectExchange("dl.direct", true, false);
    }

    // 声明存储死信的队列 dl.queue
    @Bean
    public Queue dlQueue() {
        return new Queue("dl.queue", true);
    }

    // 将死信队列 与 死信交换机绑定
    @Bean
    public Binding dlBinding() {
        return BindingBuilder.bind(dlQueue()).to(dlExchange()).with("simple");
    }

    /**
     * 延时队列   需要安装插件，所以目前暂时无法测试
     *
     * @return
     */
    @Bean
    public DirectExchange delayedExchange() {
        return ExchangeBuilder.directExchange("delay.direct").delayed().durable(true).build();
    }

    @Bean
    public Queue delayedQueue() {
        return new Queue("delay.queue");
    }

    @Bean
    public Binding delayedBinding() {
        return BindingBuilder.bind(delayedQueue()).to(delayedExchange()).with("delay");
    }
}

