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
     * 检查采集和控制设备的在线状态，发生变更推送给前端
     */
    @Scheduled(fixedRate = 2000)
    public void checkDeviceStatus() {
        long currentTime = System.currentTimeMillis();
        
        // 1. 处理采集设备在线状态
        List<SysSensorDevice> sensorDevices = sysSensorDeviceService.listAllDevicesFromCache();
        for (SysSensorDevice device : sensorDevices) {
            String deviceCode = device.getDeviceCode();
            int currentStatus = isDeviceOnline(deviceCode, currentTime);
            
            if (!Integer.valueOf(currentStatus).equals(lastStatusMap.get(deviceCode))) {
                lastStatusMap.put(deviceCode, currentStatus);
                sendDeviceStatusUpdate("SENSOR_DEVICE_STATUS", deviceCode, currentStatus);
            }
        }
    }

    private int isDeviceOnline(String deviceCode, long currentTime) {
        String key = "iot:device:active:" + deviceCode;
        Object lastActive = redisTemplate.opsForValue().get(key);
        if (lastActive != null) {
            long lastTime = Long.parseLong(lastActive.toString());
            if (currentTime - lastTime < 10000) { // 10秒内有心跳则在线
                return 1;
            }
        }
        return 0;
    }

    private void sendDeviceStatusUpdate(String type, String deviceCode, int onlineStatus) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        Map<String, Object> data = new HashMap<>();
        data.put("deviceCode", deviceCode);
        data.put("onlineStatus", onlineStatus);
        message.put("data", data);
        
        WebSocketServer.sendInfo(JSONUtil.toJsonStr(message));
        log.info("设备[{}]在线状态变化: {}, 已推送至前端", deviceCode, onlineStatus == 1 ? "在线" : "离线");
    }
}
