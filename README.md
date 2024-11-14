publish-confirm-type：开启publisher-confirm，这里支持两种类型：
    simple：【同步】等待confirm结果，直到超时（可能引起代码阻塞）
    correlated：【异步】回调，定义ConfirmCallback，MQ返回结果时会回调这个ConfirmCallback
publish-returns：开启publish-return功能，同样是基于callback机制，不过是定义ReturnCallback
template.mandatory：定义当消息从交换机路由到队列失败时的策略。【true，则调用ReturnCallback；false：则直接丢弃消息】


原文链接：https://blog.csdn.net/m0_53142956/article/details/127792054


消息队列在使用过程中，面临着很多实际问题需要思考：

消息可靠性问题：如何确保发送的消息至少被消费—次
延迟消息问题：如何实现消息的延迟投递
消息堆积问题：如何解决数百万消息堆积，无法及时消费的问题
高可用问题：如何避免单点的MQ故障而导致的不可用问题
一、消息可靠性
背景/需求：消息从发送，到消费者接收，会经历多个过程：
其中的每一步都可能导致消息丢失，常见的丢失原因包括：

发送时丢失：

生产者发送的消息【未送达exchange】——返回nack（消息确认模式）
消息【到达exchange】——返回ack（消息确认模式）
到达queue后，MQ宕机，queue将消息丢失
——返回ACK，及路由失败原因（回退模式）

consumer接收到消息后还未消费就宕机——消息持久化

1、【生产者】消息确认
RabbitMQ提供了publisher confirm机制来避免消息发送到MQ过程中丢失。这种机制必须给每个消息指定一个唯一ID。消息发送到MQ以后，会返回一个结果给发送者，表示消息是否处理成功。

返回结果有两种方式：

publisher-confirm，发送者确认
消息成功投递到交换机，返回ack
消息未投递到交换机，返回nack
publisher-return，发送者回执
消息投递到交换机了，但是没有路由到队列。返回ACK，及路由失败原因。
注意：确认机制发送消息时，需要给每个消息设置一个全局唯一id，以区分不同消息，避免ack冲突

1.1 修改application.yml配置文件，添加下面的内容：
位置：生产者/publisher服务
目的：
1、开启消息确认模式
2、开启消息回退（并设置消息路由到队列失败时，回退消息给回调接口）

spring:
rabbitmq:
publisher-confirm-type: correlated
# 开启publisher-confirm，且选择correlated：【异步】回调，定义ConfirmCallback，MQ返回结果时会回调这个ConfirmCallback
publisher-returns: true # 开启publish-return功能
template:
mandatory: true # 定义当消息从交换机路由到队列失败时的策略。【true，则调用ReturnCallback；false：则直接丢弃消息】
1
2
3
4
5
6
7


说明：

publish-confirm-type：开启publisher-confirm，这里支持两种类型：
simple：【同步】等待confirm结果，直到超时（可能引起代码阻塞）
correlated：【异步】回调，定义ConfirmCallback，MQ返回结果时会回调这个ConfirmCallback
publish-returns：开启publish-return功能，同样是基于callback机制，不过是定义ReturnCallback
template.mandatory：定义当消息从交换机路由到队列失败时的策略。【true，则调用ReturnCallback；false：则直接丢弃消息】
1.2定义Return回退：
说明：因为在yml配置文件中定义消息路由失败时的策略为true，所以当消息从交换机路由到队列失败时，会调用ReturnCallback

每个RabbitTemplate只能配置一个ReturnCallback，因此需要在项目加载时添加配置：
修改publisher服务，添加一个【配置类】：
位置：config/commic配置类

如何保证在项目加载时添加配置?
1、实现ApplicationContextAware（实现了ApplicationContextAware接口的实现类，在Spring容器的Bean工厂创建完毕后会通知该实现类）
2、此时，该实现/配置类有了Spring容器的Bean工厂类；就可以获取并设置ReturnCallback（Spring容器的Bean对象）
3、开始配置ReturnCallback；

ReturnCallback的回调函数：当消息成功发送到交换机，但是没有成功发送到消息队列时，回退到回调函数，应该如何处理？就是回调函数里面的内容

package cn.itcast.mq.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
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
rabbitTemplate.setReturnCallback((message, replyCode, replyText, exchange, routingKey) -> {
// 记录日志
log.error("消息发送到队列失败，响应码：{}, 失败原因：{}, 交换机: {}, 路由key：{}, 消息: {}",
replyCode, replyText, exchange, routingKey, message.toString());
// 如果有需要的话，重发消息
});
}
}
1
2
3
4
5
6
7
8
9
10
11
12
13
14
15
16
17
18
19
20
21
22
23
24
25
26
1、重写方法setApplicationContext(ApplicationContext applicationContext)
参数为接口，且该【接口只有一个方法】可以用lambda表达式代替


代替后如下

2、编写回调函数
（ReturnCallback回调函数：当消息成功发送到交换机，但是没有成功发送到消息队列时，回退到回调函数，应该如何处理？就是回调函数里面的内容）

1.3 定义ConfirmCallback（消息确认）
ConfirmCallback【可以在发送消息时指定】因为每个业务处理confirm成功或失败的逻辑不一定相同

消息发送代码如下：
位置：在publisher服务的cn.itcast.mq.spring.SpringAmqpTest类中，定义一个单元测试方法：

注意：确认机制发送消息时，需要给每个消息设置一个全局唯一id，以区分不同消息，避免ack冲突

CorrelationData的作用：
1、消息ID需要封装到CorrelationData
2、correlationData.getFuture().addCallback(…）是一个回调函数：决定了每个业务处理confirm成功或失败的逻辑

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class SpringAmqpTest {
@Autowired
private RabbitTemplate rabbitTemplate;

    @Test
    public void testSendMessage2SimpleQueue() throws InterruptedException {
        // 1.准备消息
        String message = "hello, spring amqp!";
        // 2.准备CorrelationData（消息ID需要封装到CorrelationData）
        // 2.1.消息ID,确认机制发送消息时，需要给每个消息设置一个全局唯一id，以区分不同消息，避免ack冲突
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        // 2.2.准备ConfirmCallback（Future是对将来的一种处理的封装）（Future.addCallback）
        correlationData.getFuture().addCallback(
                result -> {
                    // 判断结果
                    if (result.isAck()) {
                        // ACK
                        log.debug("消息成功投递到交换机！消息ID: {}", correlationData.getId());
                    } else {
                        // NACK
                        log.error("消息投递到交换机失败！消息ID：{}", correlationData.getId());
                        // 重发消息
                    }
                },
                ex -> {
                    // 记录日志
                    log.error("消息发送失败！", ex);
                    // 重发消息
                });
        // 3.发送消息（这里如果没有绑定交换机和队列关系等，可以去管控台绑定，也可以在消费者的配置类中声明）
        rabbitTemplate.convertAndSend("amq.topic", "simple.test", message, correlationData);
    }
}  
1
2
3
4
5
6
7
8
9
10
11
12
13
14
15
16
17
18
19
20
21
22
23
24
25
26
27
28
29
30
31
32
33
34
35
36
附1：有/无消息确认机制的publish消息发送的对比

没有confirm确认机制：

消息确认机制：

RabbitMQ提供了publisher confirm机制来避免消息发送到MQ过程中丢失。这种机制必须给每个消息指定一个唯一ID。消息发送到MQ以后，会返回一个结果给发送者，表示消息是否处理成功。


附2：lambda表达式如何变换

变化后：


测试
（这里如果没有绑定交换机和队列关系等，可以去管控台绑定，也可以在消费者的配置类中声明）

测试1、confirm：消息成功到达交换机——返回ack


测试2：confirm：消息未成功到达交换机——返回nack


测试3:消息发送到了交换机但没有发送到队列——返回ack，但是return回退
故意将队列名字写错（交换机不存在绑定该队列）
返回ACK，及路由失败原因.


2、消息持久化（了解）
背景/需求：生产者确认可以确保消息投递到RabbitMQ的队列中，但是消息发送到RabbitMQ以后，如果消息队列突然宕机，也可能导致消息丢失。
（因为消息队列默认是内存存储）
（发送到消息队列成功+消息队列突然宕机=消息丢失）
要想确保消息在RabbitMQ中安全保存，必须开启消息持久化机制（写入到磁盘中）

注：SpringAMQP默认是进行持久化（包括声明队列、交换机、发送消息）（备注：通过管控台创建的默认是非持久化的）

那么，下面学的消息持久化有什么用呢？持久化毕竟是写磁盘，会有一定的性能损耗，不是所有的数据都需要持久化，学了下面的持久化后可以手动将不需要持久化的数据取消持久化

RabbitMQ Management 控制台设置：
说明：
Durable：持久的
Transient：转瞬即逝的

交换机持久化

消息队列持久化


代码（配置类声明）
说明：由SpringAMQP声明的交换机和队列都是持久化的（所以持久化队列和交换机的代码和我们之前配置类声明队列、交换机一样）


@Configuration
public class CommonConfig {
// 三个参数：交换机名称、是否持久化、当没有queue与其绑定时是否自动删除(事实上，默认情况下，由SpringAMQP声明的交换机和队列都是持久化的)
@Bean
public DirectExchange simpleDirect(){
//默认为return new DirectExchange("simple.direct",true,false);
return new DirectExchange("simple.direct");
}
// 使用QueueBuilder构建队列，durable就是持久化的
@Bean
public Queue simpleQueue(){
//new Queue("");默认代码为public Queue(String name) { this(name, true, false, false);}
return QueueBuilder.durable("simple.queue").build();
}
}
1
2
3
4
5
6
7
8
9
10
11
12
13
14
15
声明完队列和交换机，可以在RabbitMQ控制台看到持久化的交换机和队列都会带上D的标示：

此时，消息和队列都持久化了，但是，如果消息还是没有持久化（重启rabbitmq，交换机和队列都在，但是消息会消失）

消息持久化
默认情况下，SpringAMQP发出的任何消息都是持久化的，不用特意指定。
下面是手动设置消息的属性（MessageProperties），指定delivery-mode：
NON_PERSISTENT,非持久化
PERSISTENT;持久化

@Test
public void testDurableMessage() {
// 1.准备消息
Message message = MessageBuilder.withBody("hello, spring".getBytes(StandardCharsets.UTF_8))
.setDeliveryMode(MessageDeliveryMode.PERSISTENT)
.build();
// 2.发送消息
rabbitTemplate.convertAndSend("simple.queue", message);
}
1
2
3
4
5
6
7
8
9
3、【消费者】消息确认
RabbitMQ是阅后即焚机制，RabbitMQ确认消息被消费者消费后会立刻删除。此时，如果消费者还没有处理消息，然后消费者挂掉了，就会导致消息丢失。

场景如下：

1）RabbitMQ投递消息给消费者
2）消费者获取消息后，【返回ACK给RabbitMQ】
3）RabbitMQ删除消息
4）消费者宕机，消息尚未处理
（成功发送到消费者+消费者还没处理消息就宕机了）消息就丢失了。因此消费者返回ACK的时机非常重要。

而SpringAMQP则允许配置三种确认模式：

manual：手动ack，需要在业务代码结束后，调用api发送ack。
auto：自动ack，由spring监测listener代码是否出现异常，没有异常则返回ack；抛出异常则返回nack
none：关闭ack，MQ假定消费者获取消息后会成功处理，因此消息投递后立即被删除(此时，消息投递是不可靠的，可能丢失)
一般，我们都是使用默认的auto即可。

【yml配置文件中配置消息确认模式】：
spring:
rabbitmq:
listener:
simple:
acknowledge-mode: auto #由spring监测listener代码是否出现异常，没有异常则返回ack；抛出异常则返回nack
1
2
3
4
5
消费者模拟处理异常
@Slf4j
@Component
public class SpringRabbitListener {

    @RabbitListener(queues = "simple.queue")
    public void listenSimpleQueue(String msg) {
        //none模式下，消费者在这里接收到消息后，消息就从队列中被删除了
        log.debug("消费者接收到simple.queue的消息：【" + msg + "】");
        System.out.println(1 / 0);//抛出异常、后面就不会执行业务代码
        log.info("消费者处理消息成功！");//模拟业务代码
    }
}
1
2
3
4
5
6
7
8
9
10
11
12
发送消息测试
管控台发送消息


auto模式下：由spring监测listener代码是否出现异常，没有异常则返回ack；抛出异常则返回nack

在异常位置打断点，再次发送消息，程序卡在断点时，可以发现此时消息状态为unack（未确定状态）：

抛出异常后，因为Spring会自动返回nack，所以消息恢复至Ready状态，并且没有被RabbitMQ删除：
【问题】：当消费者出现异常后，消息会不断requeue（重入队）到队列，再重新发送给消费者，然后再次异常，再次requeue，无限循环，导致mq的消息处理飙升，带来不必要的压力：（这里测试一条数据就已经达到 3000条/s 了）


4、消费失败重试机制
我们可以利用Spring的retry机制，在消费者出现异常时利用本地重试，而不是无限制的requeue到mq队列（不返回ack，也不返回nack），而是可以自己设置重试的次数（如果在重试n次后仍然失败，那么后面在继续重入队大概率也会失败，那么就直接扔掉，不再重入队,此时，Spring会返回ack）
总结：

开启本地重试时，消息处理过程中抛出异常，不会requeue到队列，而是在消费者本地重试
【重试达到最大次数后，Spring会返回ack，消息会被丢弃】
1、本地重试
修改consumer服务的application.yml文件，添加内容：

spring:
rabbitmq:
listener:
simple:
retry: # Spring消费者失败重试
enabled: true # 【开关】开启消费者失败重试
initial-interval: 1000 # 初识的失败等待时长为1秒（第一次失败后1s重试）
multiplier: 1 # 失败的等待时长倍数，下次等待时长 = multiplier * last-interval（举例：倍数*第一次等待时长1s，这样子永远都是1s）
#但是如果设置为2，下次等待时长为上次的2倍，因此等待时长依次为1、2、4、8、16....
max-attempts: 3 # 最大重试次数
stateless: true # （默认为true）true无状态；false有状态【如果业务中包含事务，这里改为false】
#（备注：如果设置为false，那么Spring在重试的时候保留事务——消耗性能，所以没有事务时设置为true提升性能）
max-interval: 10000 # 最大等待时长，大于此时长的一律按最大时长来计算
1
2
3
4
5
6
7
8
9
10
11
12
13
重启consumer服务，重复之前的测试。可以发现：

在重试4次后，SpringAMQP会抛出异常AmqpRejectAndDontRequeueException，说明本地重试触发了
查看RabbitMQ控制台，发现消息被删除了，说明最后SpringAMQP返回的是ack，mq删除消息了

2、失败策略
问题：在上面的测试中，达到最大重试次数后，消息会被丢弃，这是由Spring内部机制决定的。但是，有些数据特别重要，我们不希望任何消息被丢弃，此时，我们应该如何实现？

在开启重试模式后，重试次数耗尽，如果消息依然失败，则需要有MessageRecovery接口来处理，它包含三种不同的实现：

RejectAndDontRequeueRecoverer：重试耗尽后，直接reject，【丢弃消息】【默认】就是这种方式
ImmediateRequeueMessageRecoverer：重试耗尽后，返回nack，消息重新入队（Immediate立刻重入队）（但是频率比没有配置消费失败重载机制低一些）
RepublishMessageRecoverer（推荐）：重试耗尽后，将失败消息投递到指定的交换机
RepublishMessageRecoverer：失败后将消息投递到一个指定的，专门存放异常消息的队列，后续由人工集中处理，这样所有的消息都不会丢失。


【RepublishMessageRecoverer处理模式的代码实现】 ：


1）【定义】处理失败消息的【交换机和队列】
位置：在consumer服务中的配置类config/ErrorMessageConfig.java
作用：声明交换机、队列、绑定关系

绑定关系：交换机error.direct–routingkey（error）–》error.queue

@Configuration
public class ErrorMessageConfig {
@Bean
public DirectExchange errorMessageExchange(){
return new DirectExchange("error.direct");
}
@Bean
public Queue errorQueue(){
return new Queue("error.queue", true);
}
@Bean
public Binding errorBinding(Queue errorQueue, DirectExchange errorMessageExchange){
return BindingBuilder.bind(errorQueue).to(errorMessageExchange).with("error");
}

    @Bean
    public MessageRecoverer republishMessageRecoverer(RabbitTemplate rabbitTemplate){
        return new RepublishMessageRecoverer(rabbitTemplate, "error.direct", "error");
    }
}
1
2
3
4
5
6
7
8
9
10
11
12
13
14
15
16
17
18
19
20
2）定义一个RepublishMessageRecoverer，指定/关联队列和交换机
失败策略：
1、参数rabbitTemplate（Spring容器自动注入）
2、通过rabbitTemplate将消息发送到（处理失败消息的）交换机（routingKey为error）

这里的RepublishMessageRecoverer的作用：当消费者的消息失败重试次数用尽后，将失败的消息【丢弃给指定的error交换机的error队列】

//参数 Spring自动注入的rabbitTemplate
@Bean
public MessageRecoverer republishMessageRecoverer(RabbitTemplate rabbitTemplate){
return new RepublishMessageRecoverer(rabbitTemplate, "error.direct", "error");
}
1
2
3
4
5
测试结果：

我们可以在error队列中查看具体的错误信息，然后进行修改

5、总结：
如何确保RabbitMQ消息的可靠性？

开启生产者确认机制，确保生产者的消息能到达队列
开启持久化功能，确保消息未消费前在队列中不会丢失
开启消费者确认机制为auto，由spring确认消息处理成功后完成ack
开启消费者失败重试机制，并设置MessageRecoverer，多次重试失败后将消息投递到异常交换机，交由人工处理
二、死信交换机
1、初识死信交换机
什么是死信？
当一个队列中的消息满足下列情况之一时，可以成为死信（dead letter）：

消费者使用basic.reject或 basic.nack声明消费失败，并且消息的requeue参数设置为false
消息是一个过期消息，超时无人消费
要投递的队列消息满了，无法投递
如果这个包含死信的队列配置了dead-letter-exchange属性，指定了一个交换机，那么队列中的死信就会投递到这个交换机中，而这个交换机称为死信交换机（Dead Letter Exchange，简称DLX）

死信交换机过程大致如下：
1、(重试次数耗尽)一个消息被消费者拒绝了，变成了死信
2、因为simple.queue绑定了死信交换机 dl.direct，因此死信会投递给这个交换机
3、如果这个死信交换机也绑定了一个队列，则消息最终会进入这个存放死信的队列


附：死信交换机对比：消费失败策略
发送消息的对象不同？
republish是由consumer发送，死信是由队列去发送

从上图可以看出，发送消息的对象不同，因此，死信交换机的其中一个功能和消费失败策略功能类似：作为一个兜底方案，当消费者宕机，导致队列满了放不下 队列还可以将溢出的消息转发到死信队列。

【代码实现：利用死信交换机接收死信】
那么如何实现呢？
队列将死信投递给死信交换机时，必须知道两个信息：

死信交换机名称
死信交换机与死信队列绑定的RoutingKey
这样才能确保投递的消息能到达死信交换机，并且正确的路由到死信队列。

代码实现：
声明交换机、队列、绑定关系
+指定死信交换机

// 【声明普通的 simple.queue队列，并且为其指定死信交换机：dl.direct】
@Bean
public Queue simpleQueue2(){
return QueueBuilder.durable("simple.queue") // 指定队列名称，并持久化
.deadLetterExchange("dl.direct") // 【指定死信交换机】
.build();
}
// 声明死信交换机 dl.direct
@Bean
public DirectExchange dlExchange(){
return new DirectExchange("dl.direct", true, false);
}
// 声明存储死信的队列 dl.queue
@Bean
public Queue dlQueue(){
return new Queue("dl.queue", true);
}
// 将死信队列 与 死信交换机绑定
@Bean
public Binding dlBinding(){
return BindingBuilder.bind(dlQueue()).to(dlExchange()).with("simple");
}
1
2
3
4
5
6
7
8
9
10
11
12
13
14
15
16
17
18
19
20
21
22
2、TTL
什么是TTL？
TTL，也就是Time-To-Live。如果一个队列中的消息TTL结束仍未消费，则会变为死信，ttl超时分为两种情况:

消息所在的队列设置了超时时间
消息本身设置了超时时间
从上面的死信交换机中指定，消息是一个过期消息，超时无人消费，这条消息就会被投递到死信交换机中。

其流程大致如下：


TTL的延申功能：延迟消息
给一个消息/队列设置超时时间，将消息发送到ttl.queue（该队列没有消费者，消息一定会超时）消息超时后变成了死信。交给死信交换机-队列-消费者 ，这样就完成了延迟消息的功能。


代码实现：延迟消息功能
1、监听器：【定义一个新的消费者（方法）】【并且声明死信交换机、死信队列、绑定关系】
位置：在consumer服务的SpringRabbitListener中


@RabbitListener(bindings = @QueueBinding(
value = @Queue(name = "dl.ttl.queue", durable = "true"),
exchange = @Exchange(name = "dl.ttl.direct"),
key = "dl"
))
public void listenDlQueue(String msg){
log.info("接收到 dl.ttl.queue的延迟消息：{}", msg);
}
1
2
3
4
5
6
7
8
2、声明交换机、队列，绑定关系、为队列指定TTL超时时间
新建一个配置类（便于管理），配置类记得添加@Configuration
位置：consumer/config/TTLMessageConfig类
要给队列设置超时时间，需要在声明队列时配置ttl属性



@Configuration
public class TTLMessageConfig {
//声明队列ttl.queue，设置超时时间
@Bean
public Queue ttlQueue(){
return QueueBuilder.durable("ttl.queue") // 指定队列名称，并持久化
.ttl(10000) // 设置队列的超时时间，10秒
.deadLetterExchange("dl.direct") // 队列指定死信交换机，即消息超时就投到这个交换机
.deadLetterRoutingKey("dl")//消息到死信交换机的RoutingKey
.build();
}
//正常的声明交换机ttl.direct
@Bean
public DirectExchange ttlExchange(){
return new DirectExchange("ttl.direct");
}
//正常的绑定交换机和队列，routingkey为ttl
@Bean
public Binding ttlBinding(){
return BindingBuilder.bind(ttlQueue()).to(ttlExchange()).with("ttl");
}
}
1
2
3
4
5
6
7
8
9
10
11
12
13
14
15
16
17
18
19
20
21
22
3、发送消息时，设定TTL
在发送消息时，也可以指定TTL：
位置：publisher

@Test
public void testTTLMsg() {
// 创建消息
Message message = MessageBuilder
.withBody("hello, ttl message".getBytes(StandardCharsets.UTF_8))
.setExpiration("5000")
.build();
// 消息ID，需要封装到CorrelationData中
CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
// 发送消息
rabbitTemplate.convertAndSend("ttl.direct", "ttl", message, correlationData);
log.debug("发送消息成功");
}
1
2
3
4
5
6
7
8
9
10
11
12
13
测试结果：

当队列、消息都设置了TTL时，任意一个到期就会成为死信

三、延时队列
因为延迟队列的需求非常多，所以RabbitMQ的官方也推出了一个插件，原生支持延迟队列效果。

这个延时插件需要自己安装，下面文章有基于Linux系统的docekr方式安装
备注：
RabbitMQ（二）：RabbitMQ的安装（Linux、基于docker安装）及其插件安装

DelayExchange原理：

DelayExchange插件的原理是对官方原生的Exchange做了功能的升级:

将DelayExchange接受到的消息暂存在内存中(官方的Exchange是无法存储消息的)

在DelayExchange中计时，超时后才投递消息到队列中

使用DelayExchange-控制台方式
1、控制台声明延迟交换机
2、发送消息

使用DelayExchange-代码方式
DelayExchange需要将一个交换机声明为delayed类型。当我们发送消息到delayExchange时，流程如下：

接收消息
判断消息是否具备x-delay属性
如果有x-delay属性，说明是延迟消息，持久化到硬盘，读取x-delay值，作为延迟时间
返回routing not found结果给消息发送者
x-delay时间到期后，重新投递消息到指定队列
1）声明DelayExchange交换机
声明交换机为delayed类型
法一：基于@RabbitListener（推荐）


@RabbitListener(bindings = @QueueBinding(
value = @Queue(name = "delay.queue", durable = "true"),
exchange = @Exchange(name = "delay.direct", delayed = "true"),
key = "delay"
))
public void listenDelayExchange(String msg) {
log.info("消费者接收到了delay.queue的延迟消息");
}
1
2
3
4
5
6
7
8
法二：基于@Bean的方式：



2）publisher发送消息
向这个delay为true的交换机中发送消息时，一定要给消息添加一个header：x-delay属性，指定延迟的时间，单位为毫秒:


@Test
public void testSendDelayMessage() throws InterruptedException {
// 1.准备消息
Message message = MessageBuilder
.withBody("hello, ttl messsage".getBytes(StandardCharsets.UTF_8))
.setDeliveryMode(MessageDeliveryMode.PERSISTENT)
.setHeader("x-delay", 5000)
.build();
// 2.准备CorrelationData
//消息ID
CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
// 3.发送消息
rabbitTemplate.convertAndSend("delay.direct", "delay", message, correlationData);

        log.info("发送消息成功");
    }

1
2
3
4
5
6
7
8
9
10
11
12
13
14
15
16
测试：

运行会出现如下错误
错误原因：因为没有给延迟交换机指定routingKey，所以路由失败（也没有消费者）

解决方案：

因为是消息成功发送到交换机，交换机发送到队列失败——此时会进行return消息回退；那么，我们可以回退模式中添加判断
位置：publisher服务的config/CommonConfig配置类下（ReturnCallback）

添加如下判断

// 判断是否是延迟消息
Integer receivedDelay = message.getMessageProperties().getReceivedDelay();
if (receivedDelay != null && receivedDelay > 0) {
//判断延迟值非空且大于0==》是一个延迟消息，忽略这个错误提示
return;
}
1
2
3
4
5
6


延迟队列插件的使用步骤包括哪些？

•声明一个交换机，添加delayed属性为true

•发送消息时，添加x-delay头，值为超时时间

四、惰性队列
消息堆积问题
【当生产者发送消息的速度】超过了【消费者处理消息的速度】，就会导致队列中的消息堆积，直到队列存储消息达到上限。之后发送的消息就会成为死信，可能会被丢弃，这就是消息堆积问题。

解决消息堆积有三种思路：

增加更多消费者，提高消费速度。也就是我们之前说的work queue模式
在消费者内开启线程池加快消息处理速度（限制：当消息很多时，需要开启很多线程，线程越多，CPU需要进行上下文切换——消耗性能；适用于消息处理时间较长的情况，开多个线程并行处理多个业务）
扩大队列容积，提高堆积上限
其中，要提升队列容积，把消息保存在内存中显然是不行的。从RabbitMQ的3.6.0版本开始，就增加了Lazy Queues的概念，也就是惰性队列。

惰性队列的特征如下：
接收到消息后直接【存入磁盘】而非内存
mq消息一般都是储存在内存——响应速度快（优点），但是，mq在内存储存设置了一个上限，mq设置内存预警值，当消息占了内存的40%时，mq会处于暂停的状态，阻止生产者投递消息，将这部分消息刷出到磁盘，清理出一部分内存空间出来，导致mq会间歇性的出现暂停，导致mq的并发能力出现忽高忽低的性能不稳定的情况
将消息存入磁盘就不会出现这个问题，但是磁盘的速度肯定没有内存的快——性能损耗

消费者要消费消息时才会从磁盘中读取并加载到内存——同上，性能损耗
支持数百万条的消息存储
惰性队列的如何创建
方式1、基于@Bean声明lazy-queue

方式2、基于@RabbitListener声明LazyQueue

方式3、通过命令行可以将一个运行中的队列修改为惰性队列：
使用Xshell，进入mq容器中，执行该指令
1、进入容器

docker exec -it mq1
1
2、执行命令

rabbitmqctl set_policy Lazy "^lazy-queue$" '{"queue-mode":"lazy"}' --apply-to queues  
1
命令解读：

rabbitmqctl ：RabbitMQ的命令行工具
set_policy ：添加一个策略
Lazy ：策略名称，可以自定义
"^lazy-queue$" ：用正则表达式，匹配队列的名字,（凡是符合该正则表达式规则的队列，全部按照该策略设置）
'{"queue-mode":"lazy"}' ：设置队列模式为lazy模式
--apply-to queues ：策略的作用对象，是所有的队列
执行完该指令后，可以在Rabbitmq的管控台出处查看策略



————————————————

                            版权声明：本文为博主原创文章，遵循 CC 4.0 BY-SA 版权协议，转载请附上原文出处链接和本声明。

原文链接：https://blog.csdn.net/m0_53142956/article/details/127792054