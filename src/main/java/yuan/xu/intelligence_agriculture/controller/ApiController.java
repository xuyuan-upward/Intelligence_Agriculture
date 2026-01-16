package yuan.xu.intelligence_agriculture.controller;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import yuan.xu.intelligence_agriculture.dto.CommonResult;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.model.SysGreenhouse;
import yuan.xu.intelligence_agriculture.req.*;
import yuan.xu.intelligence_agriculture.resp.EnvThresholdResp;
import yuan.xu.intelligence_agriculture.service.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能农业对外 API 控制器
 * 提供传感器数据查询、设备控制、状态监控及 AI 预测等接口
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ApiController {

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

    /**
     * 获取历史传感器数据列表
     * 默认返回最近的 100 条记录，用于趋势图表展示
     */
    @GetMapping("/data/history")
    public CommonResult<List<IotSensorDataResp>> getHistoryData(String envCode) {
        List<IotSensorData> list = iotDataService.lambdaQuery()
                .orderByDesc(IotSensorData::getCreateTime)
                .last("LIMIT 100")
                .list();
        List<IotSensorDataResp> iotSensorDataRespList = new ArrayList<>();
        BeanUtils.copyProperties(list,iotSensorDataRespList );
        return CommonResult.success(iotSensorDataRespList);
    }

    /**
     * 手动控制设备开关
     * @param req 设备控制请求对象
     */
    @PostMapping("/device/control")
    public CommonResult<String> controlDevice(@RequestBody DeviceControlReq req) {
        sysControlDeviceService.controlDevice(req.getDeviceCode(), req.getStatus(),req.getEnvCode());
        return CommonResult.success("操作成功");
    }

    /**
     * 更新当前环境"所有"控制设备的控制模式(手动/自动模式的切换)
     * @param reqs 设备模式更新请求对象
     */
    @PostMapping("/devices/mode")
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
    @PostMapping("/env/envthreshoid")
    public CommonResult<String> updateEnvThreshold(@RequestBody EnvThresholdListReq req) {
        sysEnvThresholdService.updateEnvThreshold(req);
        return CommonResult.success("EnvThreshold updated");
    }

    /**
     * 获取某个环境阈值
     * @param envCode 环境阈值更新
     */
    @GetMapping("/env/envthreshoid")
    public CommonResult<List<EnvThresholdResp>> queryEnvThreshold(String envCode) {
        return CommonResult.success(sysEnvThresholdService.queryEnvThreshold(envCode));
    }

    /**
     * 获取全部环境实例,展示后续传参
     *
     */
    @GetMapping("/env/list")
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
    @GetMapping("/env/search")
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
    @GetMapping("/prediction")
    public CommonResult<String> getPrediction() {
        return CommonResult.success(aiPredictionService.getPrediction());
    }
}
