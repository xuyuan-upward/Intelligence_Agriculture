package yuan.xu.intelligence_agriculture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import yuan.xu.intelligence_agriculture.model.SysSensorDevice;
import java.util.List;

public interface SysSensorDeviceService extends IService<SysSensorDevice> {
    
    /**
     * 获取所有采集设备（优先从缓存获取）
     */
    List<SysSensorDevice> listAllDevicesFromCache();
}
