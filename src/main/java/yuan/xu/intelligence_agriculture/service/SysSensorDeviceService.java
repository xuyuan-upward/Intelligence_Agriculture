package yuan.xu.intelligence_agriculture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import yuan.xu.intelligence_agriculture.model.SysSensorDevice;
import java.util.List;
import java.util.Map;

public interface SysSensorDeviceService extends IService<SysSensorDevice> {

    /**
     * 判断对应采集设备状态是否离线,并获取判断后的所有设备状态
     */
    Map<String, Integer> listAllDevicesStatus(String greenHouseCode);
}
