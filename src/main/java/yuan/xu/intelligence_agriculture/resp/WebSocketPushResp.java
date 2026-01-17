package yuan.xu.intelligence_agriculture.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 推送消息统一响应格式
 * @param <T> 数据载荷类型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WebSocketPushResp<T> {
    /**
     * 推送类型 (e.g., SENSOR_DATA, CONTROL_DEVICE_STATUS, SYSTEM_LOG)
     */
    private String type;

    /**
     * 环境编码
     */
    private String env;

    /**
     * 推送数据
     */
    private T data;
}
