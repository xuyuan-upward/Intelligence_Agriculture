package yuan.xu.intelligence_agriculture.service.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.model.SysControlDevice;
import yuan.xu.intelligence_agriculture.model.SysEnvThreshold;
import yuan.xu.intelligence_agriculture.resp.AiAnalysisResp;
import yuan.xu.intelligence_agriculture.resp.AiSuggestion;
import yuan.xu.intelligence_agriculture.resp.PredictionPoint;
import yuan.xu.intelligence_agriculture.service.AiPredictionService;
import yuan.xu.intelligence_agriculture.service.IotDataService;
import yuan.xu.intelligence_agriculture.service.SysControlDeviceService;
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

    @Value("${ai.service.timeout-ms:15000}")
    private int aiServiceTimeoutMs;

    @Autowired
    private IotDataService iotDataService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private WebSocketServer webSocketServer;

    @Autowired
    private SysControlDeviceService sysControlDeviceService;

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
            jsonResult = HttpUtil.post(aiServiceUrl, jsonBody, aiServiceTimeoutMs);
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

        // 5. 从数据库获取当前环境下的控制设备，按 envThresholdId（即 envParameterType）分组
        List<SysControlDevice> devices = sysControlDeviceService.listControlDevices(envCode);
        Map<Integer, SysControlDevice> deviceByType = new HashMap<>();
        if (devices != null) {
            for (SysControlDevice d : devices) {
                if (d.getEnvThresholdId() != null) {
                    deviceByType.put(d.getEnvThresholdId(), d);
                }
            }
        }

        // 6. 分析异常并生成建议 (改进后的逻辑：连续异常合并，并计算建议值)
        List<AiSuggestion> suggestions = analyzePredictionsWithSummary(points, thresholds, latestData, envCode, deviceByType);
        resp.setSuggestions(suggestions);

        return resp;
    }

    /**
     * 改进后的风险分析逻辑
     * 1. 检测连续异常
     * 2. 合并异常点，提取最早和最晚时间点
     * 3. 计算持续时间
     * 4. 根据公式计算建议值 (TargetValue)
     *
     * @param deviceByType 按 envParameterType 索引的控制设备 Map（key=envThresholdId，即 envParameterType）
     */
    private List<AiSuggestion> analyzePredictionsWithSummary(List<PredictionPoint> points,
                                                            Map<Integer, SysEnvThreshold> thresholds,
                                                            IotSensorData latestData,
                                                            String envCode,
                                                            Map<Integer, SysControlDevice> deviceByType) {
        List<AiSuggestion> suggestions = new ArrayList<>();
        if (points == null || points.isEmpty() || latestData == null) return suggestions;

        // 定义参数属性映射（环境参数本身的属性，不包含设备信息）
        // Type: 1:空气温度, 2:空气湿度, 3:土壤温度, 4:土壤湿度, 5:CO2浓度, 6:光照强度
        Map<Integer, ParamAttr> paramAttrs = new HashMap<>();
        paramAttrs.put(1, new ParamAttr("空气温度", "°C", PredictionPoint::getAirTemp, IotSensorData::getAirTemp, false, "空气温度调节", "开启升温"));
        paramAttrs.put(2, new ParamAttr("空气湿度", "%", PredictionPoint::getAirHumidity, IotSensorData::getAirHumidity, false, "加湿条件", "开启加湿"));
        paramAttrs.put(3, new ParamAttr("土壤温度", "°C", PredictionPoint::getSoilTemp, IotSensorData::getSoilTemp, false, "土壤温度调节", "开启土壤加热"));
        paramAttrs.put(4, new ParamAttr("土壤湿度", "%", PredictionPoint::getSoilHumidity, IotSensorData::getSoilHumidity, false, "灌溉条件", "开始灌溉"));
        paramAttrs.put(5, new ParamAttr("CO2浓度", "ppm", PredictionPoint::getCo2Concentration, IotSensorData::getCo2Concentration, true, "通风条件", "开启排风"));
        paramAttrs.put(6, new ParamAttr("光照强度", "Lux", PredictionPoint::getLightIntensity, IotSensorData::getLightIntensity, false, "补光条件", "开始补光"));

        for (Map.Entry<Integer, ParamAttr> entry : paramAttrs.entrySet()) {
            Integer type = entry.getKey();
            ParamAttr attr = entry.getValue();
            SysEnvThreshold threshold = thresholds.get(type);
            if (threshold == null) continue;

            // 从数据库动态获取该环境参数类型关联的控制设备
            SysControlDevice device = deviceByType.get(type);
            String deviceCode = (device != null) ? device.getDeviceCode() : null;
            String deviceName = (device != null) ? device.getDeviceName() : null;

            BigDecimal minThreshold = threshold.getMinValue();
            BigDecimal maxThreshold = threshold.getMaxValue();

            // 寻找连续异常区间
            List<Range> abnormalRanges = findAbnormalRanges(points, attr, minThreshold, maxThreshold);

            // 如果有多个异常区间，我们取"整体"的统计信息
            if (!abnormalRanges.isEmpty()) {
                // 最早时间点
                Range firstRange = abnormalRanges.get(0);
                String earliestTime = points.get(firstRange.start).getTime();
                
                Range lastRange = abnormalRanges.get(abnormalRanges.size() - 1);
                int startIdx = firstRange.start;
                int endIdx = lastRange.end;
                int duration = endIdx - startIdx + 1;
                
                // 寻找预测区间内的极值 (PredMin 或 PredMax)
                BigDecimal predExtreme = firstRange.extremeVal;
                for(Range r : abnormalRanges) {
                    if (attr.isHighRisk) {
                        if (r.extremeVal.compareTo(predExtreme) > 0) predExtreme = r.extremeVal;
                    } else {
                        if (r.extremeVal.compareTo(predExtreme) < 0) predExtreme = r.extremeVal;
                    }
                }

                // 计算建议值
                BigDecimal latestVal = attr.latestValueGetter.apply(latestData);
                if (latestVal == null) latestVal = BigDecimal.ZERO;

                BigDecimal targetVal;
                BigDecimal deltaVal;

                String actionPrefix;
                
                if (attr.isHighRisk) {
                    BigDecimal margin = maxThreshold.multiply(new BigDecimal("0.05"));
                    deltaVal = predExtreme.subtract(latestVal).add(margin);
                    
                    actionPrefix = attr.actionName;
                    targetVal = latestVal.subtract(deltaVal);
                } else {
                    BigDecimal num = latestVal.compareTo(minThreshold) > 0 ? latestVal : minThreshold;
                    BigDecimal margin = minThreshold.multiply(new BigDecimal("0.05"));
                    
                    deltaVal = num.subtract(predExtreme).add(margin);
                    
                    targetVal = latestVal.add(deltaVal); 
                    actionPrefix = attr.actionName;
                }
                
                String targetStr;
                if (type == 6) {
                     targetStr = deltaVal.setScale(1, RoundingMode.HALF_UP) + " " + attr.unit;
                } else {
                     targetStr = targetVal.setScale(1, RoundingMode.HALF_UP) + attr.unit;
                }

                String content = String.format(
                        "🟡 预测预警（L1）\n" +
                        "🟡 预计 %s 后 %s可能%s安全阈值\n" +
                        "建议提前关注%s\n" +
                        "建议在 %d 小时内 %s，\n" +
                        "将%s%s至 %s (target)",
                        earliestTime, attr.name, attr.isHighRisk ? "高于" : "低于",
                        attr.focusTarget,
                        Math.max(1, startIdx),
                        actionPrefix,
                        attr.name, attr.isHighRisk ? "降低" : "提升", targetStr
                );

                AiSuggestion s = new AiSuggestion();
                s.setTime("未来" + (startIdx + 1) + "小时");
                s.setTitle(attr.name + (attr.isHighRisk ? "过高" : "过低") + "风险");
                s.setContent(content);
                s.setType(attr.isHighRisk ? "danger" : "warning");
                s.setActionDevice(deviceCode);
                s.setDeviceName(deviceName);
                suggestions.add(s);

                // 推送给控制中心 (WebSocket) - 只推一条，且增加防抖 (30分钟内不重复推送同类型)
                pushToControlCenter(envCode, content, type);
            }
        }

        return suggestions;
    }

    private List<Range> findAbnormalRanges(List<PredictionPoint> points, ParamAttr attr, BigDecimal min, BigDecimal max) {
        List<Range> ranges = new ArrayList<>();
        Range currentRange = null;

        for (int i = 0; i < points.size(); i++) {
            BigDecimal val = attr.valueGetter.apply(points.get(i));
            if (val == null) continue;

            boolean abnormal = attr.isHighRisk ? (val.compareTo(max) > 0) : (val.compareTo(min) < 0);

            if (abnormal) {
                if (currentRange == null) {
                    currentRange = new Range(i, i, val);
                } else {
                    currentRange.end = i;
                    if (attr.isHighRisk) {
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

    /**
     * 环境参数属性（不含设备信息，设备信息从数据库动态获取）
     * 仅保留参数类型本身的固有属性：名称、单位、取值函数、风险方向等
     */
    private static class ParamAttr {
        String name;            // 参数中文名
        String unit;            // 参数单位
        Function<PredictionPoint, BigDecimal> valueGetter;       // 预测点取值函数
        Function<IotSensorData, BigDecimal> latestValueGetter;   // 最新数据取值函数
        boolean isHighRisk;     // 是否是关注"过高"的情况 (如CO2)

        String focusTarget;     // 建议关注的条件描述（如"灌溉条件"、"通风条件"）
        String actionName;      // 建议动作名称（如"开启升温"、"开始灌溉"）

        ParamAttr(String name, String unit,
                  Function<PredictionPoint, BigDecimal> valueGetter,
                  Function<IotSensorData, BigDecimal> latestValueGetter,
                  boolean isHighRisk,
                  String focusTarget, String actionName) {
            this.name = name;
            this.unit = unit;
            this.valueGetter = valueGetter;
            this.latestValueGetter = latestValueGetter;
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
