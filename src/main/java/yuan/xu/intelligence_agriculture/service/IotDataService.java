package yuan.xu.intelligence_agriculture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import yuan.xu.intelligence_agriculture.model.IotSensorData;

public interface IotDataService extends IService<IotSensorData> {
    void processSensorData(String payload);
}
