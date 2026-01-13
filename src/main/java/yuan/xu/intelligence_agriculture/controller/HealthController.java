package yuan.xu.intelligence_agriculture.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author lzc
 * @date 2024.3.12
 * 概要： 服务类
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public boolean health() {
        return true;
    }

}
