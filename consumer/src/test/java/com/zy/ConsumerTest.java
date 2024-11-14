package com.zy;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * @Author: zy
 * @Date: 2024-11-14-13:32
 * @Description:
 */
@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
public class ConsumerTest {

    @Autowired
    RabbitTemplate rabbitTemplate;

    /**
     * RabbitMQ是阅后即焚机制，RabbitMQ确认消息被消费者消费后会立刻删除。
     * 此时，如果消费者还没有处理消息，然后消费者挂掉了，就会导致消息丢失。
     */
    @Test
    public void testDurableMessage() {
        // 1.准备消息
        Message message = MessageBuilder.withBody("hello, spring".getBytes(StandardCharsets.UTF_8)).setDeliveryMode(
            MessageDeliveryMode.PERSISTENT).build();
        // 2.发送消息
        rabbitTemplate.convertAndSend("simple.queue", message);
    }

    /**
     * 1）RabbitMQ投递消息给消费者
     * 2）消费者获取消息后，【返回ACK给RabbitMQ】
     * 3）RabbitMQ删除消息
     * 4）消费者宕机，消息尚未处理
     * <p>
     * 三种方式
     * manual：手动ack，需要在业务代码结束后，调用api发送ack。
     * auto：自动ack，由spring监测listener代码是否出现异常，没有异常则返回ack；抛出异常则返回nack
     * none：关闭ack，MQ假定消费者获取消息后会成功处理，因此消息投递后立即被删除(此时，消息投递是不可靠的，可能丢失)
     */

    @Test
    public void testTTLMsg() {
        // 创建消息
        Message message = MessageBuilder.withBody("hello, ttl message".getBytes(StandardCharsets.UTF_8)).setExpiration(
            "5000").build();
        // 消息ID，需要封装到CorrelationData中
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        // 发送消息
        System.out.println("消息ID：" + correlationData.getId());
        rabbitTemplate.convertAndSend("1-ttl.direct", "1-ttl", message, correlationData);
        log.debug("发送消息成功");
    }

    /**
     * 使用DelayExchange-代码方式
     * DelayExchange需要将一个交换机声明为delayed类型。当我们发送消息到delayExchange时，流程如下：
     * <p>
     * 接收消息
     * 判断消息是否具备x-delay属性
     * 如果有x-delay属性，说明是延迟消息，持久化到硬盘，读取x-delay值，作为延迟时间
     * 返回routing not found结果给消息发送者
     * x-delay时间到期后，重新投递消息到指定队列
     *
     * @throws InterruptedException 异常
     */
    @Test
    public void testSendDelayMessage() throws InterruptedException {
        // 1.准备消息
        Message message =
            MessageBuilder.withBody("hello, delayed test ttl messsage".getBytes(StandardCharsets.UTF_8)).setDeliveryMode(
                MessageDeliveryMode.PERSISTENT).setHeader("x-delay", 5000).build();
        // 2.准备CorrelationData
        //消息ID
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        System.out.println("消息ID：" + correlationData.getId());
        // 3.发送消息
        rabbitTemplate.convertAndSend("delay.direct", "delay", message, correlationData);

        log.info("发送消息成功");
    }

}
