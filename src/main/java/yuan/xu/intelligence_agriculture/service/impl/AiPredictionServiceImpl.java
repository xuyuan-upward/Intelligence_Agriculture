package yuan.xu.intelligence_agriculture.service.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.service.AiPredictionService;
import yuan.xu.intelligence_agriculture.service.IotDataService;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiPredictionServiceImpl implements AiPredictionService {

    @Value("${ai.service.url:http://localhost:5000/predict}")
    private String aiServiceUrl;

    @Autowired
    private IotDataService iotDataService;

    @Override
    public String getPrediction() {
        // 1. 获取近6小时的数据
        Date sixHoursAgo = new Date(System.currentTimeMillis() - 6 * 60 * 60 * 1000);
        List<IotSensorData> recentData = iotDataService.lambdaQuery()
                .ge(IotSensorData::getCreateTime, sixHoursAgo)
                .orderByAsc(IotSensorData::getCreateTime)
                .list();
        
        Map<String, Object> param = new HashMap<>();
        param.put("data", recentData);

        // 2. 调用 Python 接口
        try {
            return HttpUtil.post(aiServiceUrl, JSONUtil.toJsonStr(param));
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\": \"AI service unavailable\"}";
        }
    }
}
