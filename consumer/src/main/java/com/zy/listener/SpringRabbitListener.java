package com.zy.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * @Author: zy
 * @Date: 2024-11-14-13:39
 * @Description:
 */
@Slf4j
@Component
public class SpringRabbitListener {

    /**
     * auto模式下：由spring监测listener代码是否出现异常，
     * 没有异常则返回ack；抛出异常则返回nack
     * <p>
     * 【问题】：当消费者出现异常后，消息会不断requeue（重入队）到队列，
     * 再重新发送给消费者，然后再次异常，再次requeue，
     * 无限循环，导致mq的消息处理飙升，
     * 带来不必要的压力：（这里测试一条数据就已经达到 3000条/s 了）
     *
     * @param msg
     */
    @RabbitListener(queues = "simple.queue")
    public void listenSimpleQueue(String msg) {
        //none模式下，消费者在这里接收到消息后，消息就从队列中被删除了
        log.debug("消费者接收到simple.queue的消息：【" + msg + "】");
        System.out.println(1 / 0);//抛出异常、后面就不会执行业务代码

        log.info("消费者处理消息成功！");//模拟业务代码
    }

    /**
     * 死信监听
     *
     * @param msg
     */
    @RabbitListener(bindings = @QueueBinding(value = @Queue(name = "1-dl.ttl.queue", durable = "true"), exchange = @Exchange(name = "1-dl.ttl.direct"), key = "1-dl"))
    public void listenDlQueue(String msg) {
        log.info("接收到 dl.ttl.queue的延迟消息：{}", msg);
    }

    /**
     * 延时对列监听
     * @param msg
     */
    @RabbitListener(bindings = @QueueBinding(value = @Queue(name = "delay.queue", durable = "true"), exchange = @Exchange(name = "delay.direct", delayed = "true"), key = "delay"))
    public void listenDelayExchange(String msg) {
        log.info("消费者接收到了delay.queue的延迟消息：" + msg);
    }

}

