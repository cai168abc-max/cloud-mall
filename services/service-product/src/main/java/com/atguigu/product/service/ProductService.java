package com.atguigu.product.service;

import com.atguigu.product.bean.Product;
import com.atguigu.product.mapper.ProductMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.math.BigDecimal;
import java.util.List;

public interface ProductService {
    /**
     * 根据商品 ID 获取商品详情（使用默认内存数据）
     */
    Product getProductById(Long productId);

    /**
     * 批量获取商品详情
     * 性能优化：减少N+1调用问题
     * @param ids 商品ID列表
     * @return 商品列表
     */
    List<Product> batchGetProducts(List<Long> ids);

    /**
     * 查询所有商品列表（使用默认内存数据）
     */
    List<Product> listAllProducts();

    /**
     * 按商家维度查询商品
     */
    List<Product> listByMerchant(Long merchantId);

    /**
     * 管理员/商家新增或更新商品（内存中）
     */
    Product saveOrUpdate(Product product);

    /**
     * 删除商品
     */
    boolean deleteProduct(Long productId);

    /**
     * 搜索商品
     */
    List<Product> searchProducts(String keyword, String category, String sortBy, boolean ascending);

    /**
     * 分页查询商品列表
     */
    IPage<Product> listProductsByPage(int pageNum, int pageSize);

    /**
     * 分页查询商家商品
     */
    IPage<Product> listProductsByMerchant(Long merchantId, int pageNum, int pageSize);

    /**
     * 查询有库存商品
     */
    List<Product> listInStock();

    /**
     * 查询热点商品（销量最高）
     */
    List<Product> listHotProducts(int limit);

    /**
     * 查询分类商品
     */
    List<Product> listByCategory(Long categoryId);

    /**
     * 查询分类商品
     */
    List<Product> listByKeyword(String keyword);

    /**
     * 按价格区间查询
     */
    List<Product> listByPriceRange(BigDecimal minPrice, BigDecimal maxPrice);

    /**
     * 更新商品价格
     */
    boolean updatePrice(Long productId, BigDecimal price);

    /**
     * 更新商品上下架状态
     */
    boolean updateEnabled(Long productId, Boolean enabled);

    /**
     * 扣减库存
     */
    boolean decreaseStock(Long productId, Integer quantity);

    /**
     * 增加库存
     */
    boolean increaseStock(Long productId, Integer quantity);

    /**
     * 批量增加库存 - 解决N+1调用问题
     * @param items 库存回滚项列表
     * @return 成功更新的商品数量
     */
    boolean batchIncreaseStock(List<ProductMapper.StockItem> items);
}
