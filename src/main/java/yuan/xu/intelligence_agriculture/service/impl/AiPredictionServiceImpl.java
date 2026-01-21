package yuan.xu.intelligence_agriculture.service.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.model.SysEnvThreshold;
import yuan.xu.intelligence_agriculture.resp.AiAnalysisResp;
import yuan.xu.intelligence_agriculture.resp.AiSuggestion;
import yuan.xu.intelligence_agriculture.resp.PredictionPoint;
import yuan.xu.intelligence_agriculture.service.AiPredictionService;
import yuan.xu.intelligence_agriculture.service.IotDataService;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static yuan.xu.intelligence_agriculture.key.RedisKey.ENV_THRESHOLD_KEY;

@Service
public class AiPredictionServiceImpl implements AiPredictionService {

    @Value("${ai.service.url:http://localhost:5000/predict}")
    private String aiServiceUrl;

    @Autowired
    private IotDataService iotDataService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public AiAnalysisResp getPrediction(String envCode) {
        AiAnalysisResp resp = new AiAnalysisResp();
        resp.setChartData(new ArrayList<>());
        resp.setSuggestions(new ArrayList<>());

        // 1. 调用 Python 接口 (不再从数据库抓取数据作为参数传递)
        String jsonResult;
        try {
            // 直接发起 GET 请求获取预测结果
            jsonResult = HttpUtil.get(aiServiceUrl);
        } catch (Exception e) {
            e.printStackTrace();
            return resp;
        }

        if (!JSONUtil.isTypeJSON(jsonResult)) {
            return resp;
        }

        // 3. 解析预测结果
        List<PredictionPoint> points = JSONUtil.toList(jsonResult, PredictionPoint.class);
        resp.setChartData(points);

        // 4. 获取阈值配置
        Map<Object, Object> thresholdMap = redisTemplate.opsForHash().entries(ENV_THRESHOLD_KEY + envCode);
        if (thresholdMap.isEmpty()) {
            return resp;
        }
        
        Map<Integer, SysEnvThreshold> thresholds = new HashMap<>();
        thresholdMap.forEach((k, v) -> {
            SysEnvThreshold t = (SysEnvThreshold) v;
            thresholds.put(t.getEnvParameterType(), t);
        });

        // 5. 分析异常并生成建议
        List<AiSuggestion> suggestions = analyzePredictions(points, thresholds);
        resp.setSuggestions(suggestions);

        return resp;
    }

    private List<AiSuggestion> analyzePredictions(List<PredictionPoint> points, Map<Integer, SysEnvThreshold> thresholds) {
        List<AiSuggestion> list = new ArrayList<>();
        
        // 简单策略：每个小时的数据如果异常都记录，或者合并？
        // 这里的策略是：只要发现异常就生成一条建议。为了避免建议过多，可以只取最近的或者最严重的。
        // 这里演示为每个时间点的每个异常都生成（前端可以滚动显示）
        
        for (int i = 0; i < points.size(); i++) {
            PredictionPoint p = points.get(i);
            String futureTime = "未来 " + (i + 1) + " 小时";
            
            // 检查各个参数 (对应 EnvParameterType)
            // 1:空气温度, 2:空气湿度, 3:土壤温度, 4:土壤湿度, 5:CO2浓度, 6:光照强度
            
            checkThreshold(list, p.getAirTemp(), thresholds.get(1), futureTime, "空气温度", "°C", "fan", "风机");
            checkThreshold(list, p.getAirHumidity(), thresholds.get(2), futureTime, "空气湿度", "%", "humidifier", "加湿器"); // 假设低湿开加湿器
            checkThreshold(list, p.getSoilTemp(), thresholds.get(3), futureTime, "土壤温度", "°C", null, null);
            checkThreshold(list, p.getSoilHumidity(), thresholds.get(4), futureTime, "土壤湿度", "%", "pump", "水泵");
            checkThreshold(list, p.getCo2Concentration(), thresholds.get(5), futureTime, "CO2浓度", "ppm", "co2_generator", "CO2发生器");
            checkThreshold(list, p.getLightIntensity(), thresholds.get(6), futureTime, "光照强度", "Lux", "light", "补光灯");
        }
        
        return list;
    }

    private void checkThreshold(List<AiSuggestion> list, BigDecimal value, SysEnvThreshold t, String time, String name, String unit, String deviceCode, String deviceName) {
        if (value == null || t == null) return;
        
        BigDecimal min = t.getMinValue();
        BigDecimal max = t.getMaxValue();
        
        if (value.compareTo(max) > 0) {
            AiSuggestion s = new AiSuggestion();
            s.setTime(time);
            s.setTitle(name + "过高预警");
            s.setContent(String.format("预测%s%s将达到 %s%s，超过阈值 %s%s，建议开启降温/排风设备。", time, name, value, unit, max, unit));
            s.setType("danger");
            s.setActionDevice(deviceCode); // 简单映射
            s.setDeviceName(deviceName);
            list.add(s);
        } else if (value.compareTo(min) < 0) {
            AiSuggestion s = new AiSuggestion();
            s.setTime(time);
            s.setTitle(name + "过低预警");
            s.setContent(String.format("预测%s%s将降至 %s%s，低于阈值 %s%s，建议开启补充设备。", time, name, value, unit, min, unit));
            s.setType("warning");
            s.setActionDevice(deviceCode);
            s.setDeviceName(deviceName);
            list.add(s);
        }
    }
}
