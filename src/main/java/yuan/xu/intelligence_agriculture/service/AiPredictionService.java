package yuan.xu.intelligence_agriculture.service;

import yuan.xu.intelligence_agriculture.resp.AiAnalysisResp;

public interface AiPredictionService {
    AiAnalysisResp getPrediction(String envCode);
}
