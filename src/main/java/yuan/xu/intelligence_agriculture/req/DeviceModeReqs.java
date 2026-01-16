package yuan.xu.intelligence_agriculture.req;

import lombok.Data;

/**
 * 某个环境下所有设备模式更改请求类
 */
@Data
public class DeviceModeReqs {
    /**
     * 环境编码(温室/区域)
     */
    private String envCode;

    /**
     * 模式(0手动/1自动)
     */
    private Integer mode;

}
