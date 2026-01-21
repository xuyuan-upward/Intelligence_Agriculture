package yuan.xu.intelligence_agriculture.resp;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 传感器历史数据实体类
 * 对应数据库表 IotSensorHistoryData，存储六大环境指标的实时采集数值
 */
@Data
public class IotSensorHistoryDataResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 所属环境编码
     */
    private String greenhouseEnvCode;

    /**
     * 空气温度
     */
    private BigDecimal airTemp;

    /**
     * 空气湿度
     */
    private BigDecimal airHumidity;

    /**
     * 光照强度
     */
    private BigDecimal lightIntensity;

    /**
     * 土壤湿度
     */
    private BigDecimal soilHumidity;

    /**
     * 土壤温度
     */
    private BigDecimal soilTemp;

    /**
     * CO2 浓度
     */
    private BigDecimal co2Concentration;

    /**
     * 数据采集/入库时间
     */
    private Date createTime;
}