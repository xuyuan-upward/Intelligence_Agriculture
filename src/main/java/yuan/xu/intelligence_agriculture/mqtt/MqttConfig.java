package yuan.xu.intelligence_agriculture.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import yuan.xu.intelligence_agriculture.model.SysGreenhouse;
import yuan.xu.intelligence_agriculture.service.SysGreenhouseService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * MQTT 配置类
 * 用于初始化 MQTT 客户端工厂、入站通道、出站通道及相关消息适配器
 */
@Configuration
public class MqttConfig {

    @Value("${mqtt.broker}")
    private String brokerUrl;

    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    /**
     * 传感器数据上行 Topic 前缀
     */
    @Value("${mqtt.data-topic}")
    private String dataTopic;

    /**
     * 控制指令下行 Topic 前缀
     */
    @Value("${mqtt.control-topic}")
    private String controlTopic;

    @Value("${mqtt.ca-file}")
    private String caFile;

    @Autowired
    private SysGreenhouseService sysGreenhouseService;

    /**
     * 配置 MQTT 客户端工厂
     */
    @Bean
    public MqttPahoClientFactory mqttClientFactory() throws Exception {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        
        options.setServerURIs(new String[]{brokerUrl});
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setAutomaticReconnect(true);
        options.setCleanSession(true); // 设置是否清空会话
        options.setKeepAliveInterval(600); // 设置心跳包发送间隔
        
        // 配置 SSL/TLS
        if (brokerUrl.startsWith("ssl://")) {
            options.setSocketFactory(getSocketFactory(caFile));
        }
        
        factory.setConnectionOptions(options);
        return factory;
    }

    /**
     * 加载 CA 证书并创建 SSLSocketFactory
     */
    private SSLSocketFactory getSocketFactory(String caFile) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        ClassPathResource resource = new ClassPathResource(caFile);
        
        try (InputStream caInput = resource.getInputStream()) {
            X509Certificate ca = (X509Certificate) cf.generateCertificate(caInput);
            
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);
            
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, tmf.getTrustManagers(), null);
            
            return context.getSocketFactory();
        }
    }

    /**
     * 入站消息通道：用于接收从 MQTT 代理发送过来的消息
     */
    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    /**
     * MQTT 入站消息适配器：订阅所有环境的传感器数据主题并转发到入站通道
     */
    @Bean
    public MessageProducer inbound() throws Exception {
        // 从数据库获取所有环境信息，动态构建订阅主题
        List<SysGreenhouse> greenhouses = sysGreenhouseService.list();
        List<String> topics = new ArrayList<>();

        if (greenhouses != null && !greenhouses.isEmpty()) {
            for (SysGreenhouse greenhouse : greenhouses) {
                // 拼接主题: dataTopic/{envCode}
                if (greenhouse.getEnvCode() != null && !greenhouse.getEnvCode().isEmpty()) {
                    topics.add(dataTopic + "/" + greenhouse.getEnvCode());
                }
            }
        }

        // 如果没有环境信息，或者为了容错，可以添加一个默认主题
        if (topics.isEmpty()) {
            topics.add(dataTopic);
        }

        // 将 List 转换为数组
        String[] topicArray = topics.toArray(new String[0]);

        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        clientId + "_inbound_" + System.currentTimeMillis(),
                        mqttClientFactory(),
                        topicArray
                );

        DefaultPahoMessageConverter converter = new DefaultPahoMessageConverter();
        converter.setPayloadAsBytes(false); // ⭐关键

        adapter.setConverter(converter);
        adapter.setQos(0);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    /**
     * 出站消息通道：用于向 MQTT 代理发送消息
     */
    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    /**
     * MQTT 出站消息处理器：监听出站通道并将消息推送到 MQTT 代理
     */
    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound() throws Exception {
        MqttPahoMessageHandler messageHandler =
                new MqttPahoMessageHandler(clientId + "_outbound_" + System.currentTimeMillis(), mqttClientFactory());
        messageHandler.setAsync(true); // 设置异步发送
        messageHandler.setDefaultTopic(controlTopic); // 默认控制指令主题前缀
        return messageHandler;
    }
}
