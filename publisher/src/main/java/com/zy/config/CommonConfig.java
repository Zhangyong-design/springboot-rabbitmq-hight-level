package com.zy.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: zy
 * @Date: 2024-11-14-12:45
 * @Description: 定义Return回退：
 * 说明：因为在yml配置文件中定义消息路由失败时的策略为true，所以当消息从交换机路由到队列失败时，会调用ReturnCallback
 * <p>
 * 每个RabbitTemplate只能配置一个ReturnCallback，因此需要在项目加载时添加配置：
 * 修改publisher服务，添加一个【配置类】：
 * 位置：config/commic配置类
 * <p>
 * 如何保证在项目加载时添加配置?
 * 1、实现ApplicationContextAware（实现了ApplicationContextAware接口的实现类，在Spring容器的Bean工厂创建完毕后会通知该实现类）
 * 2、此时，该实现/配置类有了Spring容器的Bean工厂类；就可以获取并设置ReturnCallback（Spring容器的Bean对象）
 * 3、开始配置ReturnCallback；
 * <p>
 * ReturnCallback的回调函数：当消息成功发送到交换机，但是没有成功发送到消息队列时，回退到回调函数，应该如何处理？就是回调函数里面的内容
 */
@Slf4j
@Configuration
public class CommonConfig implements ApplicationContextAware {
    //实现了ApplicationContextAware接口的实现类，在Spring容器的Bean工厂创建完毕后会通知该实现类
    //有了Bean工厂类，然后就可以获取并设置ReturnCallback（Spring容器的Bean对象）
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // (从Spring容器中)获取RabbitTemplate对象
        RabbitTemplate rabbitTemplate = applicationContext.getBean(RabbitTemplate.class);
        // 配置ReturnCallback
        /**
         * ReturnCallback回调函数：当消息成功发送到交换机，
         * 但是没有成功发送到消息队列时，回退到回调函数，应该如何处理？就是回调函数里面的内容）
         */
        rabbitTemplate.setReturnCallback((message, replyCode, replyText, exchange, routingKey) -> {
            // 判断是否是延迟消息
            Integer receivedDelay = message.getMessageProperties().getReceivedDelay();
            if (receivedDelay != null && receivedDelay > 0) {
                //判断延迟值非空且大于0==》是一个延迟消息，忽略这个错误提示
                return;
            }

            // 记录日志
            log.error("消息发送到队列失败，响应码：{}, 失败原因：{}, 交换机: {}, 路由key：{}, 消息: {}", replyCode, replyText, exchange,
                routingKey, message.toString());
            // 如果有需要的话，重发消息

        });

        //        rabbitTemplate.setReturnCallback(new RabbitTemplate.ReturnsCallback() {
        //            @Override
        //            public void returnedMessage(ReturnedMessage returnedMessage) {
        //
        //            }
        //        });
    }
}

