package com.zy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Author: zy
 * @Date: 2024-11-14-13:22
 * @Description:
 */
@SpringBootApplication
public class ConsumerApp {

    /**
     * 问题：在上面的测试中，达到最大重试次数后，消息会被丢弃，这是由Spring内部机制决定的。
     * 但是，有些数据特别重要，我们不希望任何消息被丢弃，此时，我们应该如何实现？
     *
     * 在开启重试模式后，重试次数耗尽，如果消息依然失败，则需要有MessageRecovery接口来处理，它包含三种不同的实现：
     *
     * RejectAndDontRequeueRecoverer：重试耗尽后，直接reject，【丢弃消息】【默认】就是这种方式
     * ImmediateRequeueMessageRecoverer：重试耗尽后，返回nack，消息重新入队（Immediate立刻重入队）（但是频率比没有配置消费失败重载机制低一些）
     * RepublishMessageRecoverer（推荐）：重试耗尽后，将失败消息投递到指定的交换机
     * @param args
     */
    public static void main(String[] args) {
        SpringApplication.run(ConsumerApp.class, args);
    }
}
