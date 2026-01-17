package yuan.xu.intelligence_agriculture.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统日志 WebSocket 传输对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SystemLogResp {
    /**
     * 日志类型 (success, warning, info, error)
     */
    private String type;

    /**
     * 来源 (例如: 自动控制, 手动控制)
     */
    private String source;

    /**
     * 日志内容
     */
    private String message;
}
