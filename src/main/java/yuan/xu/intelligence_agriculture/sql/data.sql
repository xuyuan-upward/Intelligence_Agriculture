-- =========================================================================================
-- 数据库初始化数据 (根据用户提供的需求生成)
-- 环境编码: YuXuan (许苑园)
-- =========================================================================================

-- 1. 初始化温室/环境表 (sys_greenhouse)
-- -----------------------------------------------------------------------------------------
INSERT INTO sys_greenhouse (id, env_code, env_name, location, status, create_time, update_time)
VALUES (1, 'YuXuan', '许苑园', '智能化农业示范基地A区', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE env_name = VALUES(env_name), location = VALUES(location);


-- 2. 初始化环境参数类型表 (sys_env_metric)
-- 对应 EnvParameterType.java 枚举
-- -----------------------------------------------------------------------------------------
INSERT INTO sys_env_metric (id, env_parameter_type, env_parameter_name, create_time, update_time) VALUES
(1, 1, '空气温度', NOW(), NOW()),
(2, 2, '空气湿度', NOW(), NOW()),
(3, 3, '土壤温度', NOW(), NOW()),
(4, 4, '土壤湿度', NOW(), NOW()),
(5, 5, 'CO2浓度', NOW(), NOW()),
(6, 6, '光照强度', NOW(), NOW())
ON DUPLICATE KEY UPDATE env_parameter_name = VALUES(env_parameter_name);


-- 3. 初始化环境阈值表 (sys_env_threshold)
-- 为 'YuXuan' 环境下的 6 种参数设置默认阈值
-- -----------------------------------------------------------------------------------------
INSERT INTO sys_env_threshold (greenhouse_env_code, env_parameter_type, min_value, max_value, create_time, update_time) VALUES
('YuXuan', 1, 15.00, 30.00, NOW(), NOW()), -- 空气温度 (15-30℃)
('YuXuan', 2, 40.00, 70.00, NOW(), NOW()), -- 空气湿度 (40-70%)
('YuXuan', 3, 10.00, 25.00, NOW(), NOW()), -- 土壤温度 (10-25℃)
('YuXuan', 4, 30.00, 60.00, NOW(), NOW()), -- 土壤湿度 (30-60%)
('YuXuan', 5, 400.00, 1000.00, NOW(), NOW()), -- CO2浓度 (400-1000ppm)
('YuXuan', 6, 2000.00, 50000.00, NOW(), NOW()) -- 光照强度 (2000-50000lux)
ON DUPLICATE KEY UPDATE min_value = VALUES(min_value), max_value = VALUES(max_value);


-- 4. 初始化采集设备表 (sys_sensor_device)
-- 根据需求:
-- 空气温度 (DHT11, DS18B20)
-- 空气湿度 (DHT11, AM2301)
-- 土壤温度 (DHT11-YL-100)
-- 土壤湿度 (YL-100)
-- CO2 (SGP30)
-- 光照 (GY-302BH17501)
-- -----------------------------------------------------------------------------------------
INSERT INTO sys_sensor_device (device_code, device_name, greenhouse_env_code, env_parameter_type, create_time, update_time) VALUES
('S_AIR_TEMP_001', '空气温度传感器(DHT11)', 'YuXuan', 1, NOW(), NOW()),
('S_AIR_TEMP_002', '空气温度传感器(DS18B20)', 'YuXuan', 1, NOW(), NOW()),

('S_AIR_HUM_001', '空气湿度传感器(DHT11)', 'YuXuan', 2, NOW(), NOW()),
('S_AIR_HUM_002', '空气湿度传感器(AM2301)', 'YuXuan', 2, NOW(), NOW()),

('S_SOIL_TEMP_001', '土壤温度传感器(DHT11-YL-100)', 'YuXuan', 3, NOW(), NOW()),

('S_SOIL_HUM_001', '土壤湿度传感器(YL-100)', 'YuXuan', 4, NOW(), NOW()),

('S_CO2_001', 'CO2浓度传感器(SGP30)', 'YuXuan', 5, NOW(), NOW()),

('S_LIGHT_001', '光照强度传感器(GY-302BH17501)', 'YuXuan', 6, NOW(), NOW())
ON DUPLICATE KEY UPDATE device_name = VALUES(device_name), env_parameter_type = VALUES(env_parameter_type);


-- 5. 初始化控制设备表 (sys_control_device)
-- 需先查询 sys_env_threshold 表获取对应的 id 进行关联
-- 这里使用子查询动态获取 env_threshold_id
-- 
-- 映射关系:
-- 水泵(Water5V)      -> 控制土壤湿度 (Type=4)
-- 加热片1(PTC1)      -> 控制空气温度 (Type=1)
-- 加热片2(PTC2)      -> 控制土壤温度 (Type=3)
-- 风机(DC5V)         -> 控制CO2浓度 (Type=5)
-- 补光灯(6mm5V)      -> 控制光照强度 (Type=6)
-- 加湿器(DYY)        -> 控制空气湿度 (Type=2)
-- -----------------------------------------------------------------------------------------

-- 插入 水泵 (关联土壤湿度阈值)
INSERT INTO sys_control_device (device_code, device_name, greenhouse_env_code, env_threshold_id, control_mode, status, create_time, update_time)
SELECT 'C_WATER_001', '水泵(Water5V)', 'YuXuan', id, 0, 0, NOW(), NOW()
FROM sys_env_threshold WHERE greenhouse_env_code = 'YuXuan' AND env_parameter_type = 4
ON DUPLICATE KEY UPDATE device_name = VALUES(device_name);

-- 插入 加热片1 (关联空气温度阈值)
INSERT INTO sys_control_device (device_code, device_name, greenhouse_env_code, env_threshold_id, control_mode, status, create_time, update_time)
SELECT 'C_HEATER_001', '加热片1(PTC1)', 'YuXuan', id, 0, 0, NOW(), NOW()
FROM sys_env_threshold WHERE greenhouse_env_code = 'YuXuan' AND env_parameter_type = 1
ON DUPLICATE KEY UPDATE device_name = VALUES(device_name);

-- 插入 加热片2 (关联土壤温度阈值)
INSERT INTO sys_control_device (device_code, device_name, greenhouse_env_code, env_threshold_id, control_mode, status, create_time, update_time)
SELECT 'C_HEATER_002', '加热片2(PTC2)', 'YuXuan', id, 0, 0, NOW(), NOW()
FROM sys_env_threshold WHERE greenhouse_env_code = 'YuXuan' AND env_parameter_type = 3
ON DUPLICATE KEY UPDATE device_name = VALUES(device_name);

-- 插入 风机 (关联CO2浓度阈值)
INSERT INTO sys_control_device (device_code, device_name, greenhouse_env_code, env_threshold_id, control_mode, status, create_time, update_time)
SELECT 'C_FAN_001', '风机(DC5V)', 'YuXuan', id, 0, 0, NOW(), NOW()
FROM sys_env_threshold WHERE greenhouse_env_code = 'YuXuan' AND env_parameter_type = 5
ON DUPLICATE KEY UPDATE device_name = VALUES(device_name);

-- 插入 补光灯 (关联光照强度阈值)
INSERT INTO sys_control_device (device_code, device_name, greenhouse_env_code, env_threshold_id, control_mode, status, create_time, update_time)
SELECT 'C_LIGHT_001', '补光灯(6mm5V)', 'YuXuan', id, 0, 0, NOW(), NOW()
FROM sys_env_threshold WHERE greenhouse_env_code = 'YuXuan' AND env_parameter_type = 6
ON DUPLICATE KEY UPDATE device_name = VALUES(device_name);

-- 插入 加湿器 (关联空气湿度阈值)
INSERT INTO sys_control_device (device_code, device_name, greenhouse_env_code, env_threshold_id, control_mode, status, create_time, update_time)
SELECT 'C_HUMIDIFIER_001', '加湿器(DYY)', 'YuXuan', id, 0, 0, NOW(), NOW()
FROM sys_env_threshold WHERE greenhouse_env_code = 'YuXuan' AND env_parameter_type = 2
ON DUPLICATE KEY UPDATE device_name = VALUES(device_name);
