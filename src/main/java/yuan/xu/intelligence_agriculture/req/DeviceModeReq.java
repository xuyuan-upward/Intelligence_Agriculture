package yuan.xu.intelligence_agriculture.req;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 设备模式及阈值更新请求类
 */
@Data
public class DeviceModeReq {
    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 模式(0手动/1自动)
     */
    private Integer mode;

    /**
     * 阈值下限(可选)
     */
    private BigDecimal min;

    /**
     * 阈值上限(可选)
     */
    private BigDecimal max;
}
