package yuan.xu.intelligence_agriculture.mqtt;

import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * MQTT 消息发送网关接口
 * 定义了向 MQTT 代理发送消息的方法，Spring Integration 会自动生成其实现
 */
@Component
@MessagingGateway(defaultRequestChannel = "mqttOutboundChannel")
public interface MqttGateway {
    
    /**
     * 发送消息到 MQTT 默认主题
     * @param data 消息内容（通常为 JSON 字符串）
     */
    void sendToMqtt(String data);

    /**
     * 发送消息到 MQTT 指定主题
     * @param data 消息内容
     * @param topic 目标主题
     */
    void sendToMqtt(String data, @Header(MqttHeaders.TOPIC) String topic);
}
