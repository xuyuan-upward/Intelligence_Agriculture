package yuan.xu.intelligence_agriculture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import yuan.xu.intelligence_agriculture.model.SysDevice;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import java.math.BigDecimal;

public interface SysDeviceService extends IService<SysDevice> {
    void controlDevice(Long deviceId, Integer status);
    void updateMode(Long deviceId, Integer mode, BigDecimal min, BigDecimal max);
    void checkAndAutoControl(IotSensorData data);
}
