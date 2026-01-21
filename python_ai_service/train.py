import pandas as pd
import numpy as np
from sklearn.multioutput import MultiOutputRegressor
from sklearn.ensemble import RandomForestRegressor
from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_absolute_error, mean_squared_error
import joblib
import os
from datetime import datetime

# 需要预测的环境参数列表
PARAMS = ['airTemp', 'airHumidity', 'soilTemp', 'soilHumidity', 'co2Concentration', 'lightIntensity']
MODEL_PATH = 'agriculture_model.pkl'

def create_features(df):
    """
    特征工程：将原始时间序列转换为监督学习所需的特征
    """
    df = df.copy()
    
    # 1. 时间特征 (捕捉周期性)
    if 'timestamp' in df.columns:
        # 如果是毫秒级时间戳，先转为 datetime
        dt = pd.to_datetime(df['timestamp'], unit='ms')
        df['hour'] = dt.dt.hour
        df['day_of_week'] = dt.dt.dayofweek
        
        # 使用正弦/余弦编码小时特征，使其具有周期性 (23点接近0点)
        df['hour_sin'] = np.sin(2 * np.pi * df['hour']/24.0)
        df['hour_cos'] = np.cos(2 * np.pi * df['hour']/24.0)
    
    # 2. 滞后特征 (Lag Features): 使用过去 1, 2, 3 小时的值
    for param in PARAMS:
        for lag in [1, 2, 3]:
            df[f'{param}_lag_{lag}'] = df[param].shift(lag)
            
    # 3. 滚动统计特征 (Rolling Features): 过去 6 小时的均值和标准差
    for param in PARAMS:
        df[f'{param}_roll_mean_6'] = df[param].rolling(window=6).mean()
        df[f'{param}_roll_std_6'] = df[param].rolling(window=6).std()

    return df

def prepare_targets(df, horizon=12):
    """
    准备目标变量：预测未来 horizon 小时的数据
    """
    targets = []
    for i in range(1, horizon + 1):
        for param in PARAMS:
            col_name = f'{param}_future_{i}'
            df[col_name] = df[param].shift(-i)
            targets.append(col_name)
    return df, targets

def train_professional_model(csv_path):
    """
    从 CSV 文件训练专业模型
    """
    if not os.path.exists(csv_path):
        print(f"Error: {csv_path} not found.")
        return
        
    # 加载数据
    print("Loading data...")
    df = pd.read_csv(csv_path)
    
    # 确保数据按时间排序
    if 'timestamp' in df.columns:
        df = df.sort_values('timestamp')
    
    # 特征工程
    print("Engineering features...")
    df = create_features(df)
    
    # 准备目标变量 (未来12小时)
    df, target_cols = prepare_targets(df, horizon=12)
    
    # 删除含有 NaN 的行 (由于 shift 产生)
    df = df.dropna()
    
    # 提取特征列 (除了目标列和原始时间戳以外的所有列)
    feature_cols = [c for c in df.columns if c not in target_cols and c != 'timestamp']
    
    X = df[feature_cols]
    y = df[target_cols]
    
    # 划分训练集和测试集 (时间序列建议不打乱顺序)
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, shuffle=False)
    
    print(f"Training model with {len(X_train)} samples...")
    # 使用更多的树和更深的深度来提高精度
    base_model = RandomForestRegressor(
        n_estimators=100, 
        max_depth=15, 
        min_samples_split=5,
        random_state=42,
        n_jobs=-1 # 使用所有 CPU 核心
    )
    
    model = MultiOutputRegressor(base_model)
    model.fit(X_train, y_train)
    
    # 评估
    predictions = model.predict(X_test)
    mae = mean_absolute_error(y_test, predictions)
    mse = mean_squared_error(y_test, predictions)
    
    print(f"Model Training Complete.")
    print(f"Mean Absolute Error: {mae:.4f}")
    print(f"Mean Squared Error: {mse:.4f}")
    
    # 保存模型和特征列表 (预测时需要用到相同的特征列表)
    model_data = {
        'model': model,
        'feature_cols': feature_cols,
        'params': PARAMS
    }
    joblib.dump(model_data, MODEL_PATH)
    print(f"Model saved to {MODEL_PATH}")

def generate_sample_data(filename='historical_data.csv'):
    """
    生成一些模拟的农业历史数据用于演示训练
    """
    print("Generating sample data...")
    rows = 2000
    now = datetime.now()
    data = []
    
    for i in range(rows):
        ts = int((now - pd.Timedelta(hours=rows-i)).timestamp() * 1000)
        hour = (now - pd.Timedelta(hours=rows-i)).hour
        
        # 模拟真实感的数据波动
        temp = 20 + 10 * np.sin(2 * np.pi * hour / 24) + np.random.normal(0, 1)
        humi = 60 - 20 * np.sin(2 * np.pi * hour / 24) + np.random.normal(0, 5)
        soil_temp = 18 + 5 * np.sin(2 * np.pi * (hour-2) / 24) + np.random.normal(0, 0.5)
        soil_humi = 40 + np.random.normal(0, 2)
        co2 = 400 + 100 * np.random.rand()
        light = max(0, 10000 * np.sin(2 * np.pi * (hour-6) / 12)) if 6 <= hour <= 18 else 0
        
        data.append([ts, temp, humi, soil_temp, soil_humi, co2, light])
        
    df = pd.DataFrame(data, columns=['timestamp'] + PARAMS)
    df.to_csv(filename, index=False)
    print(f"Sample data saved to {filename}")

if __name__ == '__main__':
    # 1. 如果没有数据，先生成模拟数据
    if not os.path.exists('historical_data.csv'):
        generate_sample_data()
        
    # 2. 训练模型
    train_professional_model('historical_data.csv')
