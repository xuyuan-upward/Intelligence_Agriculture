package yuan.xu.intelligence_agriculture;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("yuan.xu.intelligence_agriculture.mapper")
public class IntelligenceAgricultureApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntelligenceAgricultureApplication.class, args);
    }

}
