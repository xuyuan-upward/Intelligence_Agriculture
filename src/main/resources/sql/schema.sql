-- 设备表
CREATE TABLE IF NOT EXISTS `sys_device` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_code` varchar(64) NOT NULL COMMENT '设备编号',
  `device_name` varchar(100) DEFAULT NULL COMMENT '设备名称',
  `device_type` int(11) DEFAULT NULL COMMENT '设备类型(1:FAN, 2:PUMP, 3:LIGHT, 4:HEATER, 5:HUMIDIFIER, 6:CO2_GEN, 11:TEMP, 12:HUM, 13:S_TEMP, 14:S_HUM, 15:LIGHT_S, 16:CO2_S)',
  `status` int(11) DEFAULT 0 COMMENT '状态(0:关闭, 1:开启)',
  `control_mode` int(11) DEFAULT 0 COMMENT '控制模式(0:手动, 1:自动)',
  `auto_threshold_type` int(11) DEFAULT NULL COMMENT '指标类型(1:AIR_TEMP, 2:AIR_HUMIDITY, 3:SOIL_TEMP, 4:SOIL_HUMIDITY, 5:LIGHT_INTENSITY, 6:CO2_CONCENTRATION)',
  `threshold_min` decimal(10,2) DEFAULT NULL COMMENT '阈值下限',
  `threshold_max` decimal(10,2) DEFAULT NULL COMMENT '阈值上限',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_code` (`device_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备信息表';

-- 传感器数据表
CREATE TABLE IF NOT EXISTS `iot_sensor_data` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_code` varchar(64) DEFAULT NULL COMMENT '设备编号',
  `air_temp` decimal(10,2) DEFAULT NULL COMMENT '空气温度',
  `air_humidity` decimal(10,2) DEFAULT NULL COMMENT '空气湿度',
  `light_intensity` decimal(10,2) DEFAULT NULL COMMENT '光照强度',
  `soil_humidity` decimal(10,2) DEFAULT NULL COMMENT '土壤湿度',
  `soil_temp` decimal(10,2) DEFAULT NULL COMMENT '土壤温度',
  `co2_concentration` decimal(10,2) DEFAULT NULL COMMENT 'CO2浓度',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '采集时间',
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传感器实时数据表';

-- 控制日志表
CREATE TABLE IF NOT EXISTS `sys_control_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` bigint(20) DEFAULT NULL COMMENT '设备ID',
  `operation_type` varchar(50) DEFAULT NULL COMMENT '操作类型(MANUAL_ON, AUTO_OFF等)',
  `operation_desc` varchar(255) DEFAULT NULL COMMENT '操作描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备控制日志表';

-- 初始化设备数据（含执行器和传感器）
INSERT INTO `sys_device` (`device_code`, `device_name`, `device_type`, `status`, `control_mode`, `auto_threshold_type`, `threshold_min`, `threshold_max`) VALUES 
-- 执行器 (1-6)
('DEV_FAN_001', '通风风扇', 1, 0, 0, 1, 20.00, 30.00),
('DEV_PUMP_001', '灌溉水泵', 2, 0, 0, 4, 30.00, 70.00),
('DEV_LIGHT_001', '补光灯', 3, 0, 0, 5, 1000.00, 5000.00),

-- 传感器 (11-16)，假设网关编号为 GW001
('GW001_11', '空气温度计', 11, 1, 1, NULL, NULL, NULL),
('GW001_12', '空气湿度计', 12, 1, 1, NULL, NULL, NULL),
('GW001_13', '土壤温度计', 13, 1, 1, NULL, NULL, NULL),
('GW001_14', '土壤湿度计', 14, 1, 1, NULL, NULL, NULL),
('GW001_15', '光照传感器', 15, 1, 1, NULL, NULL, NULL),
('GW001_16', 'CO2传感器', 16, 1, 1, NULL, NULL, NULL);
