-- =====================================================
-- 性能优化索引脚本
-- 创建时间: 2026-05-12
-- 说明: 为高频查询字段添加索引，提升查询性能
-- =====================================================

-- =====================================================
-- 1. Product表索引
-- =====================================================

-- 商品名称索引（支持模糊查询）
CREATE INDEX IF NOT EXISTS idx_product_name ON product(name);

-- 分类索引（支持按分类查询商品）
CREATE INDEX IF NOT EXISTS idx_product_category_id ON product(category_id);

-- 商家索引（支持按商家查询商品）
CREATE INDEX IF NOT EXISTS idx_product_merchant_id ON product(merchant_id);

-- 审核状态索引（支持按审核状态查询）
CREATE INDEX IF NOT EXISTS idx_product_verified ON product(verified);

-- 组合索引：商家+审核状态（支持商家商品审核列表查询）
CREATE INDEX IF NOT EXISTS idx_product_merchant_verified ON product(merchant_id, verified);

-- 价格索引（支持价格排序和范围查询）
CREATE INDEX IF NOT EXISTS idx_product_price ON product(price);

-- 库存索引（支持库存预警查询）
CREATE INDEX IF NOT EXISTS idx_product_num ON product(num);

-- 创建时间索引（支持按时间排序）
CREATE INDEX IF NOT EXISTS idx_product_create_time ON product(create_time);

-- =====================================================
-- 2. Order表索引
-- =====================================================

-- 用户索引（支持按用户查询订单）
CREATE INDEX IF NOT EXISTS idx_order_user_id ON `order`(user_id);

-- 订单状态索引（支持按状态查询订单）
CREATE INDEX IF NOT EXISTS idx_order_status ON `order`(status);

-- 创建时间索引（支持按时间排序和范围查询）
CREATE INDEX IF NOT EXISTS idx_order_create_time ON `order`(create_time);

-- 组合索引：用户+状态（支持用户订单状态查询）
CREATE INDEX IF NOT EXISTS idx_order_user_status ON `order`(user_id, status);

-- 组合索引：用户+创建时间（支持用户订单时间排序）
CREATE INDEX IF NOT EXISTS idx_order_user_create_time ON `order`(user_id, create_time);

-- 支付时间索引（支持支付统计）
CREATE INDEX IF NOT EXISTS idx_order_pay_time ON `order`(pay_time);

-- =====================================================
-- 3. OrderItem表索引
-- =====================================================

-- 订单ID索引（支持查询订单明细）
CREATE INDEX IF NOT EXISTS idx_order_item_order_id ON order_item(order_id);

-- 商品ID索引（支持查询商品销售记录）
CREATE INDEX IF NOT EXISTS idx_order_item_product_id ON order_item(product_id);

-- =====================================================
-- 4. CartItem表索引
-- =====================================================

-- 用户ID索引（支持查询用户购物车）
CREATE INDEX IF NOT EXISTS idx_cart_item_user_id ON cart_item(user_id);

-- 组合索引：用户+商品（支持检查商品是否在购物车）
CREATE INDEX IF NOT EXISTS idx_cart_item_user_product ON cart_item(user_id, product_id);

-- =====================================================
-- 5. VirtualAccount表索引
-- =====================================================

-- 用户ID索引（支持查询用户账户）
CREATE INDEX IF NOT EXISTS idx_virtual_account_user_id ON virtual_account(user_id);

-- =====================================================
-- 6. VirtualAccountLog表索引
-- =====================================================

-- 用户ID索引（支持查询用户交易记录）
CREATE INDEX IF NOT EXISTS idx_virtual_account_log_user_id ON virtual_account_log(user_id);

-- 交易流水号索引（支持幂等性检查）
CREATE INDEX IF NOT EXISTS idx_virtual_account_log_transaction_no ON virtual_account_log(transaction_no);

-- 订单ID索引（支持查询订单相关流水）
CREATE INDEX IF NOT EXISTS idx_virtual_account_log_order_id ON virtual_account_log(related_order_id);

-- 创建时间索引（支持按时间查询）
CREATE INDEX IF NOT EXISTS idx_virtual_account_log_create_time ON virtual_account_log(create_time);

-- =====================================================
-- 7. ProductAuditLog表索引
-- =====================================================

-- 商品ID索引（支持查询商品审核历史）
CREATE INDEX IF NOT EXISTS idx_product_audit_log_product_id ON product_audit_log(product_id);

-- 商家ID索引（支持查询商家审核记录）
CREATE INDEX IF NOT EXISTS idx_product_audit_log_merchant_id ON product_audit_log(merchant_id);

-- 审核人索引（支持查询审核人操作记录）
CREATE INDEX IF NOT EXISTS idx_product_audit_log_auditor_id ON product_audit_log(auditor_id);

-- 创建时间索引（支持按时间排序）
CREATE INDEX IF NOT EXISTS idx_product_audit_log_create_time ON product_audit_log(create_time);

-- =====================================================
-- 8. InventoryAlertConfig表索引
-- =====================================================

-- 商品ID索引（支持查询商品预警配置）
CREATE INDEX IF NOT EXISTS idx_inventory_alert_config_product_id ON inventory_alert_config(product_id);

-- 状态索引（支持查询启用的配置）
CREATE INDEX IF NOT EXISTS idx_inventory_alert_config_status ON inventory_alert_config(status);

-- =====================================================
-- 9. InventoryAlertLog表索引
-- =====================================================

-- 商品ID索引（支持查询商品预警记录）
CREATE INDEX IF NOT EXISTS idx_inventory_alert_log_product_id ON inventory_alert_log(product_id);

-- 状态索引（支持查询待处理的预警）
CREATE INDEX IF NOT EXISTS idx_inventory_alert_log_status ON inventory_alert_log(status);

-- 创建时间索引（支持按时间查询）
CREATE INDEX IF NOT EXISTS idx_inventory_alert_log_create_time ON inventory_alert_log(create_time);

-- =====================================================
-- 10. OrderReview表索引
-- =====================================================

-- 订单ID索引（支持查询订单评价）
CREATE INDEX IF NOT EXISTS idx_order_review_order_id ON order_review(order_id);

-- 商品ID索引（支持查询商品评价）
CREATE INDEX IF NOT EXISTS idx_order_review_product_id ON order_review(product_id);

-- 用户ID索引（支持查询用户评价）
CREATE INDEX IF NOT EXISTS idx_order_review_user_id ON order_review(user_id);

-- 创建时间索引（支持按时间排序）
CREATE INDEX IF NOT EXISTS idx_order_review_create_time ON order_review(create_time);

-- =====================================================
-- 11. LogisticsInfo表索引
-- =====================================================

-- 订单ID索引（支持查询订单物流）
CREATE INDEX IF NOT EXISTS idx_logistics_info_order_id ON logistics_info(order_id);

-- 物流单号索引（支持按物流单号查询）
CREATE INDEX IF NOT EXISTS idx_logistics_info_tracking_no ON logistics_info(tracking_no);

-- =====================================================
-- 12. AfterSaleTicket表索引
-- =====================================================

-- 订单ID索引（支持查询订单售后）
CREATE INDEX IF NOT EXISTS idx_after_sale_ticket_order_id ON after_sale_ticket(order_id);

-- 用户ID索引（支持查询用户售后）
CREATE INDEX IF NOT EXISTS idx_after_sale_ticket_user_id ON after_sale_ticket(user_id);

-- 商家ID索引（支持查询商家售后）
CREATE INDEX IF NOT EXISTS idx_after_sale_ticket_merchant_id ON after_sale_ticket(merchant_id);

-- 状态索引（支持按状态查询）
CREATE INDEX IF NOT EXISTS idx_after_sale_ticket_status ON after_sale_ticket(status);

-- 创建时间索引（支持按时间排序）
CREATE INDEX IF NOT EXISTS idx_after_sale_ticket_create_time ON after_sale_ticket(create_time);

-- =====================================================
-- 验证索引创建
-- =====================================================

-- 查看product表索引
-- SHOW INDEX FROM product;

-- 查看order表索引
-- SHOW INDEX FROM `order`;

-- 查看所有表的索引大小
-- SELECT 
--     TABLE_NAME,
--     INDEX_NAME,
--     ROUND(STAT_VALUE * @@innodb_page_size / 1024 / 1024, 2) AS size_mb
-- FROM mysql.innodb_index_stats
-- WHERE DATABASE_NAME = 'cloudtry_product'
-- AND STAT_NAME = 'size'
-- ORDER BY size_mb DESC;
