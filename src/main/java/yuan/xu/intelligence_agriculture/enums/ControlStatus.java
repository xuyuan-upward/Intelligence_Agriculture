package yuan.xu.intelligence_agriculture.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
/**
 * 开关enum
 */
public enum ControlStatus {
    ON(1, "开"),
    OFF(0, "关");


    private final int code;
    private final String name;

    // getter
    public Integer getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}