package yuan.xu.intelligence_agriculture.mqtt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.MessageHandler;
import org.springframework.stereotype.Service;
import yuan.xu.intelligence_agriculture.service.IotDataService;

/**
 * MQTT 消息接收处理器
 * 负责监听入站通道并调用业务服务处理收到的原始数据
 */
@Service
public class MqttMessageHandler {

    @Autowired
    private IotDataService iotDataService;

    /**
     * 监听 mqttInputChannel 通道的消息
     * ServiceActivator 注解指定该方法为消息激活器，处理入站消息
     */
    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler handler() {
        return message -> {
            try {
                // 获取消息负载（Payload）
                String payload = (String) message.getPayload();
                // 调用业务层服务处理解析、存储及推送逻辑
                iotDataService.processSensorData(payload);
            } catch (Exception e) {
                // 异常处理，防止由于单条消息处理失败导致适配器停止运行
                e.printStackTrace();
            }
        };
    }
}
