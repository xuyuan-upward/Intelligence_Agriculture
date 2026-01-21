package yuan.xu.intelligence_agriculture.service.impl;

import cn.hutool.core.lang.hash.Hash;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import yuan.xu.intelligence_agriculture.dto.SensorData;
import yuan.xu.intelligence_agriculture.mapper.SysControlDeviceMapper;
import yuan.xu.intelligence_agriculture.mapper.SysControlLogMapper;
import yuan.xu.intelligence_agriculture.mapper.SysEnvThresholdMapper;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.model.SysControlDevice;
import yuan.xu.intelligence_agriculture.model.SysControlLog;
import yuan.xu.intelligence_agriculture.model.SysEnvThreshold;
import yuan.xu.intelligence_agriculture.mqtt.MqttGateway;
import yuan.xu.intelligence_agriculture.req.DeviceModeReq;
import yuan.xu.intelligence_agriculture.req.DeviceModeReqs;
import yuan.xu.intelligence_agriculture.resp.DeviceStatusResp;
import yuan.xu.intelligence_agriculture.resp.SystemLogResp;
import yuan.xu.intelligence_agriculture.service.SysControlDeviceService;
import yuan.xu.intelligence_agriculture.service.SysEnvThresholdService;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static yuan.xu.intelligence_agriculture.enums.ControlStatus.OFF;
import static yuan.xu.intelligence_agriculture.enums.ControlStatus.ON;
import static yuan.xu.intelligence_agriculture.enums.EnvParameterType.*;
import static yuan.xu.intelligence_agriculture.key.RedisKey.*;
import static yuan.xu.intelligence_agriculture.websocket.WebSocketServer.WebSocketSendInfo;

/**
 * 控制设备管理与业务实现类
 */
@Service
@Slf4j
public class SysControlDeviceServiceImpl extends ServiceImpl<SysControlDeviceMapper, SysControlDevice> implements SysControlDeviceService {

    private static final long MIN_AUTO_CONTROL_INTERVAL_MS = 120_000L;
    private static final long LIGHT_ON_INTERVAL_MS = 120_000L;
    private static final long LIGHT_LOW_MIN_DURATION_MS = 60_000L;
    @Autowired
    private MqttGateway mqttGateway;


    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${mqtt.control-topic}")
    private String controlTopic;


    @PostConstruct
    public void init() {
        // 自动控制设备缓存
//        refreshAutoDeviceCache();
//        refreshAllDeviceCache();
    }

//    private void refreshAllDeviceCache() {
//        List<SysControlDevice> list = this.list();
//        redisTemplate.delete(ALL_CONTROL_DEVICES_KEY);
//        if (!list.isEmpty()) {
//            redisTemplate.opsForList().rightPushAll(ALL_CONTROL_DEVICES_KEY, list);
//            // 2. 为整个列表设置过期时间（例如：1小时）
//            redisTemplate.expire(ALL_CONTROL_DEVICES_KEY, 24, TimeUnit.HOURS);
//        }
//        log.info("Redis 全量控制设备缓存已刷新，当前设备总数: {}", list.size());
//    }

/*    // todo 后续可以实现某个控制设备开启自动,而不是开启自动模式后,所有设备都开启
    private void refreshAutoDeviceCache(String envCode) {
        // 获取某个环境下的自动控制模式下的所有设备
        List<SysControlDevice> list = this.list(new LambdaQueryWrapper<SysControlDevice>()
                .eq(SysControlDevice::getControlMode, 1).eq(SysControlDevice::getGreenhouseEnvCode, envCode));

        redisTemplate.delete(AUTO_DEVICE_KEY + envCode);
        if (!list.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(AUTO_DEVICE_KEY + envCode, list);
            // 2. 为整个列表设置过期时间（例如：1小时）
            redisTemplate.expire(AUTO_DEVICE_KEY + envCode, 24, TimeUnit.HOURS);
        }
        log.info("Redis 自动控制设备缓存已刷新，当前自动控制模式环境编码: {},设备数量: {}", envCode, list.size());
    }*/

    // todo 后续可以实现某个控制设备开启自动,而不是开启自动模式后,所有设备都开启
    private void refreshSingleAutoDeviceCache(String envCode, String deviceCode) {
        // 获取某个环境下的自动控制模式下的所有设备
        SysControlDevice sysControlDevice = this.baseMapper.selectOne(new LambdaQueryWrapper<SysControlDevice>()
                .eq(SysControlDevice::getControlMode, 1)
                .eq(SysControlDevice::getGreenhouseEnvCode, envCode)
                .eq(SysControlDevice::getDeviceCode, deviceCode));

        redisTemplate.delete(AUTO_DEVICE_KEY + envCode);

        if (sysControlDevice != null) {
            redisTemplate.opsForList().rightPushAll(AUTO_DEVICE_KEY + envCode + deviceCode, sysControlDevice);
            // 2. 为整个列表设置过期时间（例如：1小时）
            redisTemplate.expire(AUTO_DEVICE_KEY + envCode, 24, TimeUnit.HOURS);
        }
        log.info("Redis 自动控制设备缓存已刷新，当前自动控制模式环境编码: {},设备编码: {}", envCode, sysControlDevice.getDeviceCode());
    }


    /**
     * 下发命令&修改控制设备状态
     *
     * @param deviceCode 对应的设备唯一编码
     * @param status
     * @param envCode
     */
    @Override
    public void controlDevice(String deviceCode, Integer status, String envCode) {
        // 手动模式下，控制设备只需要更新一个状态并更新缓存
        if (!envIsAuto(envCode)) {
            redisTemplate.opsForHash().put(AUTO_DEVICE_STATUS_KEY + envCode, deviceCode, status);
            log.warn("环境: {} 【手动模式】控制设备: {}，设备状态：【{}】", envCode, deviceCode, status == 1 ? "开启" : "关闭");
        }
        LambdaQueryWrapper<SysControlDevice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysControlDevice::getDeviceCode, deviceCode);
        SysControlDevice device = this.getOne(queryWrapper);
        if (device == null) {
            log.warn("未找到设备: {}", deviceCode);
            return;
        }
        if (deviceCode == null || envCode == null || status == null) return;
        Map<String, Object> command = new HashMap<>();
        command.put("envCode", envCode);
        command.put("deviceCode", deviceCode);
        command.put("command", status);
        if (controlTopic == null || controlTopic.trim().isEmpty()) {
            log.warn("控制主题为空，取消下发: envCode={}, deviceCode={}", envCode, deviceCode);
            return;
        }
        String baseTopic = controlTopic.endsWith("/") ? controlTopic.substring(0, controlTopic.length() - 1) : controlTopic;
        String publishTopic = baseTopic + "/" + envCode + "/" + deviceCode;
        log.info("MQTT 控制下发: topic={}, payload={}", publishTopic, JSONUtil.toJsonStr(command));
        mqttGateway.sendToMqtt(JSONUtil.toJsonStr(command), publishTopic);

        SysControlLog logEntry = new SysControlLog();
        logEntry.setDeviceCode(deviceCode);
        logEntry.setOperationType(device.getControlMode());
        logEntry.setCreateTime(new Date());
        // todo 控制日志看是否要添加
//        sysControlLogMapper.insert(logEntry);

        // todo 更新数据库中设备的运行状态,不用数据库的状态了，直接用Redis作为主存储
//        device.setStatus(status);
//        device.setUpdateTime(new Date());
//        this.updateById(device);


        // todo 控制日志看后续完善
        SystemLogResp logDTO = new SystemLogResp();
        logDTO.setType(device.getControlMode() == 1 ? "success" : "primary");
        logDTO.setSource(device.getControlMode() == 1 ? "自动控制" : "手动控制");
        logDTO.setMessage(device.getDeviceName() + (status == 1 ? " 已开启" : " 已关闭"));
        WebSocketSendInfo("SYSTEM_LOG", envCode, logDTO);
    }

    /**
     * 更新某个环境的"单个"设备的控制模式
     */
    @Override
    public void updateSingleMode(DeviceModeReq req) {
        Integer mode = req.getMode();
        String deviceCode = req.getDeviceCode();
        String envCode = req.getEnvCode();
        boolean flag = this.update(
                Wrappers.<SysControlDevice>lambdaUpdate()
                        .eq(SysControlDevice::getDeviceCode, deviceCode)
                        .eq(SysControlDevice::getGreenhouseEnvCode, envCode).set(SysControlDevice::getControlMode, mode)
        );
        if (!flag) {
            return;
        }
        // 刷新的是哪个环境下的阈值模式
        refreshSingleAutoDeviceCache(envCode, deviceCode);
        log.info("更新单个设备模式并同步 Redis 完成: 设备={}, 模式={}",
                deviceCode, mode == 1 ? "自动" : "手动");
    }

    /**
     * todo 目前用来整个环境的判断,后面降低自动模式粒度只对某个设备来判断的时候,可以舍弃
     * @param envCode
     * @return
     */
    private boolean envIsAuto(String envCode) {
        String key = AUTO_MODE_KEY + envCode;
        Integer mode = (Integer) redisTemplate.opsForValue().get(key);
        // null跳过
        if (mode != null) {
            return mode == 1;
        }
        /// 获取自动控制的设备
        List<SysControlDevice> devices = fromCacheGetControlDevices(envCode);
        if (devices.isEmpty()) {
            return false;
        }
        // 设定当前
        redisTemplate.opsForValue().set(key, 1);
        return true;
    }

    /**
     * 从缓存中获取自动模式下的所有控制设备的信息
     * @param envCode
     * @return
     */
    private List<SysControlDevice> fromCacheGetControlDevices(String envCode) {
        String key = AUTO_DEVICE_KEY + envCode;
        @SuppressWarnings("unchecked")
        List<SysControlDevice> cache =
                (List<SysControlDevice>) redisTemplate.opsForValue().get(key);

        if (cache != null && !cache.isEmpty()) {
            return cache;
        }

        List<SysControlDevice> list = this.list(
                Wrappers.<SysControlDevice>lambdaQuery()
                        .eq(SysControlDevice::getGreenhouseEnvCode, envCode)
                        .eq(SysControlDevice::getControlMode, 1) // 自动控制模式的
        );

        redisTemplate.opsForValue().set(key, list);
        redisTemplate.expire(key, 24, TimeUnit.HOURS);
        return list;
    }

    /**
     * 1. 检查控制模式  2.自动模式:触发对应的控制,并且将对应的控制设备状态推送给前端
     * @param data
     * @param integerSensorDataMap
     */
    @SuppressWarnings("unchecked")
    @Override
    public void checkAndAutoControl(IotSensorData data, Map<Integer, SensorData> integerSensorDataMap) {
        String greenhouseEnvCode = data.getGreenhouseEnvCode();
        // 1 是否自动 (决策入口)
        if (!envIsAuto(greenhouseEnvCode)) {
            return;
        }
        // 2 从缓存中拿控制整个控制设备的信息
        List<SysControlDevice> autoDeviceCache = fromCacheGetControlDevices(greenhouseEnvCode);

        // 2 从缓存中拿控制每个设备的状态
        /**
         * 3.获取当前环境下的环境阈值
         * 用map来存储对应不同环境下的环境阈值,key根据不同环境来区分,filed:根据环境ID来区分,value:对应的整个环境阈值对象
         * 为什么filed:根据环境ID来区分?因为对应的控制器只和对应的环境阈值挂钩,其中挂钩是靠:环境阈值ID
         */
        Map<Object, Object> map = redisTemplate.opsForHash().entries(ENV_THRESHOLD_KEY + data.getGreenhouseEnvCode());
        HashMap<Long, SysEnvThreshold> sysEnvThresholdHashMap = new HashMap<>();
        map.forEach((k, v) -> sysEnvThresholdHashMap.put(Long.valueOf((String) k), (SysEnvThreshold) v));
        if (autoDeviceCache == null || autoDeviceCache.isEmpty() || sysEnvThresholdHashMap == null) {
            return;
        }
        // key:控制设备编码 value:状态
        // 更新前的
        Map<String, Integer> BeforeUpdateControlStatusMap  = fromCacheGetControlDeviceStatus(greenhouseEnvCode,autoDeviceCache);
        // 更新后的
        Map<String, Integer> AfterUpdateControlStatusMap = new HashMap<>(BeforeUpdateControlStatusMap);
        for (SysControlDevice sysControlDevice : autoDeviceCache) {
            autoControlOneDevice(sysControlDevice, data, integerSensorDataMap, sysEnvThresholdHashMap, AfterUpdateControlStatusMap);
        }

        // 统一推送设备状态变更
        if (!AfterUpdateControlStatusMap.isEmpty() && !BeforeUpdateControlStatusMap.equals(AfterUpdateControlStatusMap)) {
            // 刷新缓存
            redisTemplate.delete(AUTO_DEVICE_STATUS_KEY + greenhouseEnvCode);
            // Convert Map to List of Objects for frontend
            List<DeviceStatusResp> statusList = new ArrayList<>();
            AfterUpdateControlStatusMap.forEach((k, v) -> {
                statusList.add(new DeviceStatusResp(k, v));
            });
            WebSocketSendInfo("CONTROL_DEVICE_STATUS", greenhouseEnvCode, statusList);
            log.info("自动控制触发批量推送: 环境={}, 设备数={}", greenhouseEnvCode, statusList.size());
        }
    }

    private Map<String, Integer> fromCacheGetControlDeviceStatus(String greenhouseEnvCode,List<SysControlDevice> autoDeviceCache) {
        String key = AUTO_DEVICE_STATUS_KEY + greenhouseEnvCode;
        @SuppressWarnings("unchecked")
        Map<Object, Object> cache = (Map<Object, Object>) redisTemplate.opsForHash().entries(key);
        HashMap<String, Integer> autoDeviceStatusHashMap = new HashMap<>();
        cache.forEach((k, v) -> autoDeviceStatusHashMap.put(((String) k), (Integer) v));
        if (autoDeviceStatusHashMap != null && !autoDeviceStatusHashMap.isEmpty()) {
            return autoDeviceStatusHashMap;
        }else {
            List<String> list = autoDeviceCache.stream().map(SysControlDevice::getDeviceCode).collect(Collectors.toList());
            HashMap<String, Integer> hashMap = new HashMap<>() {{
                list.forEach(deviceCode -> put(deviceCode, 0));
            }};
            redisTemplate.opsForHash().putAll(key, hashMap);
            return hashMap;
        }
    }

    /**
     * 更新某个环境"所有"控制设备的控制模式
     */
    @Override
    public void updatesDevicesMode(DeviceModeReqs reqs) {
        Integer mode = reqs.getMode();
        String envCode = reqs.getEnvCode();
        List<SysControlDevice> sysControlDeviceList = this.list(
                Wrappers.<SysControlDevice>lambdaQuery()
                        .eq(SysControlDevice::getGreenhouseEnvCode, envCode)
        );
        if (sysControlDeviceList.isEmpty()) return;
        for (SysControlDevice sysControlDevice : sysControlDeviceList) {
            sysControlDevice.setControlMode(mode);
        }
        updateBatchById(sysControlDeviceList);
        
        // 清除缓存当前环境下的所有控制设备的缓存
        redisTemplate.delete(AUTO_DEVICE_KEY + envCode);
        redisTemplate.delete(AUTO_MODE_KEY + envCode);

        // 重构缓存当前环境下的所有控制设备的缓存
        fromCacheGetControlDevices(envCode);
        log.info("更新多个设备模式并同步 Redis 完成: 设备数量={}, 模式={}",
                sysControlDeviceList.size(), mode == 1 ? "自动" : "手动");

    }

    /**
     * 判断某个设备是否处于自动控制模式
     */
    private boolean deviceIsAuto(SysControlDevice device) {
        return device.getControlMode() != null && device.getControlMode() == 1;
    }

    /**
     * 单设备自动检查 + 控制
     * @param device
     * @param data
     * @param sensorDataMap
     * @param thresholdMap
     */
    private void autoControlOneDevice(SysControlDevice device,
                                      IotSensorData data,
                                      Map<Integer, SensorData> sensorDataMap,
                                      Map<Long, SysEnvThreshold> thresholdMap,
                                      Map<String, Integer> updatedDevices) {
        // 设备不是自动模式，直接跳过
        boolean deviceIsAuto = deviceIsAuto(device);
        log.info("设备控制模式:{},设备控制名称:{}", deviceIsAuto == true ? "自动" : "手动",device.getDeviceName());
        log.info("");
        if (!deviceIsAuto) {
            return;
        }

        SysEnvThreshold threshold = thresholdMap.get(device.getEnvThresholdId());
        if (threshold == null) return;

        SensorData sensorData = sensorDataMap.get(threshold.getEnvParameterType());
        if (sensorData == null || sensorData.getData() == null) return;

        BigDecimal currentVal = sensorData.getData();
        BigDecimal min = threshold.getMinValue();
        BigDecimal max = threshold.getMaxValue();
        if (min == null || max == null) return;

        Integer typeCode = threshold.getEnvParameterType();

        // 判断当前控制设备是否需要启动或关闭
        int action = computeAction(typeCode, currentVal, min, max, device);


        // todo 去掉控制设备逻辑运行状态，改成直接根据当前阈值来直接控制设备，
        //  不需要拿之前逻辑状态来作为判断依据，解决了设备状态漂移（物理状态和缓存状态不一致问题）
        if (action == 1) {
            controlDevice(device.getDeviceCode(), ON.getCode(), device.getGreenhouseEnvCode());
            // 更新当前对象状态并加入列表
            updatedDevices.put(device.getDeviceCode(), ON.getCode());
            log.info("自动控制模式开启下:开启-设备{}, 当前值: {}",
                    device.getDeviceName(),   currentVal);
        } else if (action == -1) {
            controlDevice(device.getDeviceCode(), OFF.getCode(), device.getGreenhouseEnvCode());
            // 把当前发生变更的状态放进去
            updatedDevices.put(device.getDeviceCode(), OFF.getCode());
            log.info("自动控制模式开启下:关闭-设备{}, 当前值: {}",
                    device.getDeviceName(),   currentVal);
        }
    }

    private int computeAction(Integer typeCode,
                              BigDecimal currentVal,
                              BigDecimal min,
                              BigDecimal max,
                              SysControlDevice device) {
        if (LIGHT_INTENSITY.getEnvParameterType().equals(typeCode)) {
            String env = device.getGreenhouseEnvCode();
            String lightKey = AUTO_DEVICE_LIGHT_ON_UNTIL_KEY + env;
            String lowSinceKey = AUTO_DEVICE_LIGHT_LOW_SINCE_KEY + env;
            Long onUntil = (Long) redisTemplate.opsForHash().get(lightKey, device.getDeviceCode());
            Long lowSince = (Long) redisTemplate.opsForHash().get(lowSinceKey, device.getDeviceCode());
            long now = System.currentTimeMillis();
            if (currentVal.compareTo(min) < 0) {
                if (lowSince == null) {
                    redisTemplate.opsForHash().put(lowSinceKey, device.getDeviceCode(), now);
                    return 0;
                }
                if (now - lowSince < LIGHT_LOW_MIN_DURATION_MS) {
                    return 0;
                }
                if (onUntil == null || now >= onUntil) {
                    redisTemplate.opsForHash().put(lightKey, device.getDeviceCode(), now + LIGHT_ON_INTERVAL_MS);
                    return 1;
                }
                return 0;
            } else {
                if (lowSince != null) {
                    redisTemplate.opsForHash().delete(lowSinceKey, device.getDeviceCode());
                }
                if (onUntil != null && now < onUntil) {
                    return 0;
                }
                if (onUntil != null) {
                    redisTemplate.opsForHash().delete(lightKey, device.getDeviceCode());
                }
                return -1;
            }
        }

        if (CO2_CONCENTRATION.getEnvParameterType().equals(typeCode)) {
            if (currentVal.compareTo(max) > 0) return 1;
            else if (currentVal.compareTo(min) < 0) return -1;
            else return 0;
        }

        if (currentVal.compareTo(min) < 0) return 1;
        else if (currentVal.compareTo(max) > 0) return -1;
        else return 0;
    }



}
