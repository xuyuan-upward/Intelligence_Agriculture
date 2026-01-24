package yuan.xu.intelligence_agriculture.req;

import lombok.Data;

/**
 * 设备控制请求类
 */
@Data
public class DeviceControlReq {


    /**
     * 环境编码(温室/区域)
     */
    private String envCode;

    /**
     * 设备唯一编码
     */
    private String deviceCode;

    /**
     * 目标状态(0关/1开)
     */
    private Integer status;

    /**
     * 设备的名称
     */
    private String deviceName;
}
