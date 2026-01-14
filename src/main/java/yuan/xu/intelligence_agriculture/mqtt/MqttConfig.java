package yuan.xu.intelligence_agriculture.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

/**
 * MQTT 配置类
 * 用于初始化 MQTT 客户端工厂、入站通道、出站通道及相关消息适配器
 */
@Configuration
public class MqttConfig {

    // MQTT 代理服务器地址
    private static final String MQTT_BROKER_URL = "tcp://broker.emqx.io:1883";
    // 客户端 ID，使用时间戳保证唯一性
    private static final String CLIENT_ID = "springboot_server_" + System.currentTimeMillis();
    // 传感器数据订阅主题
    private static final String TOPIC_SENSOR = "sensor/data";

    /**
     * 配置 MQTT 客户端工厂
     */
    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{MQTT_BROKER_URL});
        options.setCleanSession(true); // 设置是否清空会话
        options.setKeepAliveInterval(60); // 设置心跳包发送间隔
        factory.setConnectionOptions(options);
        return factory;
    }

    /**
     * 入站消息通道：用于接收从 MQTT 代理发送过来的消息
     */
    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    /**
     * MQTT 入站消息适配器：订阅指定主题并将消息转发到入站通道
     */
    @Bean
    public MessageProducer inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(CLIENT_ID + "_inbound", mqttClientFactory(), TOPIC_SENSOR);
        adapter.setCompletionTimeout(5000); // 连接超时时间
        adapter.setConverter(new DefaultPahoMessageConverter()); // 默认的消息转换器
        adapter.setQos(1); // 设置质量服务等级
        adapter.setOutputChannel(mqttInputChannel()); // 设置输出通道
        return adapter;
    }

    /**
     * 出站消息通道：用于向 MQTT 代理发送消息
     */
    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    /**
     * MQTT 出站消息处理器：监听出站通道并将消息推送到 MQTT 代理
     */
    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound() {
        MqttPahoMessageHandler messageHandler =
                new MqttPahoMessageHandler(CLIENT_ID + "_outbound", mqttClientFactory());
        messageHandler.setAsync(true); // 设置异步发送
        messageHandler.setDefaultTopic("device/control"); // 默认下发控制的主题
        return messageHandler;
    }
}
