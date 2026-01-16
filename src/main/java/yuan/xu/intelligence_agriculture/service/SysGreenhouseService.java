package yuan.xu.intelligence_agriculture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import yuan.xu.intelligence_agriculture.model.SysGreenhouse;
import yuan.xu.intelligence_agriculture.req.EnvThresholdListReq;
import yuan.xu.intelligence_agriculture.req.EnvThresholdReq;

public interface SysGreenhouseService extends IService<SysGreenhouse> {

    public void refreshEnvHouseCache();

}
