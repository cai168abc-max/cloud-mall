package com.atguigu.common.cache;

/**
 * 统一缓存Key管理类
 * 
 * <p>缓存Key命名规范：</p>
 * <ul>
 *   <li>格式：{业务模块}:{业务对象}:{ID}[:子属性]</li>
 *   <li>示例：product:info:123, user:session:abc123, order:detail:456</li>
 *   <li>分隔符使用冒号(:)</li>
 * </ul>
 * 
 * <p>使用场景：</p>
 * <ul>
 *   <li>商品缓存：product:info:{productId}, product:stock:{productId}</li>
 *   <li>用户缓存：user:info:{userId}, user:session:{sessionId}</li>
 *   <li>订单缓存：order:info:{orderId}, order:user:{userId}</li>
 *   <li>分布式锁：lock:order:create:{orderId}</li>
 * </ul>
 */
public final class CacheKeyConstants {

    private CacheKeyConstants() {
        // 工具类，禁止实例化
    }

    // ==================== 分隔符 ====================
    
    /**
     * Key分隔符
     */
    public static final String SEPARATOR = ":";

    // ==================== 业务模块前缀 ====================
    
    /**
     * 商品模块前缀
     */
    public static final String MODULE_PRODUCT = "product";
    
    /**
     * 用户模块前缀
     */
    public static final String MODULE_USER = "user";
    
    /**
     * 订单模块前缀
     */
    public static final String MODULE_ORDER = "order";
    
    /**
     * 购物车模块前缀
     */
    public static final String MODULE_CART = "cart";
    
    /**
     * 优惠券模块前缀
     */
    public static final String MODULE_COUPON = "coupon";

    // ==================== 业务对象类型 ====================
    
    /**
     * 信息对象
     */
    public static final String TYPE_INFO = "info";
    
    /**
     * 库存对象
     */
    public static final String TYPE_STOCK = "stock";
    
    /**
     * 会话对象
     */
    public static final String TYPE_SESSION = "session";
    
    /**
     * 详情对象
     */
    public static final String TYPE_DETAIL = "detail";
    
    /**
     * 列表对象
     */
    public static final String TYPE_LIST = "list";

    // ==================== 特殊用途前缀 ====================
    
    /**
     * 分布式锁前缀
     */
    public static final String PREFIX_LOCK = "lock";
    
    /**
     * 空值缓存前缀
     */
    public static final String PREFIX_NULL = "null";
    
    /**
     * 热点数据前缀
     */
    public static final String PREFIX_HOT = "hot";

    // ==================== 商品相关Key ====================

    /**
     * 商品信息Key
     * 格式：product:info:{productId}
     */
    public static String productInfo(final Long productId) {
        return MODULE_PRODUCT + SEPARATOR + TYPE_INFO + SEPARATOR + productId;
    }

    /**
     * 商品库存Key
     * 格式：product:stock:{productId}
     */
    public static String productStock(Long productId) {
        return MODULE_PRODUCT + SEPARATOR + TYPE_STOCK + SEPARATOR + productId;
    }

    /**
     * 热点商品列表Key
     * 格式：product:hot:list
     */
    public static String hotProductList() {
        return MODULE_PRODUCT + SEPARATOR + PREFIX_HOT + SEPARATOR + TYPE_LIST;
    }

    // ==================== 用户相关Key ====================

    /**
     * 用户信息Key
     * 格式：user:info:{userId}
     */
    public static String userInfo(final Long userId) {
        return MODULE_USER + SEPARATOR + TYPE_INFO + SEPARATOR + userId;
    }

    /**
     * 用户会话Key
     * 格式：user:session:{sessionId}
     */
    public static String userSession(final String sessionId) {
        return MODULE_USER + SEPARATOR + TYPE_SESSION + SEPARATOR + sessionId;
    }

    /**
     * 用户地址列表Key.
     * 格式：user:address:list:{userId}
     * @param userId 用户ID
     * @return 缓存Key
     */
    public static String userAddressList(final Long userId) {
        return MODULE_USER + SEPARATOR + "address" + SEPARATOR 
            + TYPE_LIST + SEPARATOR + userId;
    }

    // ==================== 订单相关Key ====================

    /**
     * 订单信息Key.
     * 格式：order:info:{orderId}
     * @param orderId 订单ID
     * @return 缓存Key
     */
    public static String orderInfo(final Long orderId) {
        return MODULE_ORDER + SEPARATOR + TYPE_INFO + SEPARATOR + orderId;
    }

    /**
     * 用户订单列表Key.
     * 格式：order:user:list:{userId}
     * @param userId 用户ID
     * @return 缓存Key
     */
    public static String userOrderList(final Long userId) {
        return MODULE_ORDER + SEPARATOR + MODULE_USER + SEPARATOR 
            + TYPE_LIST + SEPARATOR + userId;
    }

    /**
     * 商家订单列表Key.
     * 格式：order:merchant:list:{merchantId}
     * @param merchantId 商家ID
     * @return 缓存Key
     */
    public static String merchantOrderList(final Long merchantId) {
        return MODULE_ORDER + SEPARATOR + "merchant" + SEPARATOR 
            + TYPE_LIST + SEPARATOR + merchantId;
    }

    // ==================== 购物车相关Key ====================

    /**
     * 用户购物车Key.
     * 格式：cart:user:{userId}
     * @param userId 用户ID
     * @return 缓存Key
     */
    public static String userCart(final Long userId) {
        return MODULE_CART + SEPARATOR + MODULE_USER + SEPARATOR + userId;
    }

    /**
     * 购物车选中商品Key.
     * 格式：cart:checked:{userId}
     * @param userId 用户ID
     * @return 缓存Key
     */
    public static String cartCheckedItems(final Long userId) {
        return MODULE_CART + SEPARATOR + "checked" + SEPARATOR + userId;
    }

    // ==================== 优惠券相关Key ====================

    /**
     * 优惠券信息Key.
     * 格式：coupon:info:{couponId}
     * @param couponId 优惠券ID
     * @return 缓存Key
     */
    public static String couponInfo(final Long couponId) {
        return MODULE_COUPON + SEPARATOR + TYPE_INFO + SEPARATOR + couponId;
    }

    /**
     * 优惠券库存Key.
     * 格式：coupon:stock:{couponId}
     * @param couponId 优惠券ID
     * @return 缓存Key
     */
    public static String couponStock(final Long couponId) {
        return MODULE_COUPON + SEPARATOR + TYPE_STOCK + SEPARATOR + couponId;
    }

    /**
     * 用户优惠券Key.
     * 格式：coupon:user:{userId}:{couponId}
     * @param userId 用户ID
     * @param couponId 优惠券ID
     * @return 缓存Key
     */
    public static String userCoupon(final Long userId, final Long couponId) {
        return MODULE_COUPON + SEPARATOR + MODULE_USER + SEPARATOR 
            + userId + SEPARATOR + couponId;
    }

    // ==================== 分布式锁Key ====================

    /**
     * 订单创建锁Key.
     * 格式：lock:order:create:{productId}:{userId}
     * @param productId 商品ID
     * @param userId 用户ID
     * @return 缓存Key
     */
    public static String orderCreateLock(final Long productId, final Long userId) {
        return PREFIX_LOCK + SEPARATOR + MODULE_ORDER + SEPARATOR + "create" 
            + SEPARATOR + productId + SEPARATOR + userId;
    }

    /**
     * 库存扣减锁Key.
     * 格式：lock:stock:deduct:{productId}
     * @param productId 商品ID
     * @return 缓存Key
     */
    public static String stockDeductLock(final Long productId) {
        return PREFIX_LOCK + SEPARATOR + TYPE_STOCK + SEPARATOR 
            + "deduct" + SEPARATOR + productId;
    }

    /**
     * 优惠券领取锁Key.
     * 格式：lock:coupon:acquire:{couponId}:{userId}
     * @param couponId 优惠券ID
     * @param userId 用户ID
     * @return 缓存Key
     */
    public static String couponAcquireLock(final Long couponId, final Long userId) {
        return PREFIX_LOCK + SEPARATOR + MODULE_COUPON + SEPARATOR 
            + "acquire" + SEPARATOR + couponId + SEPARATOR + userId;
    }

    // ==================== 空值缓存Key ====================

    /**
     * 空值缓存Key.
     * 格式：null:{originalKey}
     * @param originalKey 原始Key
     * @return 空值缓存Key
     */
    public static String nullCacheKey(final String originalKey) {
        return PREFIX_NULL + SEPARATOR + originalKey;
    }

    // ==================== 物流相关Key ====================

    /**
     * 物流模块前缀.
     */
    public static final String MODULE_LOGISTICS = "logistics";

    /**
     * 物流信息Key.
     * 格式：logistics:info:{orderId}
     * @param orderId 订单ID
     * @return 缓存Key
     */
    public static String logisticsInfo(final Long orderId) {
        return MODULE_LOGISTICS + SEPARATOR + TYPE_INFO + SEPARATOR + orderId;
    }

    /**
     * 物流轨迹Key.
     * 格式：logistics:trace:{logisticsId}
     * @param logisticsId 物流ID
     * @return 缓存Key
     */
    public static String logisticsTrace(final Long logisticsId) {
        return MODULE_LOGISTICS + SEPARATOR + "trace" + SEPARATOR + logisticsId;
    }

    /**
     * 用户物流列表Key.
     * 格式：logistics:user:list:{userId}
     * @param userId 用户ID
     * @return 缓存Key
     */
    public static String userLogisticsList(final Long userId) {
        return MODULE_LOGISTICS + SEPARATOR + MODULE_USER + SEPARATOR 
            + TYPE_LIST + SEPARATOR + userId;
    }

    /**
     * 商家物流列表Key.
     * 格式：logistics:merchant:list:{merchantId}
     * @param merchantId 商家ID
     * @return 缓存Key
     */
    public static String merchantLogisticsList(final Long merchantId) {
        return MODULE_LOGISTICS + SEPARATOR + "merchant" + SEPARATOR 
            + TYPE_LIST + SEPARATOR + merchantId;
    }

    /**
     * 订单发货锁Key.
     * 格式：lock:order:ship:{orderId}
     * @param orderId 订单ID
     * @return 缓存Key
     */
    public static String orderShipLock(final Long orderId) {
        return PREFIX_LOCK + SEPARATOR + MODULE_ORDER + SEPARATOR 
            + "ship" + SEPARATOR + orderId;
    }

    // ==================== 评价相关Key ====================

    /**
     * 评价模块前缀.
     */
    public static final String MODULE_REVIEW = "review";

    /**
     * 商品评价数量Key.
     * 格式：product:review:count:{productId}
     * @param productId 商品ID
     * @return 缓存Key
     */
    public static String productReviewCount(final Long productId) {
        return MODULE_PRODUCT + SEPARATOR + MODULE_REVIEW + SEPARATOR 
            + "count" + SEPARATOR + productId;
    }

    /**
     * 商品评价平均分Key.
     * 格式：product:review:avg:{productId}
     * @param productId 商品ID
     * @return 缓存Key
     */
    public static String productReviewAvg(final Long productId) {
        return MODULE_PRODUCT + SEPARATOR + MODULE_REVIEW + SEPARATOR 
            + "avg" + SEPARATOR + productId;
    }

    /**
     * 订单评价锁Key.
     * 格式：lock:order:review:{orderId}
     * @param orderId 订单ID
     * @return 缓存Key
     */
    public static String orderReviewLock(final Long orderId) {
        return PREFIX_LOCK + SEPARATOR + MODULE_ORDER + SEPARATOR 
            + MODULE_REVIEW + SEPARATOR + orderId;
    }

    /**
     * 用户评价列表Key.
     * 格式：review:user:list:{userId}
     * @param userId 用户ID
     * @return 缓存Key
     */
    public static String userReviewList(final Long userId) {
        return MODULE_REVIEW + SEPARATOR + MODULE_USER + SEPARATOR 
            + TYPE_LIST + SEPARATOR + userId;
    }

    /**
     * 商品评价列表Key.
     * 格式：review:product:list:{productId}
     * @param productId 商品ID
     * @return 缓存Key
     */
    public static String productReviewList(final Long productId) {
        return MODULE_REVIEW + SEPARATOR + MODULE_PRODUCT + SEPARATOR 
            + TYPE_LIST + SEPARATOR + productId;
    }

    /**
     * 商家评价列表Key.
     * 格式：review:merchant:list:{merchantId}
     * @param merchantId 商家ID
     * @return 缓存Key
     */
    public static String merchantReviewList(final Long merchantId) {
        return MODULE_REVIEW + SEPARATOR + "merchant" + SEPARATOR 
            + TYPE_LIST + SEPARATOR + merchantId;
    }

    // ==================== 库存预警相关Key ====================

    /**
     * 库存预警模块前缀.
     */
    public static final String MODULE_ALERT = "alert";

    /**
     * 库存预警已发送标记Key.
     * 格式：alert:sent:{productId}:{date}
     * @param productId 商品ID
     * @param date 日期
     * @return 缓存Key
     */
    public static String alertSent(final Long productId, final String date) {
        return MODULE_ALERT + SEPARATOR + "sent" + SEPARATOR 
            + productId + SEPARATOR + date;
    }

    /**
     * 库存预警配置Key.
     * 格式：alert:config:{productId}
     * @param productId 商品ID
     * @return 缓存Key
     */
    public static String alertConfig(final Long productId) {
        return MODULE_ALERT + SEPARATOR + "config" + SEPARATOR + productId;
    }

    /**
     * 商品当天预警次数Key.
     * 格式：alert:count:{productId}:{date}
     * @param productId 商品ID
     * @param date 日期
     * @return 缓存Key
     */
    public static String alertCount(final Long productId, final String date) {
        return MODULE_ALERT + SEPARATOR + "count" + SEPARATOR 
            + productId + SEPARATOR + date;
    }
}
