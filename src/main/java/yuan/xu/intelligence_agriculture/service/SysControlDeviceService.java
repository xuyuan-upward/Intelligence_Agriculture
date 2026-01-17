package yuan.xu.intelligence_agriculture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import yuan.xu.intelligence_agriculture.dto.SensorData;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.model.SysControlDevice;
import yuan.xu.intelligence_agriculture.req.DeviceModeReq;
import yuan.xu.intelligence_agriculture.req.DeviceModeReqs;

import java.util.Map;

public interface SysControlDeviceService extends IService<SysControlDevice> {
    
    /**
     * 手动控制设备开关
     */
    void controlDevice(String deviceCode, Integer status, String envCode);

    /**
     * 更新某个环境的"单个"设备的控制模式
     */
    void updateSingleMode(DeviceModeReq req);

    /**
     * 自动模式下的控制触发
     */
    void checkAndAutoControl(IotSensorData data, Map<Integer, SensorData> integerSensorDataMap);


    /**
     * 更新某个环境"所有"控制设备的控制模式
     */
    void updatesDevicesMode(DeviceModeReqs reqs);

    /**
     * 判断对应控制设备状态是否离线,并获取判断后的所有设备状态
     */
    Map<String, Integer> listAllDevicesStatus(String envCode);



//    /**
//     * 获取所有控制设备（优先从缓存获取）
//     */
//    List<SysControlDevice> listAllDevicesFromCache();
}
