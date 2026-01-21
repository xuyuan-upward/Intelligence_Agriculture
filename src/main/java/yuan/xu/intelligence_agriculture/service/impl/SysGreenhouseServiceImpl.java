package yuan.xu.intelligence_agriculture.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import yuan.xu.intelligence_agriculture.mapper.SysGreenhouseMapper;
import yuan.xu.intelligence_agriculture.model.SysGreenhouse;
import yuan.xu.intelligence_agriculture.service.SysEnvThresholdService;
import yuan.xu.intelligence_agriculture.service.SysGreenhouseService;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static yuan.xu.intelligence_agriculture.key.RedisKey.ALL_ENV_HOUSE;

@Service
@Slf4j
public class SysGreenhouseServiceImpl extends ServiceImpl<SysGreenhouseMapper, SysGreenhouse> implements SysGreenhouseService {
    @Autowired
    private SysEnvThresholdService sysEnvThresholdService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @PostConstruct
    public void init() {
        refreshEnvHouseCache();
    }

    // 环境实例预缓存
    public void refreshEnvHouseCache() {
        List<SysGreenhouse> list = list();
        redisTemplate.delete(ALL_ENV_HOUSE);
        if (!list.isEmpty()) {
            redisTemplate.opsForValue().set(ALL_ENV_HOUSE, list, 48, TimeUnit.HOURS);
        }
        log.info("Redis 环境实例缓存已刷新，当前环境实例数量: {}", list.size());
    }

}
