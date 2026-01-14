package yuan.xu.intelligence_agriculture.req;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
     * 设备编号（所属网关）
     */
    private String deviceCode;

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
