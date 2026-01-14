package yuan.xu.intelligence_agriculture.req;

import lombok.Data;

/**
 * 设备控制请求类
 */
@Data
public class DeviceControlReq {
    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 目标状态(0关/1开)
     */
    private Integer status;
}
