package yuan.xu.intelligence_agriculture.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yuan.xu.intelligence_agriculture.mapper.SysEnvThresholdMapper;
import yuan.xu.intelligence_agriculture.model.SysEnvThreshold;
import yuan.xu.intelligence_agriculture.req.EnvThresholdListReq;
import yuan.xu.intelligence_agriculture.req.EnvThresholdReq;
import yuan.xu.intelligence_agriculture.resp.EnvThresholdResp;
import yuan.xu.intelligence_agriculture.service.SysEnvThresholdService;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static yuan.xu.intelligence_agriculture.key.RedisKey.ENV_THRESHOLD_KEY;

@Service
@Slf4j
public class SysEnvThresholdServiceImpl extends ServiceImpl<SysEnvThresholdMapper, SysEnvThreshold> implements SysEnvThresholdService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private SysEnvThresholdMapper sysEnvThresholdMapper;


    @PostConstruct
    public void init() {

        refreshEnvThresholdCache();
    }

    // 环境阈值预缓存
    private void refreshEnvThresholdCache() {
        List<SysEnvThreshold> sysEnvThresholdList = list();
        ///  根据对应环境实例区分不同的环境阈值,方便后续的存放 key:环境实例编码 value:对应实例编码下的环境阈值
        ///  用map来存储对应不同环境下的环境阈值,key根据不同环境来区分,filed:根据环境类型来区分,value:对应的整个环境阈值对象
        Map<String, List<SysEnvThreshold>> EnvThresholdMaps = sysEnvThresholdList.stream().collect(Collectors.groupingBy(SysEnvThreshold::getGreenhouseEnvCode));
        if (!sysEnvThresholdList.isEmpty()) {
            // 单独对某个对应的环境阈值进行修改
            EnvThresholdMaps.forEach(
                    (envCode, list) -> {
                        list.forEach(threshold -> {
                            redisTemplate.opsForHash().put(ENV_THRESHOLD_KEY + envCode, threshold.getId().toString(), threshold);
                        });
                        redisTemplate.expire(ENV_THRESHOLD_KEY + envCode, 24, TimeUnit.HOURS);
                    }
            );
        }
        log.info("Redis 环境阈值预缓存，当前环境阈值数量: {}", sysEnvThresholdList.size());
    }
    /// 单个环境阈值修改
    @Override
    public void updateSingleEnvThreshold(EnvThresholdReq req) {

    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateEnvThreshold(EnvThresholdListReq req) {
        if (req == null || req.getEnvCode() == null || req.getEnvThresholdList() == null || req.getEnvThresholdList().isEmpty()) {
            return;
        }
        // todo: 批量更新(普通数量不大情况) =>  使用 MyBatis-Plus 的 updateBatchById（推荐）
        // 步骤1：先从数据库查询出现有的记录（获取ID）
//        List<SysEnvThreshold> existingList = sysEnvThresholdMapper.selectList(
//                new LambdaQueryWrapper<SysEnvThreshold>()
//                        .eq(SysEnvThreshold::getGreenhouseEnvCode, req.getEnvCode())
//                        .in(SysEnvThreshold::getEnvParameterType,
//                                req.getEnvThresholdList().stream()
//                                        .map(EnvThresholdReq::getEnvParameterType)
//                                        .collect(Collectors.toList()))
//        );
//        Map<Integer, EnvThresholdReq> envThresholdReqMap = req.getEnvThresholdList().stream().collect(Collectors.toMap(EnvThresholdReq::getEnvParameterType, item ->
//                item
//        ));
//        List<SysEnvThreshold> updateList = new ArrayList<>();
//        for (SysEnvThreshold sysEnvThreshold : existingList) {
////                EnvThresholdReq envThresholdReq = envThresholdReqMap.get(sysEnvThreshold.getEnvParameterType());
//
//        }
//
//
//        List<SysEnvThreshold> list = list(lambdaQuery().eq(SysEnvThreshold::getGreenhouseEnvCode, req.getEnvCode()));
        // todo 进阶版本:直接调用，只执行一次SQL
        sysEnvThresholdMapper.batchUpdateEnvThreshold(req.getEnvCode(), req.getEnvThresholdList());
        // 5. 刷新某个环境下的全部环境阈值 Redis 缓存
        refreshThresholdCache(req);
    }

    @Override
    public List<EnvThresholdResp> queryEnvThreshold(String envCode) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(ENV_THRESHOLD_KEY + envCode);
        // 先建立一个null的list,如果entries为空,则返回一个空的list,避免直接返回null
        List<EnvThresholdResp>  envThresholdRespList = new ArrayList<>();
        if (!entries.isEmpty()) {
            envThresholdRespList = entries.values().stream().map(item -> (EnvThresholdResp) item).collect(Collectors.toList());
        }
        return envThresholdRespList;
    }

    private void refreshThresholdCache(EnvThresholdListReq req) {
        // 步骤1：先从数据库查询出现有的记录（获取ID）
        List<SysEnvThreshold> existingList = sysEnvThresholdMapper.selectList(
                new LambdaQueryWrapper<SysEnvThreshold>()
                        .eq(SysEnvThreshold::getGreenhouseEnvCode, req.getEnvCode())
                        .in(SysEnvThreshold::getEnvParameterType,
                                req.getEnvThresholdList().stream()
                                        .map(EnvThresholdReq::getEnvParameterType)
                                        .collect(Collectors.toList()))
        );
        Map<String, SysEnvThreshold> IdAndEnvThreshold = existingList.stream().collect(Collectors.toMap( (val)-> val.getId().toString(), threshold -> threshold));
        redisTemplate.delete(ENV_THRESHOLD_KEY + req.getEnvCode());
        redisTemplate.opsForHash().putAll(ENV_THRESHOLD_KEY + req.getEnvCode(), IdAndEnvThreshold);
        redisTemplate.expire(ENV_THRESHOLD_KEY + req.getEnvCode(), 24, TimeUnit.HOURS);
        log.info("Redis 环境阈值缓存已刷新，当前总数: {}", existingList.size());
    }


    /*// 环境阈值缓存修改
    public void updateSingleEnvThresholdCache(EnvThresholdDTO envThresholdDTO) {
        redisTemplate.opsForValue().set(ALL_ENV_THRESHOLD_KEY + envThresholdDTO.getEnvCode() + ":" + envThresholdDTO.getEnvParameterType(), envThresholdDTO, 24, TimeUnit.HOURS);

        List<SysEnvThreshold> sysEnvThresholdList = list();
        ///  根据对应环境实例区分不同的环境阈值,方便后续的存放 key:环境实例编码 value:对应实例编码下的环境阈值
        Map<String, List<SysEnvThreshold>> EnvThresholdMaps = sysEnvThresholdList.stream().collect(Collectors.groupingBy(SysEnvThreshold::getGreenhouseEnvCode));
        if (!sysEnvThresholdList.isEmpty()) {
            // 单独对某个对应的环境阈值进行修改
            EnvThresholdMaps.forEach(
                    (envCode, list) -> {
                        list.forEach(threshold -> {
                        });
                    }
            );
        }
        log.info("Redis 环境阈值缓存已修改");
    }


    @Override
    public void updateSingleEnvThreshold(EnvThresholdReq req) {
        if (req == null || req.getEnvCode() == null || req.getEnvParameterType() == null) {
            return;
        }

        SysEnvThreshold threshold = lambdaQuery()
                .eq(SysEnvThreshold::getGreenhouseEnvCode, req.getEnvCode())
                .eq(SysEnvThreshold::getEnvParameterType, req.getEnvParameterType())
                .one();

        if (threshold == null) {
            threshold = new SysEnvThreshold();
            threshold.setGreenhouseEnvCode(req.getEnvCode());
            threshold.setEnvParameterType(req.getEnvParameterType());
        }

        if (req.getMin() != null) {
            threshold.setMinValue(new BigDecimal(req.getMin()));
        }
        if (req.getMax() != null) {
            threshold.setMaxValue(new BigDecimal(req.getMax()));
        }

        if (threshold.getId() == null) {
            save(threshold);
        } else {
            updateById(threshold);
        }

        List<SysEnvThreshold> list = list();
        redisTemplate.opsForValue().set(ALL_ENV_THRESHOLD_KEY, list, 24, TimeUnit.HOURS);
    }*/
}
