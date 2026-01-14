package yuan.xu.intelligence_agriculture.dto;

import lombok.Data;

@Data
public class SensorData {
    /**
     * 设备编号
     */
    private String deviceCode;

    /**
     * 采集环境参数类型 (1:空气温度 , 2:空气湿度 , 3:土壤温度, 4:土壤湿度 , 5:CO2浓度 , 6:光照强度)
     */

    private Integer envParameterType;

    /**
     * 设备数据
     */
    private Integer data;

}

