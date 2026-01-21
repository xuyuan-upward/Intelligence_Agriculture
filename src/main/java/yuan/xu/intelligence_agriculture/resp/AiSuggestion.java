package yuan.xu.intelligence_agriculture.resp;

import lombok.Data;

@Data
public class AiSuggestion {
    private String time; // e.g. "未来 2 小时"
    private String title; // e.g. "高温预警"
    private String content; // e.g. "预测午间温度..."
    private String type; // warning, danger, info
    private String actionDevice; // optional
    private String deviceName; // optional
}
