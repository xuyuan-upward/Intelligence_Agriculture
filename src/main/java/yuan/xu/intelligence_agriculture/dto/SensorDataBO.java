package yuan.xu.intelligence_agriculture.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备通过MQTT上传的数据类型
 */
@Data
public class SensorDataBO {
    /**
     * 环境编码
     */
    private String greenhouseEnvCode;
    /**
     * 设备编号/设备数据
     */
    List<SensorData> sensorDataList = new ArrayList<>();
}
