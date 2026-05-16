-- ==========================================
-- 商城数据库初始化脚本
-- 分开执行三个数据库的脚本
-- ==========================================

-- ==========================================
-- 第一部分：用户服务数据库 (cloudtry_user)
-- ==========================================
DROP DATABASE IF EXISTS cloudtry_user;
CREATE DATABASE cloudtry_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cloudtry_user;

-- 用户账号表
CREATE TABLE `user_account` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `phone` VARCHAR(20) UNIQUE,
  `email` VARCHAR(100) UNIQUE,
  `salt` VARCHAR(50) NOT NULL,
  `password_hash` VARCHAR(200) NOT NULL,
  `role` VARCHAR(20) NOT NULL DEFAULT 'USER',
  `nick_name` VARCHAR(100),
  `merchant_name` VARCHAR(100),
  `avatar_url` VARCHAR(500) COMMENT '用户头像URL',
  `enabled` TINYINT DEFAULT 1,
  `verified` TINYINT DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_phone (`phone`),
  INDEX idx_email (`email`),
  INDEX idx_role (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户地址表
CREATE TABLE `user_address` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `consignee` VARCHAR(50) NOT NULL,
  `phone` VARCHAR(20) NOT NULL,
  `province` VARCHAR(50),
  `city` VARCHAR(50),
  `district` VARCHAR(50),
  `detail` VARCHAR(200) NOT NULL,
  `is_default` TINYINT DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_id (`user_id`),
  FOREIGN KEY (`user_id`) REFERENCES `user_account`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==========================================
-- 第二部分：商品服务数据库 (cloudtry_product)
-- ==========================================
DROP DATABASE IF EXISTS cloudtry_product;
CREATE DATABASE cloudtry_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cloudtry_product;

-- 商品表
CREATE TABLE `product` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `name` VARCHAR(200) NOT NULL,
  `description` TEXT,
  `price` DECIMAL(10,2) NOT NULL,
  `num` INT DEFAULT 0,
  `sales` INT DEFAULT 0,
  `merchant_id` BIGINT NOT NULL,
  `category_id` BIGINT,
  `image_url` VARCHAR(500),
  `enabled` TINYINT DEFAULT 1,
  `verified` TINYINT DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_name (`name`(100)),
  INDEX idx_merchant_id (`merchant_id`),
  INDEX idx_category_id (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 分类表
CREATE TABLE `category` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `description` TEXT,
  `parent_id` BIGINT,
  `level` INT DEFAULT 1,
  `enabled` TINYINT DEFAULT 1,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_parent_id (`parent_id`),
  INDEX idx_level (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==========================================
-- 第三部分：订单服务数据库 (cloudtry_order)
-- ==========================================
DROP DATABASE IF EXISTS cloudtry_order;
CREATE DATABASE cloudtry_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cloudtry_order;

-- 订单表
CREATE TABLE `orders` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `merchant_id` BIGINT NOT NULL,
  `nick_name` VARCHAR(100),
  `address` VARCHAR(500),
  `total_price` DECIMAL(10,2) NOT NULL,
  `discount_amount` DECIMAL(10,2) DEFAULT 0,
  `pay_amount` DECIMAL(10,2) NOT NULL,
  `coupon_id` BIGINT,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `pay_time` DATETIME,
  `ship_time` DATETIME,
  `complete_time` DATETIME,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_id (`user_id`),
  INDEX idx_merchant_id (`merchant_id`),
  INDEX idx_status (`status`),
  INDEX idx_create_time (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 订单商品关联表
CREATE TABLE `order_item` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `product_name` VARCHAR(200),
  `price` DECIMAL(10,2),
  `quantity` INT,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_order_id (`order_id`),
  INDEX idx_product_id (`product_id`),
  FOREIGN KEY (`order_id`) REFERENCES `orders`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 购物车表
CREATE TABLE `cart` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL UNIQUE,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_id (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 购物车项表
CREATE TABLE `cart_item` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `cart_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `product_name` VARCHAR(200),
  `price` DECIMAL(10,2),
  `quantity` INT DEFAULT 1,
  `checked` TINYINT DEFAULT 1,
  `category_id` BIGINT,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_cart_id (`cart_id`),
  INDEX idx_product_id (`product_id`),
  FOREIGN KEY (`cart_id`) REFERENCES `cart`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 优惠券表
CREATE TABLE `coupon` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `merchant_id` BIGINT NOT NULL,
  `user_id` BIGINT,
  `name` VARCHAR(100) NOT NULL,
  `amount` DECIMAL(10,2) NOT NULL,
  `threshold` DECIMAL(10,2),
  `stock` INT DEFAULT 0,
  `valid_from` DATETIME,
  `valid_to` DATETIME,
  `status` VARCHAR(20) DEFAULT 'ACTIVE',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_merchant_id (`merchant_id`),
  INDEX idx_user_id (`user_id`),
  INDEX idx_status (`status`),
  INDEX idx_valid (`valid_from`, `valid_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户优惠券关联表
CREATE TABLE `user_coupon` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `coupon_id` BIGINT NOT NULL,
  `used` TINYINT DEFAULT 0,
  `acquire_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `use_time` DATETIME,
  INDEX idx_user_id (`user_id`),
  INDEX idx_coupon_id (`coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 虚拟账户表
CREATE TABLE `virtual_account` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL UNIQUE COMMENT '用户ID',
  `balance` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '余额',
  `frozen_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '冻结金额',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常 0冻结',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_id (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='虚拟账户表';

-- 交易流水表
CREATE TABLE `virtual_account_log` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `transaction_no` VARCHAR(64) NOT NULL UNIQUE COMMENT '交易流水号',
  `account_id` BIGINT NOT NULL COMMENT '账户ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `type` VARCHAR(20) NOT NULL COMMENT '类型：RECHARGE/PAY/REFUND/FREEZE/UNFREEZE',
  `amount` DECIMAL(10,2) NOT NULL COMMENT '金额',
  `balance_before` DECIMAL(10,2) NOT NULL COMMENT '变更前余额',
  `balance_after` DECIMAL(10,2) NOT NULL COMMENT '变更后余额',
  `related_order_id` BIGINT COMMENT '关联订单ID',
  `status` VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS/FAILED/PENDING',
  `remark` VARCHAR(200) COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_account_id (`account_id`),
  INDEX idx_user_id (`user_id`),
  INDEX idx_transaction_no (`transaction_no`),
  INDEX idx_create_time (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易流水表';

-- ==========================================
-- 初始化默认管理员账号
-- 密码: admin123
-- ==========================================
INSERT INTO cloudtry_user.user_account (`phone`, `salt`, `password_hash`, `role`, `nick_name`, `enabled`, `verified`)
VALUES ('admin', 'defaultSalt', 'c05354f7c1a7f1f0a3b0c6e5d4b2a8f9e6c7d1b3a5f7e9d2c4b6a8f0e2d4c6', 'ADMIN', '系统管理员', 1, 1);

-- ==========================================
-- 数据库迁移脚本（用于更新已有数据库）
-- ==========================================

-- 1. orders表：添加merchant_id和时间字段
ALTER TABLE `orders` ADD COLUMN `merchant_id` BIGINT NOT NULL AFTER `user_id`;
ALTER TABLE `orders` ADD COLUMN `pay_time` DATETIME AFTER `status`;
ALTER TABLE `orders` ADD COLUMN `ship_time` DATETIME AFTER `pay_time`;
ALTER TABLE `orders` ADD COLUMN `complete_time` DATETIME AFTER `ship_time`;
ALTER TABLE `orders` ADD INDEX idx_merchant_id (`merchant_id`);

-- 2. product表：添加image_url字段
ALTER TABLE `product` ADD COLUMN `image_url` VARCHAR(500) AFTER `category_id`;

-- 3. coupon表：添加merchant_id和user_id字段
ALTER TABLE `coupon` ADD COLUMN `merchant_id` BIGINT NOT NULL AFTER `id`;
ALTER TABLE `coupon` ADD COLUMN `user_id` BIGINT AFTER `merchant_id`;
ALTER TABLE `coupon` ADD INDEX idx_merchant_id (`merchant_id`);
ALTER TABLE `coupon` ADD INDEX idx_user_id (`user_id`);

-- ==========================================
-- 物流跟踪表（在cloudtry_order数据库中）
-- ==========================================

-- 物流信息主表
CREATE TABLE IF NOT EXISTS `logistics_info` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL UNIQUE COMMENT '订单ID，唯一关联',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
  `tracking_no` VARCHAR(100) COMMENT '物流单号',
  `carrier` VARCHAR(100) COMMENT '物流公司',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '物流状态: PENDING-待发货, SHIPPED-已发货, IN_TRANSIT-运输中, DELIVERED-已签收',
  `sender_name` VARCHAR(100) COMMENT '发件人姓名',
  `sender_phone` VARCHAR(20) COMMENT '发件人电话',
  `sender_address` VARCHAR(500) COMMENT '发件人地址',
  `receiver_name` VARCHAR(100) COMMENT '收件人姓名',
  `receiver_phone` VARCHAR(20) COMMENT '收件人电话',
  `receiver_address` VARCHAR(500) COMMENT '收件人地址',
  `estimated_arrival_time` DATETIME COMMENT '预计到达时间',
  `actual_arrival_time` DATETIME COMMENT '实际到达时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_order_id (`order_id`),
  INDEX idx_user_id (`user_id`),
  INDEX idx_merchant_id (`merchant_id`),
  INDEX idx_tracking_no (`tracking_no`),
  INDEX idx_status (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流信息主表';

-- 物流轨迹表
CREATE TABLE IF NOT EXISTS `logistics_trace` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `logistics_id` BIGINT NOT NULL COMMENT '物流信息ID',
  `trace_time` DATETIME NOT NULL COMMENT '轨迹时间',
  `status` VARCHAR(50) NOT NULL COMMENT '轨迹状态',
  `location` VARCHAR(200) COMMENT '所在地点',
  `description` VARCHAR(500) NOT NULL COMMENT '轨迹描述',
  `operator` VARCHAR(100) COMMENT '操作人/网点',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_logistics_id (`logistics_id`),
  INDEX idx_trace_time (`trace_time`),
  FOREIGN KEY (`logistics_id`) REFERENCES `logistics_info`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流轨迹表';

-- ==========================================
-- 商品审核日志表（在cloudtry_product数据库中）
-- ==========================================

-- 商品审核日志表
CREATE TABLE IF NOT EXISTS `product_audit_log` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `product_id` BIGINT NOT NULL COMMENT '商品ID',
  `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
  `auditor_id` BIGINT NOT NULL COMMENT '审核人ID',
  `before_status` TINYINT COMMENT '审核前状态',
  `after_status` TINYINT NOT NULL COMMENT '审核后状态：1通过 0拒绝',
  `reason` VARCHAR(200) COMMENT '审核原因/拒绝理由',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_product_id (`product_id`),
  INDEX idx_merchant_id (`merchant_id`),
  INDEX idx_create_time (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品审核日志表';

-- ==========================================
-- 售后工单表（在cloudtry_order数据库中）
-- ==========================================

-- 售后工单表
CREATE TABLE IF NOT EXISTS `after_sale_ticket` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
  `type` VARCHAR(20) NOT NULL COMMENT '类型：REFUND-仅退款/RETURN-退货退款/EXCHANGE-换货',
  `reason` VARCHAR(500) NOT NULL COMMENT '申请原因',
  `description` TEXT COMMENT '详细描述',
  `images` VARCHAR(1000) COMMENT '凭证图片，逗号分隔',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待处理/APPROVED-已同意/REJECTED-已拒绝/PROCESSING-处理中/COMPLETED-已完成/CANCELLED-已取消',
  `reject_reason` VARCHAR(200) COMMENT '拒绝原因',
  `refund_amount` DECIMAL(10,2) COMMENT '退款金额',
  `refund_transaction_no` VARCHAR(64) COMMENT '退款交易流水号',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_order_id (`order_id`),
  INDEX idx_user_id (`user_id`),
  INDEX idx_merchant_id (`merchant_id`),
  INDEX idx_status (`status`),
  INDEX idx_create_time (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后工单表';

-- ==========================================
-- 数据库迁移脚本：添加用户头像字段
-- ==========================================

-- 为已有数据库添加avatar_url字段
ALTER TABLE `user_account` ADD COLUMN IF NOT EXISTS `avatar_url` VARCHAR(500) COMMENT '用户头像URL' AFTER `merchant_name`;
