-- ==========================================
-- 订单评价表（在cloudtry_order数据库中）
-- ==========================================

USE cloudtry_order;

-- 订单评价表
CREATE TABLE IF NOT EXISTS `order_review` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评价ID',
  `order_id` BIGINT NOT NULL UNIQUE COMMENT '订单ID，唯一约束防止重复评价',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
  `product_id` BIGINT NOT NULL COMMENT '商品ID',
  `rating` TINYINT NOT NULL COMMENT '评分：1-5星',
  `content` VARCHAR(1000) COMMENT '评价内容',
  `images` VARCHAR(2000) COMMENT '评价图片URL列表，JSON数组格式',
  `anonymous` TINYINT DEFAULT 0 COMMENT '是否匿名评价：0否 1是',
  `status` TINYINT DEFAULT 1 COMMENT '状态：1正常 0隐藏',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_order_id (`order_id`),
  INDEX idx_user_id (`user_id`),
  INDEX idx_merchant_id (`merchant_id`),
  INDEX idx_product_id (`product_id`),
  INDEX idx_rating (`rating`),
  INDEX idx_create_time (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单评价表';

-- 为订单表添加评价状态字段
ALTER TABLE `orders` ADD COLUMN `reviewed` TINYINT DEFAULT 0 COMMENT '是否已评价：0否 1是' AFTER `complete_time`;
ALTER TABLE `orders` ADD INDEX idx_reviewed (`reviewed`);

-- 为商品表添加评价统计字段（可选，用于快速查询）
-- ALTER TABLE `product` ADD COLUMN `review_count` INT DEFAULT 0 COMMENT '评价数量';
-- ALTER TABLE `product` ADD COLUMN `avg_rating` DECIMAL(3,2) DEFAULT 0.00 COMMENT '平均评分';
