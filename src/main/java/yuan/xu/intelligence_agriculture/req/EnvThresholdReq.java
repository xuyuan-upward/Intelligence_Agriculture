package yuan.xu.intelligence_agriculture.req;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EnvThresholdReq {
    /**
     * 环境编码(温室/区域)
     */
    private String envCode;

    /**
     * 环境参数类型 (1-6)
     */
    private Integer envParameterType;

    /**
     * 阈值下限
     */
    private BigDecimal minValue;

    /**
     * 阈值上限
     */
    private BigDecimal maxValue;
}
