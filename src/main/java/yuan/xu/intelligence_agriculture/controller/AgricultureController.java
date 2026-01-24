package yuan.xu.intelligence_agriculture.controller;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import yuan.xu.intelligence_agriculture.dto.CommonResult;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.model.SysControlDevice;
import yuan.xu.intelligence_agriculture.model.SysEnvThreshold;
import yuan.xu.intelligence_agriculture.model.SysGreenhouse;
import yuan.xu.intelligence_agriculture.req.*;
import yuan.xu.intelligence_agriculture.resp.AiAnalysisResp;
import yuan.xu.intelligence_agriculture.resp.EnvThresholdResp;
import yuan.xu.intelligence_agriculture.resp.IotSensorHistoryDataResp;
import yuan.xu.intelligence_agriculture.service.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static yuan.xu.intelligence_agriculture.key.RedisKey.AUTO_DEVICE_STATUS_KEY;

/**
 * 智能农业对外 API 控制器
 * 提供传感器数据查询、设备控制、状态监控及 AI 预测等接口
 */
@RestController
@RequestMapping("/agriculture/device")
@CrossOrigin(origins = "*")
public class AgricultureController {

    @Autowired
    private IotDataService iotDataService;

    @Autowired
    private SysControlDeviceService sysControlDeviceService;


    @Autowired
    private AiPredictionService aiPredictionService;

    @Autowired
    private SysGreenhouseService sysGreenhouseService;
    @Autowired
    private SysEnvThresholdService sysEnvThresholdService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取历史传感器数据列表
     * 返回近一小时的记录，用于趋势图表展示
     */
    @GetMapping("/query/data/history")
    public CommonResult<List<IotSensorHistoryDataResp>> getHistoryData(@RequestParam String envCode) {
        return CommonResult.success(iotDataService.getHistoryData(envCode));
    }

    /**
     * 获取历史工况 analysis 数据
     * 限制：
     * 1. 只能查询最近 6 小时内的数据
     * 2. 查询时间范围不得超过 6 小时
     */
    @PostMapping("/query/data/analysis")
    public CommonResult<List<IotSensorHistoryDataResp>> getAnalysisData(@RequestBody AnalysisReq req) {
        return iotDataService.getAnalysisData(req);
    }

    /**
     * 手动控制设备开关
     * @param req 设备控制请求对象
     */
    @PostMapping("/update/device/control")
    public CommonResult<String> controlDevice(@RequestBody DeviceControlReq req) {
        sysControlDeviceService.controlDevice(req.getDeviceCode(), req.getStatus(),req.getEnvCode(),req.getDeviceName());
        return CommonResult.success("操作成功");
    }

    /**
     * 获取某个环境下的所有控制设备
     * @param envCode
     * @return
     */
    @GetMapping("/query/device/list")
    public CommonResult<List<SysControlDevice>> listControlDevices(@RequestParam String envCode) {
        return CommonResult.success(sysControlDeviceService.listControlDevices(envCode));
    }

    /**
     * 更新当前环境"所有"控制设备的控制模式(手动/自动模式的切换)
     * @param reqs 设备模式更新请求对象
     */
    @PostMapping("/update/devices/mode")
    public CommonResult<String> updateDevicesMode(@RequestBody DeviceModeReqs reqs) {
        sysControlDeviceService.updatesDevicesMode(reqs);
        return CommonResult.success("Mode updated");
    }

//     /**
//     * 更新当前环境"单个"控制设备的控制模式 todo(目前不会使用,后续再进行扩展)
//     * @param req 设备模式更新请求对象
//     */
//    @PostMapping("/device/mode")
//    public CommonResult<String> updateSingleMode(@RequestBody DeviceModeReq req) {
//        sysControlDeviceService.updateSingleMode(req);
//        return CommonResult.success("Mode updated");
//    }

    /**
     * 更新某个环境阈值
     * @param req 环境阈值更新
     */
    @PostMapping("/update/env/envthreshold")
    public CommonResult<String> updateEnvThreshold(@RequestBody EnvThresholdListReq req) {
        sysEnvThresholdService.updateEnvThreshold(req);
        return CommonResult.success("EnvThreshold updated");
    }

    /**
     * 获取某个环境阈值
     * @param envCode 获取某个环境阈值
     */
    @GetMapping("/query/env/envthreshold")
    public CommonResult<List<EnvThresholdResp>> queryEnvThreshold(String envCode) {
        return CommonResult.success(sysEnvThresholdService.queryEnvThreshold(envCode));
    }

    /**
     * 获取当前默认环境（通常是第一个启用的环境）
     */
    @GetMapping("/query/env/current")
    public CommonResult<SysGreenhouse> getCurrentEnv() {
        List<SysGreenhouse> list = sysGreenhouseService.lambdaQuery()
                .eq(SysGreenhouse::getStatus, 1)
                .orderByAsc(SysGreenhouse::getId)
                .last("LIMIT 1")
                .list();
        if (list != null && !list.isEmpty()) {
            return CommonResult.success(list.get(0));
        }
        return CommonResult.success(null);
    }

    /**
     * 获取全部环境实例,展示后续传参
     *
     */
    @GetMapping("/query/env/list")
    public CommonResult<List<SysGreenhouse>> listEnv() {
        List<SysGreenhouse> list = sysGreenhouseService.lambdaQuery()
                .eq(SysGreenhouse::getStatus, 1)
                .orderByAsc(SysGreenhouse::getId)
                .list();
        return CommonResult.success(list);
    }

    /**
     * 支持模糊查询某个环境实例,展示后续传参
     *
     */
    @GetMapping("/query/env/search")
    public CommonResult<List<SysGreenhouse>> searchEnv(@RequestParam(required = false) String keyword) {
        List<SysGreenhouse> list = sysGreenhouseService.lambdaQuery()
                .eq(SysGreenhouse::getStatus, 1)
                .and(keyword != null && !keyword.trim().isEmpty(),
                     wrapper -> wrapper.like(SysGreenhouse::getEnvName, keyword)
                                     .or()
                                     .like(SysGreenhouse::getEnvCode, keyword))
                .orderByAsc(SysGreenhouse::getId)
                .list();
        return CommonResult.success(list);
    }


    /**
     * 获取 AI 农作物生长建议或产量预测
     * 调用内部 AI 服务根据历史环境数据生成建议
     */
    @GetMapping("/query/prediction")
    public CommonResult<AiAnalysisResp> getPrediction(@RequestParam String envCode) {
        return CommonResult.success(aiPredictionService.getPrediction(envCode));
    }
}
