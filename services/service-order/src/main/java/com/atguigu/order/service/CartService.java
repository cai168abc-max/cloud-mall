package com.atguigu.order.service;

import com.atguigu.order.bean.Cart;
import com.atguigu.order.bean.CartItem;

import java.util.List;
import java.util.Map;

/**
 * 购物车服务接口
 */
public interface CartService {
    /**
     * 添加商品到购物车
     */
    Cart addItemToCart(Long userId, Long productId, Integer quantity);

    /**
     * 从购物车中移除商品
     */
    Cart removeItemFromCart(Long userId, Long productId);

    /**
     * 更新购物车中商品数量
     */
    Cart updateItemQuantity(Long userId, Long productId, Integer quantity);

    /**
     * 更新商品选中状态
     */
    Cart updateItemChecked(Long userId, Long productId, Boolean checked);

    /**
     * 全选/取消全选
     */
    Cart updateAllItemsChecked(Long userId, Boolean checked);

    /**
     * 清空购物车
     */
    Cart clearCart(Long userId);

    /**
     * 获取用户购物车
     */
    Cart getCart(Long userId);

    /**
     * 获取购物车中选中的商品项
     */
    List<CartItem> getCheckedItems(Long userId);

    /**
     * 获取购物车数量
     */
    Integer getCartItemCount(Long userId);

    /**
     * 获取购物车数量（Controller用）
     */
    default int getCartCount(Long userId) {
        Cart cart = getCart(userId);
        if (cart != null && cart.getItems() != null) {
            return cart.getItems().size();
        }
        return 0;
    }

    /**
     * 获取购物车商品列表（Controller用）
     */
    default List<CartItem> listCartItems(Long userId) {
        Cart cart = getCart(userId);
        return cart != null ? cart.getItems() : null;
    }

    /**
     * 添加购物车商品（Controller用）
     */
    default CartItem addCartItem(Long userId, Long productId, Integer quantity) {
        Cart cart = addItemToCart(userId, productId, quantity);
        if (cart != null && cart.getItems() != null) {
            return cart.getItems().stream()
                    .filter(item -> item.getProductId().equals(productId))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * 更新购物车商品数量（Controller用）
     */
    default void updateQuantity(Long userId, Long productId, Integer quantity) {
        updateItemQuantity(userId, productId, quantity);
    }

    /**
     * 更新商品选中状态（Controller用）
     */
    default void checkItem(Long userId, Long productId, boolean checked) {
        updateItemChecked(userId, productId, checked);
    }

    /**
     * 全选/取消全选（Controller用）
     */
    default void checkAllItems(Long userId, boolean checked) {
        updateAllItemsChecked(userId, checked);
    }

    /**
     * 删除购物车商品（Controller用）
     */
    default void removeItem(Long userId, Long productId) {
        removeItemFromCart(userId, productId);
    }

    /**
     * 清空已选商品（Controller用）
     */
    default void clearCheckedItems(Long userId) {
        Cart cart = getCart(userId);
        if (cart != null && cart.getItems() != null) {
            List<CartItem> checkedItems = cart.getItems().stream()
                    .filter(CartItem::getChecked)
                    .toList();
            for (CartItem item : checkedItems) {
                removeItemFromCart(userId, item.getProductId());
            }
        }
    }

    /**
     * 获取购物车总价（Controller用）
     */
    default Map<String, Object> getCartTotal(Long userId) {
        Cart cart = getCart(userId);
        Map<String, Object> result = new java.util.HashMap<>();
        if (cart != null) {
            result.put("totalPrice", cart.getTotalAmount());
            result.put("totalCount", cart.getTotalCount());
            result.put("itemCount", cart.getItems() != null ? cart.getItems().size() : 0);
        } else {
            result.put("totalPrice", 0);
            result.put("totalCount", 0);
            result.put("itemCount", 0);
        }
        return result;
    }
}
