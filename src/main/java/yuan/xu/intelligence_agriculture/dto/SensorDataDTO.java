package yuan.xu.intelligence_agriculture.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class SensorDataDTO {
    /**
     * 设备编号/网关编号
     */
    private String deviceCode;

    private BigDecimal airTemp;
    private BigDecimal airHumidity;
    private BigDecimal lightIntensity;
    private BigDecimal soilHumidity;
    private BigDecimal soilTemp;
    private BigDecimal co2Concentration;
}
