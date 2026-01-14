package yuan.xu.intelligence_agriculture.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 配置类
 * 用于启用和配置 Spring Boot 对原生 WebSocket (@ServerEndpoint) 的支持
 */
@Configuration
public class WebSocketConfig {
    
    /**
     * 注入 ServerEndpointExporter
     * 该 Bean 会自动注册使用了 @ServerEndpoint 注解声明的 WebSocket endpoint
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
