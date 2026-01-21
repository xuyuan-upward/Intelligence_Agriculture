package yuan.xu.intelligence_agriculture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import yuan.xu.intelligence_agriculture.model.IotSensorData;

public interface IotDataService extends IService<IotSensorData> {
    /**
     * 处理从 MQTT 接收到的传感器数据
     *
     * @param payload 原始 MQTT 消息内容（JSON 格式）
     * @param topic   消息来源的 Topic
     */
    void processSensorData(String payload, String topic);
}
