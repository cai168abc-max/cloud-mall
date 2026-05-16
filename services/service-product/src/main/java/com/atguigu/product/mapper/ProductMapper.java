package com.atguigu.product.mapper;

import com.atguigu.product.bean.Product;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @Select("SELECT * FROM product WHERE merchant_id = #{merchantId} ORDER BY create_time DESC")
    List<Product> selectByMerchantId(@Param("merchantId") Long merchantId);

    @Select("SELECT * FROM product WHERE category_id = #{categoryId} ORDER BY create_time DESC")
    List<Product> selectByCategoryId(@Param("categoryId") Long categoryId);

    @SelectProvider(type = ProductSqlProvider.class, method = "searchProducts")
    IPage<Product> searchProducts(Page<Product> page, @Param("keyword") String keyword, @Param("categoryId") Long categoryId);

    @Select("SELECT * FROM product WHERE price BETWEEN #{minPrice} AND #{maxPrice}")
    List<Product> selectByPriceRange(@Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice);

    @Select("SELECT * FROM product WHERE num > 0")
    List<Product> selectInStock();

    @Select("SELECT * FROM product ORDER BY sales DESC LIMIT #{limit}")
    List<Product> selectHotProducts(@Param("limit") int limit);

    // 优化：使用后缀匹配替代前后通配符，可以利用索引，避免全表扫描
    // 同时添加LIMIT防止返回过多数据
    @Select("SELECT * FROM product WHERE name LIKE CONCAT(#{keyword}, '%') LIMIT 100")
    List<Product> selectByKeyword(@Param("keyword") String keyword);

    @Select("SELECT * FROM product ORDER BY create_time DESC")
    IPage<Product> selectPageAll(Page<Product> page);

    @Select("SELECT * FROM product WHERE merchant_id = #{merchantId}")
    IPage<Product> selectPageByMerchantId(Page<Product> page, @Param("merchantId") Long merchantId);

    @Update("UPDATE product SET num = num - #{quantity}, sales = sales + #{quantity}, update_time = NOW() " +
            "WHERE id = #{productId} AND num >= #{quantity}")
    int decreaseStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Update("UPDATE product SET num = num + #{quantity}, update_time = NOW() " +
            "WHERE id = #{productId}")
    int increaseStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Update("UPDATE product SET price = #{price}, update_time = NOW() WHERE id = #{id}")
    int updatePrice(@Param("id") Long id, @Param("price") BigDecimal price);

    @Update("UPDATE product SET enabled = #{enabled}, update_time = NOW() WHERE id = #{id}")
    int updateEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled);

    @Insert("INSERT INTO product (merchant_id, category_id, name, price, num, sales, image_url, enabled, create_time, update_time) " +
            "VALUES (#{merchantId}, #{categoryId}, #{name}, #{price}, #{num}, #{sales}, #{imageUrl}, #{enabled}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertProduct(Product product);

    @Update("UPDATE product SET name = #{name}, category_id = #{categoryId}, price = #{price}, num = #{num}, " +
            "description = #{description}, image_url = #{imageUrl}, update_time = NOW() WHERE id = #{id}")
    int updateProduct(Product product);

    @Delete("DELETE FROM product WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("<script>" +
            "SELECT id, merchant_id, category_id, name, price, num, sales, description, image_url, enabled, verified, create_time, update_time " +
            "FROM product WHERE id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<Product> batchSelectByIds(@Param("ids") List<Long> ids);

    @UpdateProvider(type = ProductSqlProvider.class, method = "batchIncreaseStock")
    int batchIncreaseStock(@Param("items") List<StockItem> items);

    @Update("<script>" +
            "UPDATE product SET num = num - CASE id " +
            "<foreach item='item' collection='items' separator=''>" +
            " WHEN #{item.productId} THEN #{item.quantity}" +
            "</foreach>" +
            " END, sales = sales + CASE id " +
            "<foreach item='item' collection='items' separator=''>" +
            " WHEN #{item.productId} THEN #{item.quantity}" +
            "</foreach>" +
            " END, update_time = NOW() WHERE id IN (" +
            "<foreach item='item' collection='items' separator=','>" +
            "#{item.productId}" +
            "</foreach>" +
            ") AND num >= CASE id " +
            "<foreach item='item' collection='items' separator=''>" +
            " WHEN #{item.productId} THEN #{item.quantity}" +
            "</foreach>" +
            " END" +
            "</script>")
    int batchDecreaseStock(@Param("items") List<StockItem> items);

    /**
     * 更新商品审核状态
     */
    @Update("UPDATE product SET verified = #{verified}, update_time = NOW() WHERE id = #{productId} AND (verified IS NULL OR verified = 0)")
    int updateVerified(@Param("productId") Long productId, @Param("verified") Boolean verified);

    /**
     * 使用行锁查询商品（用于分布式锁不可用时的兜底方案）
     */
    @Select("SELECT * FROM product WHERE id = #{productId} FOR UPDATE")
    Product selectByIdForUpdate(@Param("productId") Long productId);

    public static class StockItem {
        private Long productId;
        private Integer quantity;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
