package yuan.xu.intelligence_agriculture.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class SensorDataDTO {
    /**
     * 环境编码
     */
    private String greenhouseEnvCode;
    /**
     * 设备编号/设备数据
     */
    List<SensorData> sensorDataList = new ArrayList<>();
}
