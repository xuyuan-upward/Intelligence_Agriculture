package yuan.xu.intelligence_agriculture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.RedisTemplate;
import yuan.xu.intelligence_agriculture.dto.SensorData;
import yuan.xu.intelligence_agriculture.dto.SensorDataDTO;
import yuan.xu.intelligence_agriculture.enums.EnvParameterType;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.mapper.IotSensorDataMapper;
import yuan.xu.intelligence_agriculture.service.IotDataService;
import yuan.xu.intelligence_agriculture.service.SysControlDeviceService;
import yuan.xu.intelligence_agriculture.websocket.WebSocketServer;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static yuan.xu.intelligence_agriculture.enums.EnvParameterType.*;

/**
 * 传感器数据处理业务实现类
 * 负责接收、解析、校验 MQTT 原始数据，并持久化到数据库及实时推送至 WebSocket 客户端
 */
@Service
@Slf4j
public class IotDataServiceImpl extends ServiceImpl<IotSensorDataMapper, IotSensorData> implements IotDataService {

    @Autowired
    private SysControlDeviceService sysControlDeviceService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Redis 中存储设备最后在线时间的 Key 前缀
     */
    private static final String DEVICE_LAST_ACTIVE_KEY = "iot:device:active:";

    /**
     * 处理从 MQTT 接收到的传感器数据
     * @param payload 原始 MQTT 消息内容（JSON 格式）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processSensorData(String payload) {
        log.info("开始处理 MQTT 传感器数据: {}", payload);
        try {
            // 1. 解析 JSON 数据为 DTO 对象
            if (!JSONUtil.isTypeJSON(payload)) {
                log.warn("非法数据格式，忽略处理: {}", payload);
                return;
            }
            SensorDataDTO dto = JSONUtil.toBean(payload, SensorDataDTO.class);
            
            // 2. 更新采集设备在线状态到 Redis
            String greenhouseEnvCode = dto.getGreenhouseEnvCode();
            List<SensorData> sensorDataList = dto.getSensorDataList();
            if (dto.getGreenhouseEnvCode() != null) {
                String envCode = dto.getGreenhouseEnvCode();
                long now = System.currentTimeMillis();
                IotSensorData data = new IotSensorData();
                data.setGreenhouseEnvCode(envCode);
                data.setCreateTime(new Date());
                for (SensorData sensorData : sensorDataList) {
                    String deviceCode = sensorData.getDeviceCode();
                    Integer type = sensorData.getEnvParameterType();
                    BigDecimal value = sensorData.getData() != null ? new BigDecimal(sensorData.getData()) : null;
                    // 更新每个采集传感器的独立在线状态
                    redisTemplate.opsForValue().set(DEVICE_LAST_ACTIVE_KEY + envCode + deviceCode, now, 10, TimeUnit.SECONDS);
                    
                    if (type == null || value == null) continue;

                    // 根据采集参数类型给对应的字段赋值
                    if (AIR_TEMP.getEnvParameterType()==type) data.setAirTemp(value);
                    else if (AIR_HUMIDITY.getEnvParameterType()==type) data.setAirHumidity(value);
                    else if (SOIL_TEMP.getEnvParameterType()==type) data.setSoilTemp(value);
                    else if (SOIL_HUMIDITY.getEnvParameterType()==type) data.setSoilHumidity(value);
                    else if (CO2_CONCENTRATION.getEnvParameterType()==type) data.setCo2Concentration(value);
                    else if (LIGHT_INTENSITY.getEnvParameterType()==type) data.setLightIntensity(value);
                }

                // todo 4. 持久化到数据库 有待优化:得进行批量插入
                boolean saved = this.save(data);
                if (!saved) {
                    log.error("传感器数据保存数据库失败!");
                    return;
                }

                // 5. 触发自动"控制"设备逻辑
                sysControlDeviceService.checkAndAutoControl(data);

                // 6. WebSocket 实时推送
                HashMap<String, Object> wsMessage = new HashMap<>();
                wsMessage.put("type", "SENSOR_DATA");
                wsMessage.put("data", data);
                WebSocketServer.sendInfo(JSONUtil.toJsonStr(wsMessage));
                
                log.info("MQTT 传感器数据处理完成并已推送到 WebSocket 客户端");
            }

        } catch (Exception e) {
            log.error("处理传感器数据时发生异常", e);
        }
    }
}
