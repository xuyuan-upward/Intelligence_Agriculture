package yuan.xu.intelligence_agriculture.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备状态 WebSocket 传输对象
 * 用于推送设备工作状态(ON/OFF)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceStatusResp {
    /**
     * 设备唯一编码
     */
    private String deviceCode;
    
    /**
     * 状态值
     * 工作状态: 0-关闭, 1-开启
     */
    private Integer status;
}
