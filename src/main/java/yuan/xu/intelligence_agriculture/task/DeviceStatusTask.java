package yuan.xu.intelligence_agriculture.task;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import yuan.xu.intelligence_agriculture.model.SysControlDevice;
import yuan.xu.intelligence_agriculture.model.SysSensorDevice;
import yuan.xu.intelligence_agriculture.service.SysControlDeviceService;
import yuan.xu.intelligence_agriculture.service.SysSensorDeviceService;
import yuan.xu.intelligence_agriculture.websocket.WebSocketServer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备状态检查定时任务
 * 每 2 秒运行一次，检查 Redis 中的活跃标识，判定设备是否在线
 */
@Component
@Slf4j
public class DeviceStatusTask {

    @Autowired
    private SysControlDeviceService sysControlDeviceService;

    @Autowired
    private SysSensorDeviceService sysSensorDeviceService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 上一次的状态快照，用于对比是否发生变化
     */
    private Map<String, Integer> lastStatusMap = new HashMap<>();

    /**
     * 每 2 秒执行一次状态检查
     * 检查对应的采集设备的在线状态,发生变更推送给前端
     */
    @Scheduled(fixedRate = 2000)
    public void checkDeviceStatus() {
        // 1. 获取所有设备列表（从缓存获取，避免数据库压力）
        List<SysControlDevice> controlDevices = sysControlDeviceService.listAllDevicesFromCache();
        List<SysSensorDevice> sensorDevices = sysSensorDeviceService.listAllDevicesFromCache();
        
        Map<String, Integer> currentStatusMap = new HashMap<>();

        long currentTime = System.currentTimeMillis();

        // 处理控制设备
        for (SysControlDevice device : controlDevices) {
            String deviceCode = device.getDeviceCode();
            String key = "iot:device:active:" + deviceCode;
            Object lastActive = redisTemplate.opsForValue().get(key);

            int status = 0;
            if (lastActive != null) {
                long lastTime = Long.parseLong(lastActive.toString());
                if (currentTime - lastTime < 6000) {
                    status = 1;
                }
            }
            currentStatusMap.put(deviceCode, status);
        }

        // 处理采集设备
        for (SysSensorDevice device : sensorDevices) {
            String deviceCode = device.getDeviceCode();
            String key = "iot:device:active:" + deviceCode;
            Object lastActive = redisTemplate.opsForValue().get(key);

            int status = 0;
            if (lastActive != null) {
                long lastTime = Long.parseLong(lastActive.toString());
                if (currentTime - lastTime < 6000) {
                    status = 1;
                }
            }
            currentStatusMap.put(deviceCode, status);
        }

        // 4. 如果状态发生了变化，或者这是第一次运行，则通过 WebSocket 推送给前端
        if (!currentStatusMap.equals(lastStatusMap)) {
            lastStatusMap = new HashMap<>(currentStatusMap);
            
            Map<String, Object> message = new HashMap<>();
            message.put("type", "DEVICE_STATUS");
            message.put("data", currentStatusMap);
            
            WebSocketServer.sendInfo(JSONUtil.toJsonStr(message));
            log.info("设备在线状态发生变化，已推送至前端: {}", currentStatusMap);
        }
    }
}
