package yuan.xu.intelligence_agriculture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import yuan.xu.intelligence_agriculture.dto.SensorData;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.model.SysControlDevice;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface SysControlDeviceService extends IService<SysControlDevice> {
    
    /**
     * 手动控制设备开关
     */
    void controlDevice(Long deviceId, Integer status);
    
    /**
     * 更新设备的控制模式及自动控制阈值
     */
    void updateMode(Long deviceId, Integer mode, BigDecimal min, BigDecimal max);
    
    /**
     * 自动模式下的控制触发
     */
    void checkAndAutoControl(IotSensorData data, Map<Integer, SensorData> integerSensorDataMap);

    /**
     * 获取所有控制设备（优先从缓存获取）
     */
    List<SysControlDevice> listAllDevicesFromCache();
}
