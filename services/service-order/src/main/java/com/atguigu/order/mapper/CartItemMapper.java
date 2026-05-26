package com.atguigu.order.mapper;

import com.atguigu.order.bean.CartItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    @Select("SELECT * FROM cart_item WHERE cart_id = #{cartId}")
    List<CartItem> selectByCartId(@Param("cartId") Long cartId);

    @Select("SELECT * FROM cart_item WHERE cart_id = #{cartId} AND product_id = #{productId} LIMIT 1")
    CartItem selectByCartIdAndProductId(@Param("cartId") Long cartId, @Param("productId") Long productId);

    @Select("SELECT * FROM cart_item WHERE cart_id = #{cartId}")
    IPage<CartItem> selectPageByCartId(Page<CartItem> page, @Param("cartId") Long cartId);

    @Select("SELECT SUM(quantity) FROM cart_item WHERE cart_id = #{cartId}")
    Integer selectCartCountByCartId(@Param("cartId") Long cartId);

    @Update("UPDATE cart_item SET quantity = quantity + #{quantity}, update_time = NOW() WHERE cart_id = #{cartId} AND product_id = #{productId}")
    int increaseQuantity(@Param("cartId") Long cartId, @Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Update("UPDATE cart_item SET quantity = #{quantity}, update_time = NOW() WHERE cart_id = #{cartId} AND product_id = #{productId}")
    int updateQuantity(@Param("cartId") Long cartId, @Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Update("UPDATE cart_item SET checked = #{checked}, update_time = NOW() WHERE cart_id = #{cartId} AND product_id = #{productId}")
    int updateChecked(@Param("cartId") Long cartId, @Param("productId") Long productId, @Param("checked") Boolean checked);

    @Update("UPDATE cart_item SET checked = #{checked}, update_time = NOW() WHERE cart_id = #{cartId}")
    int updateAllChecked(@Param("cartId") Long cartId, @Param("checked") Boolean checked);

    @Delete("DELETE FROM cart_item WHERE cart_id = #{cartId} AND product_id = #{productId}")
    int deleteByCartIdAndProductId(@Param("cartId") Long cartId, @Param("productId") Long productId);

    @Delete("DELETE FROM cart_item WHERE cart_id = #{cartId}")
    int clearByCartId(@Param("cartId") Long cartId);

    @Delete("DELETE FROM cart_item WHERE cart_id = #{cartId} AND checked = 1")
    int clearCheckedByCartId(@Param("cartId") Long cartId);

    @Insert("INSERT INTO cart_item (cart_id, product_id, product_name, price, quantity, checked, category_id, create_time, update_time) "
            + "VALUES (#{cartId}, #{productId}, #{productName}, #{price}, #{quantity}, #{checked}, #{categoryId}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertCartItem(CartItem cartItem);

    @Select("<script>"
            + "SELECT * FROM cart_item WHERE cart_id IN "
            + "<foreach item='cartId' collection='cartIds' open='(' separator=',' close=')'>"
            + "#{cartId}"
            + "</foreach>"
            + "</script>")
    List<CartItem> batchSelectByCartIds(@Param("cartIds") List<Long> cartIds);

    @Select("SELECT * FROM cart_item WHERE cart_id = #{cartId} AND checked = 1")
    List<CartItem> selectCheckedItems(@Param("cartId") Long cartId);
}
