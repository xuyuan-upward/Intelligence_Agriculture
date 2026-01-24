package yuan.xu.intelligence_agriculture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import yuan.xu.intelligence_agriculture.dto.SensorData;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.model.SysControlDevice;
import yuan.xu.intelligence_agriculture.req.DeviceModeReq;
import yuan.xu.intelligence_agriculture.req.DeviceModeReqs;
import yuan.xu.intelligence_agriculture.resp.IotSensorDataListResp;

import java.util.List;
import java.util.Map;

public interface SysControlDeviceService extends IService<SysControlDevice> {
    
    /**
     * 手动控制设备开关
     */
    void controlDevice(String deviceCode, Integer status, String envCode, String deviceName);

    /**
     * 更新某个环境的"单个"设备的控制模式
     */
    void updateSingleMode(DeviceModeReq req);

    /**
     * 检查是否触发对应的自动控制，以及对应的控制设备是否需要"开启"
     */
    void checkAndAutoControl(IotSensorData data, Map<Integer, SensorData> integerSensorDataMap);



    /**
     * 更新某个环境"所有"控制设备的控制模式
     */
    void updatesDevicesMode(DeviceModeReqs reqs);

    /**
     * 获取某个环境下的所有控制设备（带状态）
     */
    List<SysControlDevice> listControlDevices(String envCode);
}
