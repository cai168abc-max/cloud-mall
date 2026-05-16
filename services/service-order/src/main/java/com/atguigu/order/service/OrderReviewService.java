package com.atguigu.order.service;

import com.atguigu.order.bean.OrderReview;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.Map;

/**
 * 订单评价服务接口
 */
public interface OrderReviewService {

    /**
     * 创建评价
     * 包含评分校验、内容长度校验、图片数量校验、分布式锁防重复评价、订单状态校验
     *
     * @param orderId    订单ID
     * @param userId     用户ID
     * @param rating     评分（1-5）
     * @param content    评价内容
     * @param images     评价图片URL列表
     * @param anonymous  是否匿名
     * @return 评价信息
     */
    OrderReview createReview(Long orderId, Long userId, Integer rating, String content, List<String> images, Boolean anonymous);

    /**
     * 根据ID获取评价详情
     *
     * @param reviewId 评价ID
     * @return 评价信息
     */
    OrderReview getReviewById(Long reviewId);

    /**
     * 根据订单ID获取评价
     *
     * @param orderId 订单ID
     * @return 评价信息
     */
    OrderReview getReviewByOrderId(Long orderId);

    /**
     * 分页查询用户评价列表
     *
     * @param userId   用户ID
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页评价列表
     */
    IPage<OrderReview> listReviewsByUserId(Long userId, int pageNum, int pageSize);

    /**
     * 分页查询商品评价列表
     *
     * @param productId 商品ID
     * @param pageNum   页码
     * @param pageSize  每页数量
     * @return 分页评价列表
     */
    IPage<OrderReview> listReviewsByProductId(Long productId, int pageNum, int pageSize);

    /**
     * 分页查询商家评价列表
     *
     * @param merchantId 商家ID
     * @param pageNum    页码
     * @param pageSize   每页数量
     * @return 分页评价列表
     */
    IPage<OrderReview> listReviewsByMerchantId(Long merchantId, int pageNum, int pageSize);

    /**
     * 获取商品评价统计
     * 优先从Redis缓存获取，缓存不存在则从数据库查询并写入缓存
     *
     * @param productId 商品ID
     * @return 包含评价数量、平均分、各评分数量的统计信息
     */
    Map<String, Object> getProductReviewStats(Long productId);

    /**
     * 获取商品评价数量（从缓存）
     *
     * @param productId 商品ID
     * @return 评价数量
     */
    Long getProductReviewCount(Long productId);

    /**
     * 获取商品平均评分（从缓存）
     *
     * @param productId 商品ID
     * @return 平均评分
     */
    Double getProductAvgRating(Long productId);

    /**
     * 更新评价缓存
     * MQ消息消费后调用此方法更新Redis缓存
     *
     * @param productId 商品ID
     */
    void updateReviewCache(Long productId);

    /**
     * 同步商品评价统计到数据库
     * 定时任务调用，将Redis中的评价统计同步到数据库
     *
     * @param productId 商品ID
     */
    void syncReviewStatsToDb(Long productId);

    /**
     * 批量同步所有商品评价统计
     * 定时任务调用
     */
    void syncAllReviewStats();

    /**
     * 检查订单是否已评价
     *
     * @param orderId 订单ID
     * @return 是否已评价
     */
    boolean hasReviewed(Long orderId);

    /**
     * 隐藏/显示评价
     *
     * @param reviewId 评价ID
     * @param status   状态：1正常 0隐藏
     * @param operator 操作人ID（管理员）
     * @return 是否成功
     */
    boolean updateReviewStatus(Long reviewId, Integer status, Long operator);
}
