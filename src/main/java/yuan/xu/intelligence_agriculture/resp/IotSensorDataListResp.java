package yuan.xu.intelligence_agriculture.resp;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 传感器采集数据实体类
 * 对应数据库表 IotSensorDataListResp，存储六大环境指标的实时采集数值，以及是否对应环境指标类型是否处于异常
 */
@Data
public class IotSensorDataListResp implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 环境编码(温室/区域)
     */
    private String envCode;

    /**
     * 环境编码(温室/区域)
     */
    List<IotSensorDataResp> iotSensorDataRespList;

}
