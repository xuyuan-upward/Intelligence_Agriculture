package yuan.xu.intelligence_agriculture.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
/**
 * 环境参数类型
 */
public enum EnvParameterType {
    AIR_TEMP(1, "空气温度"),
    AIR_HUMIDITY(2, "空气湿度"),
    SOIL_TEMP(3, "土壤温度"),
    SOIL_HUMIDITY(4, "土壤湿度"),
    CO2_CONCENTRATION(5, "CO2浓度"),
    LIGHT_INTENSITY(6, "光照强度");

    private final int envParameterType;
    private final String envParameterName;

    // getter
    public Integer getEnvParameterType() {
        return envParameterType;
    }

    public String getEnvParameterName() {
        return envParameterName;
    }
}