package yuan.xu.intelligence_agriculture.resp;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PredictionPoint {
    private String time;
    private Long timestamp;
    private BigDecimal airTemp;
    private BigDecimal airHumidity;
    private BigDecimal soilTemp;
    private BigDecimal soilHumidity;
    private BigDecimal co2Concentration;
    private BigDecimal lightIntensity;
}
