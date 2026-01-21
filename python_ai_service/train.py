import pandas as pd
import numpy as np
from sklearn.multioutput import MultiOutputRegressor
from sklearn.ensemble import RandomForestRegressor
from sklearn.model_selection import train_test_split, TimeSeriesSplit, GridSearchCV
from sklearn.metrics import mean_absolute_error, mean_squared_error
import joblib
import os
from datetime import datetime

# 需要预测的环境参数列表
PARAMS = ['airTemp', 'airHumidity', 'soilTemp', 'soilHumidity', 'co2Concentration', 'lightIntensity']
MODEL_PATH = 'agriculture_model.pkl'
LAG_HOURS = [1, 2, 3, 6, 12, 24]
ROLL_WINDOWS = [6, 12, 24]

COLUMN_ALIASES = {
    'timestamp': ['timestamp', 'time', 'datetime', 'date_time', 'date', '采集时间', '时间', '日期时间', '日期'],
    'airTemp': ['airTemp', 'air_temperature', 'temperature', 'temp', '气温', '空气温度', '温度'],
    'airHumidity': ['airHumidity', 'air_humidity', 'humidity', 'rh', '相对湿度', '空气湿度', '湿度'],
    'soilTemp': ['soilTemp', 'soil_temperature', 'soil_temp', '土壤温度', '地温'],
    'soilHumidity': ['soilHumidity', 'soil_moisture', 'soil_humidity', '土壤湿度', '土壤含水量'],
    'co2Concentration': ['co2Concentration', 'co2', 'CO2', '二氧化碳浓度', '二氧化碳'],
    'lightIntensity': ['lightIntensity', 'light', 'illumination', '光照', '光照强度', '照度']
}

REGION_ALIASES = ['region', 'province', 'area', 'location', '地区', '省份', '区域', '城市', 'city']

def normalize_columns(df):
    df = df.copy()
    df.columns = [str(c).strip() for c in df.columns]
    lower_map = {c.lower(): c for c in df.columns}
    for target, aliases in COLUMN_ALIASES.items():
        if target in df.columns:
            continue
        for alias in aliases:
            if alias in df.columns:
                df = df.rename(columns={alias: target})
                break
            alias_lower = alias.lower()
            if alias_lower in lower_map:
                df = df.rename(columns={lower_map[alias_lower]: target})
                break
    return df

def pick_region_column(df):
    lower_map = {c.lower(): c for c in df.columns}
    for alias in REGION_ALIASES:
        if alias in df.columns:
            return alias
        alias_lower = alias.lower()
        if alias_lower in lower_map:
            return lower_map[alias_lower]
    return None

def filter_region(df, region_keyword):
    if not region_keyword:
        return df
    region_col = pick_region_column(df)
    if not region_col:
        return df
    series = df[region_col].astype(str)
    mask = series.str.contains(region_keyword, case=False, na=False) | series.str.contains('广西', case=False, na=False) | series.str.contains('Guangxi', case=False, na=False)
    filtered = df.loc[mask]
    return filtered if not filtered.empty else df

def ensure_timestamp(df):
    if 'timestamp' in df.columns:
        ts = df['timestamp']
        if np.issubdtype(ts.dtype, np.number):
            median_val = ts.dropna().median()
            if pd.notna(median_val) and median_val < 10**11:
                df['timestamp'] = (ts * 1000).astype('int64')
        else:
            dt = pd.to_datetime(ts, errors='coerce')
            df['timestamp'] = (dt.astype('int64') // 10**6)
        return df
    datetime_candidates = ['datetime', 'date_time', '时间', '日期时间']
    date_candidates = ['date', '日期']
    time_candidates = ['time', '小时', '时刻']
    lower_map = {c.lower(): c for c in df.columns}
    datetime_col = next((lower_map.get(c.lower()) for c in datetime_candidates if c.lower() in lower_map), None)
    date_col = next((lower_map.get(c.lower()) for c in date_candidates if c.lower() in lower_map), None)
    time_col = next((lower_map.get(c.lower()) for c in time_candidates if c.lower() in lower_map), None)
    if datetime_col:
        dt = pd.to_datetime(df[datetime_col], errors='coerce')
    elif date_col and time_col:
        dt = pd.to_datetime(df[date_col].astype(str) + ' ' + df[time_col].astype(str), errors='coerce')
    elif date_col:
        dt = pd.to_datetime(df[date_col], errors='coerce')
    else:
        dt = pd.date_range(end=datetime.now(), periods=len(df), freq='H')
    df['timestamp'] = (dt.astype('int64') // 10**6)
    return df

def prepare_training_data(df, region_keyword='广西'):
    df = normalize_columns(df)
    df = filter_region(df, region_keyword)
    df = ensure_timestamp(df)
    for param in PARAMS:
        if param not in df.columns:
            raise ValueError(f"Missing required column: {param}")
        df[param] = pd.to_numeric(df[param], errors='coerce')
    df = df.dropna(subset=['timestamp'] + PARAMS)
    return df

def resolve_data_path():
    candidates = [
        os.getenv('GUANGXI_DATA_PATH'),
        os.getenv('AGRI_DATA_PATH'),
        os.getenv('DATA_PATH'),
        'guangxi_agri_env.csv',
        os.path.join('data', 'guangxi_agri_env.csv'),
        'historical_data.csv'
    ]
    for path in candidates:
        if not path:
            continue
        if isinstance(path, str) and (path.startswith('http://') or path.startswith('https://')):
            return path
        if os.path.exists(path):
            return path
    return None

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
    
    # 2. 滞后特征 (Lag Features)
    for param in PARAMS:
        for lag in LAG_HOURS:
            df[f'{param}_lag_{lag}'] = df[param].shift(lag)
            
    # 3. 滚动统计特征 (Rolling Features)
    for param in PARAMS:
        for window in ROLL_WINDOWS:
            df[f'{param}_roll_mean_{window}'] = df[param].rolling(window=window).mean()
            df[f'{param}_roll_std_{window}'] = df[param].rolling(window=window).std()

    return df

def prepare_targets(df, horizon=12):
    """
    准备目标变量：预测未来 horizon 小时的数据
    """
    targets = []
    future_frames = []
    for i in range(1, horizon + 1):
        future_cols = {}
        for param in PARAMS:
            col_name = f'{param}_future_{i}'
            future_cols[col_name] = df[param].shift(-i)
            targets.append(col_name)
        future_frames.append(pd.DataFrame(future_cols))
    df = pd.concat([df] + future_frames, axis=1)
    return df, targets

def train_professional_model(csv_path, region_keyword='广西'):
    """
    从 CSV 文件训练专业模型
    """
    if not csv_path:
        print("Error: data path not found.")
        return
    if not (str(csv_path).startswith('http://') or str(csv_path).startswith('https://')) and not os.path.exists(csv_path):
        print(f"Error: {csv_path} not found.")
        return
        
    # 加载数据
    print("Loading data...")
    df = pd.read_csv(csv_path)
    try:
        df = prepare_training_data(df, region_keyword=region_keyword)
    except Exception as exc:
        print(f"Error: {exc}")
        return
    
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
    model = train_with_tuning(X_train, y_train)
    
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

def train_with_tuning(X_train, y_train):
    enable_tuning = os.getenv('ENABLE_TUNING', '1') != '0'
    if not enable_tuning or len(X_train) < 200:
        base_model = RandomForestRegressor(
            n_estimators=240,
            max_depth=18,
            min_samples_split=4,
            min_samples_leaf=2,
            max_features='sqrt',
            random_state=42,
            n_jobs=-1
        )
        model = MultiOutputRegressor(base_model)
        model.fit(X_train, y_train)
        return model

    base_model = RandomForestRegressor(random_state=42, n_jobs=-1)
    model = MultiOutputRegressor(base_model)
    param_grid = [
        {'estimator__n_estimators': [200], 'estimator__max_depth': [16], 'estimator__min_samples_split': [4], 'estimator__min_samples_leaf': [2], 'estimator__max_features': ['sqrt']},
        {'estimator__n_estimators': [260], 'estimator__max_depth': [18], 'estimator__min_samples_split': [4], 'estimator__min_samples_leaf': [2], 'estimator__max_features': ['sqrt']},
        {'estimator__n_estimators': [320], 'estimator__max_depth': [20], 'estimator__min_samples_split': [3], 'estimator__min_samples_leaf': [2], 'estimator__max_features': ['sqrt']},
        {'estimator__n_estimators': [240], 'estimator__max_depth': [18], 'estimator__min_samples_split': [5], 'estimator__min_samples_leaf': [1], 'estimator__max_features': [0.7]},
        {'estimator__n_estimators': [300], 'estimator__max_depth': [22], 'estimator__min_samples_split': [4], 'estimator__min_samples_leaf': [1], 'estimator__max_features': [0.7]}
    ]
    tscv = TimeSeriesSplit(n_splits=3)
    search = GridSearchCV(
        model,
        param_grid=param_grid,
        scoring='neg_mean_absolute_error',
        cv=tscv,
        n_jobs=-1
    )
    search.fit(X_train, y_train)
    return search.best_estimator_

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
    data_path = resolve_data_path()
    if not data_path:
        generate_sample_data()
        data_path = 'historical_data.csv'
    region_keyword = os.getenv('AGRI_REGION', '广西')
    train_professional_model(data_path, region_keyword=region_keyword)
