-- 库存预警配置表
CREATE TABLE IF NOT EXISTS inventory_alert_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL UNIQUE COMMENT '商品ID',
    threshold INT NOT NULL DEFAULT 10 COMMENT '预警阈值',
    alert_interval INT NOT NULL DEFAULT 1440 COMMENT '预警间隔（分钟）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存预警配置表';

-- 库存预警记录表
CREATE TABLE IF NOT EXISTS inventory_alert_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL COMMENT '商品ID',
    merchant_id BIGINT NOT NULL COMMENT '商家ID',
    stock INT NOT NULL COMMENT '当前库存',
    threshold INT NOT NULL COMMENT '预警阈值',
    status VARCHAR(20) NOT NULL COMMENT '状态：PENDING/SENT/FAILED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_product_id (product_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存预警记录表';
