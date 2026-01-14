package yuan.xu.intelligence_agriculture.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.model.SysControlLog;
import yuan.xu.intelligence_agriculture.model.SysDevice;
import yuan.xu.intelligence_agriculture.mapper.SysControlLogMapper;
import yuan.xu.intelligence_agriculture.mapper.SysDeviceMapper;
import yuan.xu.intelligence_agriculture.mqtt.MqttGateway;
import yuan.xu.intelligence_agriculture.service.SysDeviceService;

import org.springframework.data.redis.core.RedisTemplate;
import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 设备管理与控制业务实现类
 * 负责设备的手动控制、自动控制逻辑校验以及 MQTT 控制命令下发
 */
@Service
@Slf4j
public class SysDeviceServiceImpl extends ServiceImpl<SysDeviceMapper, SysDevice> implements SysDeviceService {

    @Autowired
    private MqttGateway mqttGateway;

    @Autowired
    private SysControlLogMapper sysControlLogMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Redis 中存储自动控制设备的 Key
     */
    private static final String AUTO_DEVICE_KEY = "iot:auto_devices";

    /**
     * 项目启动时初始化缓存
     */
    @PostConstruct
    public void init() {
        refreshAutoDeviceCache();
    }

    /**
     * 刷新自动控制设备缓存
     * 将处于“自动模式”的设备信息从数据库加载到 Redis 中。
     * 这样做的好处是：
     * 1. 分布式支持：如果部署多个后端实例，所有实例都能共享同一份阈值数据。
     * 2. 性能：Redis 读取速度极快，满足 2 秒级的高频校验。
     */
    private void refreshAutoDeviceCache() {
        List<SysDevice> list = this.list(new LambdaQueryWrapper<SysDevice>()
                .eq(SysDevice::getControlMode, 1)); // 1 代表自动模式
        
        // 先删除旧缓存
        redisTemplate.delete(AUTO_DEVICE_KEY);
        
        if (!list.isEmpty()) {
            // 将列表存入 Redis，设置 24 小时过期（仅作为保险，实际会主动刷新）
            redisTemplate.opsForValue().set(AUTO_DEVICE_KEY, list, 24, TimeUnit.HOURS);
        }
        log.info("Redis 自动控制设备缓存已刷新，当前自动模式设备数量: {}", list.size());
    }

    /**
     * 执行设备开关控制
     * @param deviceId 设备ID
     * @param status 目标状态 (0:关, 1:开)
     */
    @Override
    public void controlDevice(Long deviceId, Integer status) {
        SysDevice device = this.getById(deviceId);
        if (device == null) return;

        log.info("执行设备控制: 设备名称={}, 目标状态={}", device.getDeviceName(), status == 1 ? "开启" : "关闭");

        // 1. 更新本地数据库中的设备状态
        device.setStatus(status);
        this.updateById(device);

        // 2. 封装 MQTT 控制指令并下发
        Map<String, Object> command = new HashMap<>();
        command.put("deviceCode", device.getDeviceCode());
        command.put("command", status == 1 ? "ON" : "OFF");
        mqttGateway.sendToMqtt(JSONUtil.toJsonStr(command));

        // 3. 记录控制日志
        SysControlLog logEntry = new SysControlLog();
        logEntry.setDeviceId(deviceId);
        logEntry.setOperationType(device.getControlMode() == 1 ? "AUTO" : "MANUAL");
        logEntry.setOperationDesc((device.getControlMode() == 1 ? "自动" : "手动") + (status == 1 ? "开启" : "关闭") + device.getDeviceName());
        logEntry.setCreateTime(new Date());
        sysControlLogMapper.insert(logEntry);
        
        // 4. 如果是自动模式设备，控制后其状态变了，建议刷新一下缓存中的状态
        if (device.getControlMode() == 1) {
            refreshAutoDeviceCache();
        }
    }

    /**
     * 更新设备控制模式及阈值
     */
    @Override
    public void updateMode(Long deviceId, Integer mode, BigDecimal min, BigDecimal max) {
        SysDevice device = this.getById(deviceId);
        if (device == null) return;
        device.setControlMode(mode);
        if (min != null) device.setThresholdMin(min);
        if (max != null) device.setThresholdMax(max);
        this.updateById(device);
        
        // 关键：更新数据库后，必须刷新 Redis 缓存，确保下一次传感器数据到来时使用最新的模式和阈值
        refreshAutoDeviceCache();
        
        log.info("更新设备模式并同步 Redis 完成: 设备={}, 模式={}, 阈值范围=[{}, {}]", 
                device.getDeviceName(), mode == 1 ? "自动" : "手动", min, max);
    }

    /**
     * 自动控制逻辑校验
     * 从 Redis 中获取自动模式的设备进行校验
     * @param data 最新传感器数据
     */
    @Override
    @SuppressWarnings("unchecked")
    public void checkAndAutoControl(IotSensorData data) {
        // 从 Redis 获取缓存的自动模式设备列表
        List<SysDevice> autoDeviceCache = (List<SysDevice>) redisTemplate.opsForValue().get(AUTO_DEVICE_KEY);
        
        if (autoDeviceCache == null || autoDeviceCache.isEmpty()) {
            return;
        }

        for (SysDevice device : autoDeviceCache) {
            BigDecimal currentValue = null;
            Integer thresholdType = device.getAutoThresholdType();
            
            // 根据数字标识符获取对应的传感器数值
            if (Integer.valueOf(1).equals(thresholdType)) { // 1: 空气温度
                currentValue = data.getAirTemp();
            } else if (Integer.valueOf(2).equals(thresholdType)) { // 2: 空气湿度
                currentValue = data.getAirHumidity();
            } else if (Integer.valueOf(3).equals(thresholdType)) { // 3: 土壤温度
                currentValue = data.getSoilTemp();
            } else if (Integer.valueOf(4).equals(thresholdType)) { // 4: 土壤湿度
                currentValue = data.getSoilHumidity();
            } else if (Integer.valueOf(5).equals(thresholdType)) { // 5: 光照强度
                currentValue = data.getLightIntensity();
            } else if (Integer.valueOf(6).equals(thresholdType)) { // 6: CO2浓度
                currentValue = data.getCo2Concentration();
            }
            
            if (currentValue == null) continue;

            boolean shouldOpen = false;
            boolean shouldClose = false;

            // 1: 风扇(FAN) - 属于降温/排气设备（高值开启，低值关闭）
            if (Integer.valueOf(1).equals(device.getDeviceType())) {
                 if (currentValue.compareTo(device.getThresholdMax()) > 0) shouldOpen = true;
                 else if (currentValue.compareTo(device.getThresholdMin()) < 0) shouldClose = true;
            } 
            // 2: 水泵(PUMP), 3: 补光灯(LIGHT), 4: 加热器(HEATER), 5: 加湿器(HUMIDIFIER), 6: CO2发生器(CO2_GENERATOR)
            // 以上均属于补充类设备（低值开启，高值关闭）
            else {
                 if (currentValue.compareTo(device.getThresholdMin()) < 0) shouldOpen = true;
                 else if (currentValue.compareTo(device.getThresholdMax()) > 0) shouldClose = true;
            }

            if (shouldOpen && device.getStatus() == 0) {
                log.info("【Redis 自动控制触发】指标: {}, 当前值: {}, 开启设备: {}", 
                        thresholdType, currentValue, device.getDeviceName());
                controlDevice(device.getId(), 1);
            } else if (shouldClose && device.getStatus() == 1) {
                log.info("【Redis 自动控制触发】指标: {}, 当前值: {}, 关闭设备: {}", 
                        thresholdType, currentValue, device.getDeviceName());
                controlDevice(device.getId(), 0);
            }
        }
    }
}
