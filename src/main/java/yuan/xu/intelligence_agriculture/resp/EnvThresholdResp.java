package yuan.xu.intelligence_agriculture.resp;

import lombok.Data;

@Data
public class EnvThresholdResp {
    /**
     * 环境编码(温室/区域)
     */
    private String envCode;

    /**
     * 环境参数类型 (1-6)
     */
    private Integer envParameterType;

    /**
     * 最大阈值
     */
    private Integer max;
    /**
     * 最大阈值
     */
    private Integer min;
}
