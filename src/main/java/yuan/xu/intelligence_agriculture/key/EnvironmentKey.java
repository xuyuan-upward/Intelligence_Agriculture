package yuan.xu.intelligence_agriculture.key;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EnvironmentKey {
   @Value("${mqtt.control-topic}")
    public  String controlTopic;

}
