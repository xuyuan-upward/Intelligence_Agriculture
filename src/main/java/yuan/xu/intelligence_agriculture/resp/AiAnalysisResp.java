package yuan.xu.intelligence_agriculture.resp;

import lombok.Data;
import java.util.List;

@Data
public class AiAnalysisResp {
    private List<PredictionPoint> chartData;
    private List<AiSuggestion> suggestions;
}
