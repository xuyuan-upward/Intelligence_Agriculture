-- 温室/环境表
create table sys_greenhouse (

                                id          bigint auto_increment primary key comment '主键ID',
                                env_code    varchar(64) not null comment '环境编码(温室/区域)',
                                env_name    varchar(100) comment '环境名称',
                                location    varchar(255) comment '位置描述',
                                status      tinyint default 1 comment '状态(1:启用,0:停用)',

                                update_time datetime default CURRENT_TIMESTAMP
                                    on update CURRENT_TIMESTAMP,
                                create_time datetime default CURRENT_TIMESTAMP,

                                unique key uk_env_code (env_code)
) comment '温室/环境表';



-- 采集设备表
create table sys_sensor_device (
                                   id                  bigint auto_increment primary key comment '主键ID',
                                   device_code         varchar(64) not null comment '采集设备编号',
                                   device_name         varchar(100) comment '设备名称',
                                   greenhouse_env_code            varchar(64) not null comment '所属环境',
                                   update_time datetime default CURRENT_TIMESTAMP
                                       on update CURRENT_TIMESTAMP,
                                   create_time datetime default CURRENT_TIMESTAMP,

                                   unique key uk_sensor_device (device_code),
                                   key idx_env_code (greenhouse_env_code)
) comment '采集设备表';



-- 传感器采集实时数据表
create table iot_sensor_data
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    greenhouse_env_code      varchar(64)                        null comment '所属环境',
    air_temp          decimal(10, 2)                     null comment '空气温度',
    air_humidity      decimal(10, 2)                     null comment '空气湿度',
    light_intensity   decimal(10, 2)                     null comment '光照强度',
    soil_humidity     decimal(10, 2)                     null comment '土壤湿度',
    soil_temp         decimal(10, 2)                     null comment '土壤温度',
    co2_concentration decimal(10, 2)                     null comment 'CO2浓度',
    create_time       datetime default CURRENT_TIMESTAMP null comment '采集时间'
    ,
    key idx_env_code (greenhouse_env_code)
)
    comment '传感器采集实时数据表';


-- 环境阈值定义表
create index idx_create_time
    on iot_sensor_data (create_time);

create table sys_env_threshold (
                                   id bigint auto_increment primary key,
                                   greenhouse_env_code varchar(64) not null comment '所属环境',
                                   env_parameter_type int not null comment '环境参数类型',
                                   min_value decimal(10,2) comment '阈值下限',
                                   max_value decimal(10,2) comment '阈值上限',
                                   update_time datetime default CURRENT_TIMESTAMP
                                       on update CURRENT_TIMESTAMP,
                                   create_time datetime default CURRENT_TIMESTAMP,
                                   unique key uk_env_threshold (greenhouse_env_code, env_parameter_type)
) comment '环境阈值定义表';


-- 控制设备表
create table sys_control_device (
                                    id bigint auto_increment primary key,
                                    device_code varchar(64) not null comment '控制设备编号',
                                    env_threshold_id bigint comment '关联环境阈值',
                                    device_name varchar(100) comment '设备名称',
                                    greenhouse_env_code varchar(64) not null comment '所属环境',
                                    control_mode int default 0 comment '控制模式(0:手动,1:自动)',
                                    status tinyint default 0 comment '当前状态(0:关,1:开)',
                                    update_time datetime default CURRENT_TIMESTAMP
                                        on update CURRENT_TIMESTAMP,
                                    create_time datetime default CURRENT_TIMESTAMP,

                                    unique key uk_control_device (device_code),
                                    key idx_env_code (greenhouse_env_code)
) comment '控制设备表';


-- 环境参数类型表
create table sys_env_metric (
                                id bigint auto_increment primary key,
                                env_parameter_type int comment '环境参数类型',
                                env_parameter_name  varchar(64) comment '环境参数名称',
                                update_time datetime default CURRENT_TIMESTAMP
                                    on update CURRENT_TIMESTAMP,
                                create_time datetime default CURRENT_TIMESTAMP,

                                unique key uk_control_device (env_parameter_type)
) comment '环境参数类型表';


-- 设备控制日志表
create table sys_control_log (
                                 id bigint auto_increment primary key comment '主键ID',
                                 device_id bigint not null comment '关联的设备ID',
                                 operation_type varchar(20) comment '操作类型(MANUAL/AUTO)',
                                 operation_desc varchar(255) comment '操作描述',
                                 create_time datetime default CURRENT_TIMESTAMP comment '操作时间'
) comment '设备控制日志表';

