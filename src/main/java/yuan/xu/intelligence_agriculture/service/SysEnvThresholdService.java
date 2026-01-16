package yuan.xu.intelligence_agriculture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;
import yuan.xu.intelligence_agriculture.model.SysEnvThreshold;
import yuan.xu.intelligence_agriculture.req.EnvThresholdListReq;
import yuan.xu.intelligence_agriculture.req.EnvThresholdReq;
import yuan.xu.intelligence_agriculture.resp.EnvThresholdResp;

import java.util.List;

public interface SysEnvThresholdService extends IService<SysEnvThreshold> {
    /**
     * 更新某个环境下的单个环境参数阈值
     * @param req
     */
    void updateSingleEnvThreshold(EnvThresholdReq req);

    /**
     * 更新某个环境下的所有环境参数阈值
     * @param req
     */
    @Transactional(rollbackFor = Exception.class)
    void updateEnvThreshold(EnvThresholdListReq req);

    /**
     * 获取环境阈值
     * @param envCode
     */
    List<EnvThresholdResp> queryEnvThreshold(String envCode);
}
