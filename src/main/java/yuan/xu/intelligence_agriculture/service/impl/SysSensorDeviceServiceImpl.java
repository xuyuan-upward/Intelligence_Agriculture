package yuan.xu.intelligence_agriculture.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import yuan.xu.intelligence_agriculture.mapper.SysSensorDeviceMapper;
import yuan.xu.intelligence_agriculture.model.SysSensorDevice;
import yuan.xu.intelligence_agriculture.service.SysSensorDeviceService;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static yuan.xu.intelligence_agriculture.service.impl.IotDataServiceImpl.DEVICE_LAST_ACTIVE_KEY;

/**
 * 采集设备管理业务实现类
 */
@Service
@Slf4j
public class SysSensorDeviceServiceImpl extends ServiceImpl<SysSensorDeviceMapper, SysSensorDevice> implements SysSensorDeviceService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String ALL_SENSOR_DEVICES_KEY = "iot:all_sensor_devices";

    @PostConstruct
    public void init() {
        refreshAllDeviceCache();
    }

    private void refreshAllDeviceCache() {
        List<SysSensorDevice> list = this.list();
        redisTemplate.delete(ALL_SENSOR_DEVICES_KEY);
        if (!list.isEmpty()) {
            redisTemplate.opsForValue().set(ALL_SENSOR_DEVICES_KEY, list, 24, TimeUnit.HOURS);
        }
        log.info("Redis 全量采集设备缓存已刷新，当前设备总数: {}", list.size());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<SysSensorDevice> listAllDevicesFromCache() {
        List<SysSensorDevice> list = (List<SysSensorDevice>) redisTemplate.opsForValue().get(ALL_SENSOR_DEVICES_KEY);
        if (list == null || list.isEmpty()) {
            refreshAllDeviceCache();
            list = this.list();
        }

        if (list != null) {
            long currentTime = System.currentTimeMillis();
            for (SysSensorDevice device : list) {
                String key = DEVICE_LAST_ACTIVE_KEY + device.getDeviceCode();
                Object lastActive = redisTemplate.opsForValue().get(key);
                if (lastActive != null && (currentTime - Long.parseLong(lastActive.toString()) < 6000)) {
                    device.setOnlineStatus(1);
                } else {
                    device.setOnlineStatus(0);
                }
            }
        }
        return list;
    }
}
