package yuan.xu.intelligence_agriculture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.RedisTemplate;
import yuan.xu.intelligence_agriculture.dto.SensorDataDTO;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.mapper.IotSensorDataMapper;
import yuan.xu.intelligence_agriculture.service.IotDataService;
import yuan.xu.intelligence_agriculture.service.SysDeviceService;
import yuan.xu.intelligence_agriculture.websocket.WebSocketServer;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 传感器数据处理业务实现类
 * 负责接收、解析、校验 MQTT 原始数据，并持久化到数据库及实时推送至 WebSocket 客户端
 */
@Service
@Slf4j
public class IotDataServiceImpl extends ServiceImpl<IotSensorDataMapper, IotSensorData> implements IotDataService {

    @Autowired
    private SysDeviceService sysDeviceService;

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
            
            // 2. 更新设备在线状态到 Redis
            // 采用“点名机制”：不仅更新网关状态，还根据数据字段更新各个传感器的状态
            if (dto.getDeviceCode() != null) {
                String gatewayCode = dto.getDeviceCode();
                long now = System.currentTimeMillis();
                
                // 更新网关本身状态
                redisTemplate.opsForValue().set(DEVICE_LAST_ACTIVE_KEY + gatewayCode, now, 10, TimeUnit.SECONDS);
                
                // 如果对应传感器数据不为空，则更新该传感器的独立在线状态
                // 约定子设备 Code 格式：网关Code + _ + 传感器类型ID
                // 到时候对应的设备也用这个对应的DEVICE_LAST_ACTIVE_KEY + gatewayCode + "_11"作为MQTT客户端ID
                if (dto.getAirTemp() != null) 
                    redisTemplate.opsForValue().set(DEVICE_LAST_ACTIVE_KEY + gatewayCode + "_11", now, 10, TimeUnit.SECONDS);
                if (dto.getAirHumidity() != null) 
                    redisTemplate.opsForValue().set(DEVICE_LAST_ACTIVE_KEY + gatewayCode + "_12", now, 10, TimeUnit.SECONDS);
                if (dto.getSoilTemp() != null) 
                    redisTemplate.opsForValue().set(DEVICE_LAST_ACTIVE_KEY + gatewayCode + "_13", now, 10, TimeUnit.SECONDS);
                if (dto.getSoilHumidity() != null) 
                    redisTemplate.opsForValue().set(DEVICE_LAST_ACTIVE_KEY + gatewayCode + "_14", now, 10, TimeUnit.SECONDS);
                if (dto.getLightIntensity() != null) 
                    redisTemplate.opsForValue().set(DEVICE_LAST_ACTIVE_KEY + gatewayCode + "_15", now, 10, TimeUnit.SECONDS);
                if (dto.getCo2Concentration() != null) 
                    redisTemplate.opsForValue().set(DEVICE_LAST_ACTIVE_KEY + gatewayCode + "_16", now, 10, TimeUnit.SECONDS);
            }

            // 3. 数据校验：确保关键字段不为空
            if (dto.getAirTemp() == null || dto.getAirHumidity() == null) {
                log.warn("数据校验未通过，缺失关键环境指标: {}", payload);
                return;
            }

            // 3. 转换为实体类并设置时间戳
            IotSensorData data = new IotSensorData();
            BeanUtil.copyProperties(dto, data);
            data.setCreateTime(new Date());

            // 4. 持久化到数据库：保存实时上传的 6 个参数数值
            boolean saved = this.save(data);
            if (!saved) {
                log.error("传感器数据保存数据库失败!");
                return;
            }

            // 5. 触发自动控制逻辑：根据最新环境数据和用户设置的阈值决定是否开关设备
            sysDeviceService.checkAndAutoControl(data);

            // 6. WebSocket 实时推送：将解析后的数据推送到前端展示
            // 采用结构化 JSON 格式，方便前端区分数据类型
            HashMap<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "SENSOR_DATA");
            wsMessage.put("data", data);
            WebSocketServer.sendInfo(JSONUtil.toJsonStr(wsMessage));
            
            log.info("MQTT 传感器数据处理完成并已推送到 WebSocket 客户端");

        } catch (Exception e) {
            log.error("处理传感器数据时发生异常", e);
        }
    }
}
