package yuan.xu.intelligence_agriculture.resp;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 传感器历史数据实体类
 * 对应数据库表 iot_sensor_data，存储六大环境指标的实时采集数值
 */
@Data
public class IotSensorDataResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 环境参数类型 (1-6):其中
     * 1, '空气温度',
     * 2, '空气湿度',
     * 3, '土壤温度',
     * 4, '土壤湿度',
     * 5, 'CO2浓度'
     * 6, '光照强度',
     */
    private Integer envParameterType;

    /**
     * 采集的数值
     */
    private BigDecimal dataValue = new BigDecimal(0);

    /**
     * 数值异常 0：正常 1：异常 默认：0
     */
    private Integer ex = 0;
}
