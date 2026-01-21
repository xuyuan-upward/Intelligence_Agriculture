from flask import Flask, request, jsonify
import pandas as pd
import numpy as np
from sklearn.multioutput import MultiOutputRegressor
from sklearn.ensemble import RandomForestRegressor
import joblib
import os
from datetime import datetime, timedelta

# 初始化 Flask 应用
app = Flask(__name__)

# 模型保存路径
MODEL_PATH = 'agriculture_model.pkl'

# 需要预测的环境参数列表
PARAMS = ['airTemp', 'airHumidity', 'soilTemp', 'soilHumidity', 'co2Concentration', 'lightIntensity']

def train_dummy_model():
    """
    训练一个虚拟模型，以确保服务在没有历史数据的情况下也能开箱即用。
    """
    # 创建虚拟历史数据 (例如：100 个样本)
    # 输入: 6 个参数 (为简单起见，使用最新值作为输入特征)
    X = np.random.rand(100, len(PARAMS)) * 100 
    # 输出: 未来12小时 * 6个参数 = 72 个目标值
    y = np.random.rand(100, 12 * len(PARAMS)) * 100
    
    # 使用随机森林回归器进行多输出预测
    model = MultiOutputRegressor(RandomForestRegressor(n_estimators=10))
    model.fit(X, y)
    
    # 保存模型
    joblib.dump(model, MODEL_PATH)
    print("Dummy model trained and saved.")
    return model

def load_model():
    """
    加载已有模型，如果不存在则训练一个新模型
    """
    if os.path.exists(MODEL_PATH):
        return joblib.load(MODEL_PATH)
    else:
        return train_dummy_model()

# 全局加载模型
model_data = None

def create_features(df):
    """
    特征工程：将原始时间序列转换为监督学习所需的特征 (必须与训练时一致)
    """
    df = df.copy()
    if 'timestamp' in df.columns:
        dt = pd.to_datetime(df['timestamp'], unit='ms')
        df['hour'] = dt.dt.hour
        df['day_of_week'] = dt.dt.dayofweek
        df['hour_sin'] = np.sin(2 * np.pi * df['hour']/24.0)
        df['hour_cos'] = np.cos(2 * np.pi * df['hour']/24.0)
    
    for param in PARAMS:
        for lag in [1, 2, 3]:
            df[f'{param}_lag_{lag}'] = df[param].shift(lag)
        df[f'{param}_roll_mean_6'] = df[param].rolling(window=6).mean()
        df[f'{param}_roll_std_6'] = df[param].rolling(window=6).std()
    return df

def load_model():
    """
    加载已有模型，如果不存在则训练一个新模型
    """
    global model_data
    if os.path.exists(MODEL_PATH):
        model_data = joblib.load(MODEL_PATH)
        # 兼容旧版本模型格式
        if not isinstance(model_data, dict):
            model_data = {'model': model_data, 'feature_cols': PARAMS}
    else:
        # 训练并返回一个简单的模型数据
        model = train_dummy_model()
        model_data = {'model': model, 'feature_cols': PARAMS}

# 初始化加载
load_model()

@app.route('/predict', methods=['POST'])
def predict():
    """
    预测接口
    接收最近的环境数据，返回未来12小时的预测趋势
    """
    try:
        data = request.json
        if not data:
            return jsonify({"error": "No data provided"}), 400
            
        # 解析输入数据
        df = pd.DataFrame(data)
        if df.empty or len(df) < 1:
             return jsonify({"error": "Insufficient data"}), 400

        # 应用特征工程
        df_featured = create_features(df)
        
        # 获取最新的一行特征用于预测
        latest_features_df = df_featured.tail(1)
        
        # 提取模型需要的特征列 (处理缺失列，填充为0)
        X = []
        for col in model_data['feature_cols']:
            val = latest_features_df[col].iloc[0] if col in latest_features_df.columns else 0
            if pd.isna(val): val = 0
            X.append(float(val))
            
        X = np.array(X).reshape(1, -1)
        
        # 预测未来 12 小时
        prediction_flat = model_data['model'].predict(X)[0]
        prediction_reshaped = prediction_flat.reshape(12, len(PARAMS))
        
        # 构建响应数据
        results = []
        now = datetime.now()
        start_hour = now.replace(minute=0, second=0, microsecond=0) + timedelta(hours=1)
        
        for i in range(12):
            future_time = start_hour + timedelta(hours=i)
            pred_item = {
                "time": future_time.strftime("%H:00"), 
                "timestamp": int(future_time.timestamp() * 1000)
            }
            
            for j, param in enumerate(PARAMS):
                # 获取当前最新值作为基准
                current_val = float(df[param].iloc[-1]) if param in df.columns else 0
                
                # 预测值 (这里我们直接使用模型预测的绝对值，
                # 但为了演示平滑，如果模型是 Dummy 的，可以做些处理)
                val = prediction_reshaped[i][j]
                
                # 数值约束
                if 'Humidity' in param:
                    val = max(0, min(100, val))
                elif 'Concentration' in param or 'Intensity' in param:
                    val = max(0, val)
                    
                pred_item[param] = round(val, 2)
            
            results.append(pred_item)
            
        return jsonify(results)

    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
