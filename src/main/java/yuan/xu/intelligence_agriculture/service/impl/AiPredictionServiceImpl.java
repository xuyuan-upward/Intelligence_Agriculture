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
import yuan.xu.intelligence_agriculture.websocket.WebSocketServer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
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

    @Autowired
    private WebSocketServer webSocketServer;

    @Override
    public AiAnalysisResp getPrediction(String envCode) {
        AiAnalysisResp resp = new AiAnalysisResp();
        resp.setChartData(new ArrayList<>());
        resp.setSuggestions(new ArrayList<>());

        // 1. 获取过去 6 小时的数据
        long now = System.currentTimeMillis();
        long sixHoursAgo = now - 6 * 60 * 60 * 1000;
        
        List<IotSensorData> historyData = iotDataService.lambdaQuery()
                .eq(IotSensorData::getGreenhouseEnvCode, envCode)
                .ge(IotSensorData::getCreateTime, new Date(sixHoursAgo))
                .le(IotSensorData::getCreateTime, new Date(now))
                .orderByAsc(IotSensorData::getCreateTime)
                .list();

        // 获取最新一条数据，用于计算建议值
        IotSensorData latestData = historyData.isEmpty() ? null : historyData.get(historyData.size() - 1);

        // 2. 调用 Python 接口
        String jsonResult;
        try {
            String jsonBody = JSONUtil.toJsonStr(historyData);
            jsonResult = HttpUtil.post(aiServiceUrl, jsonBody);
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

        // 5. 分析异常并生成建议 (改进后的逻辑：连续异常合并，并计算建议值)
        List<AiSuggestion> suggestions = analyzePredictionsWithSummary(points, thresholds, latestData, envCode);
        resp.setSuggestions(suggestions);

        return resp;
    }

    /**
     * 改进后的风险分析逻辑
     * 1. 检测连续异常
     * 2. 合并异常点，提取最早和最晚时间点
     * 3. 计算持续时间
     * 4. 根据公式计算建议值 (TargetValue)
     */
    private List<AiSuggestion> analyzePredictionsWithSummary(List<PredictionPoint> points, 
                                                            Map<Integer, SysEnvThreshold> thresholds, 
                                                            IotSensorData latestData,
                                                            String envCode) {
        List<AiSuggestion> suggestions = new ArrayList<>();
        if (points == null || points.isEmpty() || latestData == null) return suggestions;

        // 定义参数映射
        // Type: 1:空气温度, 2:空气湿度, 3:土壤温度, 4:土壤湿度, 5:CO2浓度, 6:光照强度
        Map<Integer, ParamConfig> paramConfigs = new HashMap<>();
        paramConfigs.put(1, new ParamConfig("空气温度", "°C", PredictionPoint::getAirTemp, IotSensorData::getAirTemp, "C_HEATER_001", "加热片", false, "空气温度调节", "开启升温"));
        paramConfigs.put(2, new ParamConfig("空气湿度", "%", PredictionPoint::getAirHumidity, IotSensorData::getAirHumidity, "C_HUMIDIFIER_001", "加湿器", false, "加湿条件", "开启加湿"));
        paramConfigs.put(3, new ParamConfig("土壤温度", "°C", PredictionPoint::getSoilTemp, IotSensorData::getSoilTemp, "C_HEATER_002", "土壤加热片", false, "土壤温度调节", "开启土壤加热"));
        paramConfigs.put(4, new ParamConfig("土壤湿度", "%", PredictionPoint::getSoilHumidity, IotSensorData::getSoilHumidity, "C_WATER_001", "水泵", false, "灌溉条件", "开始灌溉"));
        paramConfigs.put(5, new ParamConfig("CO2浓度", "ppm", PredictionPoint::getCo2Concentration, IotSensorData::getCo2Concentration, "C_FAN_001", "风机", true, "通风条件", "开启排风"));
        paramConfigs.put(6, new ParamConfig("光照强度", "Lux", PredictionPoint::getLightIntensity, IotSensorData::getLightIntensity, "C_LIGHT_001", "补光灯", false, "补光条件", "开始补光"));

        for (Map.Entry<Integer, ParamConfig> entry : paramConfigs.entrySet()) {
            Integer type = entry.getKey();
            ParamConfig config = entry.getValue();
            SysEnvThreshold threshold = thresholds.get(type);
            if (threshold == null) continue;

            BigDecimal minThreshold = threshold.getMinValue();
            BigDecimal maxThreshold = threshold.getMaxValue();

            // 寻找连续异常区间
            List<Range> abnormalRanges = findAbnormalRanges(points, config, minThreshold, maxThreshold);

            // 如果有多个异常区间，我们取"整体"的统计信息
            if (!abnormalRanges.isEmpty()) {
                // 最早时间点
                Range firstRange = abnormalRanges.get(0);
                String earliestTime = points.get(firstRange.start).getTime();
                
                // 最晚时间点 (用于计算持续时间，这里简化为所有异常时间段的总和，或者第一个开始到最后一个结束？)
                // 用户说："提取最早...和最晚...计算持续时间"。
                // 假设是：从第一个异常开始，到最后一个异常结束的总跨度
                Range lastRange = abnormalRanges.get(abnormalRanges.size() - 1);
                int startIdx = firstRange.start;
                int endIdx = lastRange.end;
                int duration = endIdx - startIdx + 1; // 假设每点代表1小时
                
                // 寻找预测区间内的极值 (PredMin 或 PredMax)
                BigDecimal predExtreme = firstRange.extremeVal;
                for(Range r : abnormalRanges) {
                    if (config.isHighRisk) {
                        if (r.extremeVal.compareTo(predExtreme) > 0) predExtreme = r.extremeVal;
                    } else {
                        if (r.extremeVal.compareTo(predExtreme) < 0) predExtreme = r.extremeVal;
                    }
                }

                // 计算建议值
                BigDecimal latestVal = config.latestValueGetter.apply(latestData);
                if (latestVal == null) latestVal = BigDecimal.ZERO;

                BigDecimal targetVal; // 这里作为"Target"展示在"提升至..."
                BigDecimal deltaVal;  // 补充/减少的量

                String actionPrefix;
                
                // 计算公式更新
                // num = max(latestVal, minThreshold)
                // target (delta) = (num - predExtreme) + margin
                // Display Target = latestVal + delta (Raise to ...)

                if (config.isHighRisk) {
                    // CO2 高于阈值 (maxThreshold)
                    // num = min(latestVal, maxThreshold) -- 逻辑反转?
                    // 用户只给了低于阈值的公式。对于高于阈值，假设是对称的。
                    // 假设 num = latestVal < max ? latestVal : max
                    // delta = (latestVal - predExtreme) ... 
                    // Let's stick to simple logic for High Risk if user didn't specify.
                    // Or reuse the logic:
                    // Delta = (PredMax - Latest) + Margin
                    
                    BigDecimal margin = maxThreshold.multiply(new BigDecimal("0.05"));
                    deltaVal = predExtreme.subtract(latestVal).add(margin);
                    
                    actionPrefix = config.actionName;
                    targetVal = latestVal.subtract(deltaVal); // 降低至...
                } else {
                    // 低于阈值 (minThreshold)
                    // num = max(latestVal, minThreshold)
                    BigDecimal num = latestVal.compareTo(minThreshold) > 0 ? latestVal : minThreshold;
                    BigDecimal margin = minThreshold.multiply(new BigDecimal("0.05"));
                    
                    // target (delta) = (num - predMin) + margin
                    deltaVal = num.subtract(predExtreme).add(margin);
                    
                    // Display Target (提升至) = latestVal + delta
                    targetVal = latestVal.add(deltaVal); 
                    actionPrefix = config.actionName;
                }
                
                // 修正 TargetVal 显示，用户模板说 "提升至 xx (target)"
                // 如果是光照，我们显示补充量。如果是湿度，显示目标值。
                String targetStr;
                if (type == 6) { // 光照
                     // 光照通常说"补充多少Lux"，或者"开启补光灯"
                     // 用户说 "target = (num - pred) + margin" -> Light_target
                     // 假设用户想看到的是这个 Delta
                     targetStr = deltaVal.setScale(1, RoundingMode.HALF_UP) + " " + config.unit;
                } else {
                     targetStr = targetVal.setScale(1, RoundingMode.HALF_UP) + config.unit;
                }

                // 格式化消息
                // 🟡 预测预警（L1）
                // 🟡 预计 4 小时后 土壤湿度可能低于安全阈值
                // 建议提前关注灌溉条件
                // 建议在 3 小时内 开始灌溉，
                // 将土壤湿度提升至 38–40%（target ）
                String content = String.format(
                        "🟡 预测预警（L1）\n" +
                        "🟡 预计 %s 后 %s可能%s安全阈值\n" +
                        "建议提前关注%s\n" +
                        "建议在 %d 小时内 %s，\n" +
                        "将%s%s至 %s (target)",
                        earliestTime, config.name, config.isHighRisk ? "高于" : "低于",
                        config.focusTarget,
                        Math.max(1, startIdx), // 建议在X小时内开始 (即异常开始前)
                        actionPrefix,
                        config.name, config.isHighRisk ? "降低" : "提升", targetStr
                );

                AiSuggestion s = new AiSuggestion();
                s.setTime("未来" + (startIdx + 1) + "小时");
                s.setTitle(config.name + (config.isHighRisk ? "过高" : "过低") + "风险");
                s.setContent(content);
                s.setType(config.isHighRisk ? "danger" : "warning");
                s.setActionDevice(config.deviceCode);
                s.setDeviceName(config.deviceName);
                suggestions.add(s);

                // 推送给控制中心 (WebSocket) - 只推一条，且增加防抖 (30分钟内不重复推送同类型)
                pushToControlCenter(envCode, content, type);
            }
        }

        return suggestions;
    }

    private List<Range> findAbnormalRanges(List<PredictionPoint> points, ParamConfig config, BigDecimal min, BigDecimal max) {
        List<Range> ranges = new ArrayList<>();
        Range currentRange = null;

        for (int i = 0; i < points.size(); i++) {
            BigDecimal val = config.valueGetter.apply(points.get(i));
            if (val == null) continue;

            boolean abnormal = config.isHighRisk ? (val.compareTo(max) > 0) : (val.compareTo(min) < 0);

            if (abnormal) {
                if (currentRange == null) {
                    currentRange = new Range(i, i, val);
                } else {
                    currentRange.end = i;
                    if (config.isHighRisk) {
                        if (val.compareTo(currentRange.extremeVal) > 0) currentRange.extremeVal = val;
                    } else {
                        if (val.compareTo(currentRange.extremeVal) < 0) currentRange.extremeVal = val;
                    }
                }
            } else {
                if (currentRange != null) {
                    ranges.add(currentRange);
                    currentRange = null;
                }
            }
        }
        if (currentRange != null) ranges.add(currentRange);
        return ranges;
    }

    private void pushToControlCenter(String envCode, String content, Integer type) {
        // 使用 Redis 进行防抖，避免重复推送
        String throttleKey = "AI_RISK_LOG_THROTTLE:" + envCode + ":" + type;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(throttleKey))) {
            return; // 30分钟内已推送过同类型告警，跳过
        }

        WebSocketServer.WebSocketSendInfo("AI_RISK_LOG", envCode, content);
        
        // 设置 30 分钟过期时间
        redisTemplate.opsForValue().set(throttleKey, "SENT", 30, java.util.concurrent.TimeUnit.MINUTES);
    }

    // 内部类用于辅助计算
    private static class Range {
        int start;
        int end;
        BigDecimal extremeVal; // 最小值(对于低风险)或最大值(对于高风险)

        Range(int start, int end, BigDecimal extremeVal) {
            this.start = start;
            this.end = end;
            this.extremeVal = extremeVal;
        }
    }

    private static class ParamConfig {
        String name;
        String unit;
        Function<PredictionPoint, BigDecimal> valueGetter;
        Function<IotSensorData, BigDecimal> latestValueGetter;
        String deviceCode;
        String deviceName;
        boolean isHighRisk; // 是否是关注"过高"的情况 (如CO2)

        String focusTarget;
        String actionName;

        ParamConfig(String name, String unit, 
                    Function<PredictionPoint, BigDecimal> valueGetter,
                    Function<IotSensorData, BigDecimal> latestValueGetter,
                    String deviceCode, String deviceName, boolean isHighRisk,
                    String focusTarget, String actionName) {
            this.name = name;
            this.unit = unit;
            this.valueGetter = valueGetter;
            this.latestValueGetter = latestValueGetter;
            this.deviceCode = deviceCode;
            this.deviceName = deviceName;
            this.isHighRisk = isHighRisk;
            this.focusTarget = focusTarget;
            this.actionName = actionName;
        }
    }

    @Deprecated
    private List<AiSuggestion> analyzePredictions(List<PredictionPoint> points, Map<Integer, SysEnvThreshold> thresholds) {
        // 此方法已弃用，使用 analyzePredictionsWithSummary 代替
        return new ArrayList<>();
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
