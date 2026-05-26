package com.atguigu.order.controller;

import com.atguigu.common.result.R;
import com.atguigu.order.bean.CartItem;
import com.atguigu.common.context.UserContext;
import com.atguigu.order.service.CartService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings("EI_EXPOSE_REP2")
@RestController
@RequestMapping("/api/cart")
@Validated
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public R listCartItems() {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        Long userId = UserContext.get().getId();
        List<CartItem> list = cartService.listCartItems(userId);
        return R.ok("查询购物车成功", list);
    }

    @GetMapping("/count")
    public R getCartCount() {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        Long userId = UserContext.get().getId();
        int count = cartService.getCartCount(userId);
        return R.ok("查询购物车数量成功", count);
    }

    @GetMapping("/checked")
    public R getCheckedItems() {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        Long userId = UserContext.get().getId();
        List<CartItem> list = cartService.getCheckedItems(userId);
        return R.ok("查询已选商品成功", list);
    }

    @PostMapping("/add")
    public R addCartItem(@RequestParam @NotNull Long productId,
                         @RequestParam(value = "quantity", defaultValue = "1") @Min(1) Integer quantity) {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        Long userId = UserContext.get().getId();
        CartItem item = cartService.addCartItem(userId, productId, quantity);
        return R.ok("添加购物车成功", item);
    }

    @PutMapping("/quantity")
    public R updateQuantity(@RequestParam @NotNull Long productId,
                            @RequestParam @NotNull @Min(1) Integer quantity) {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        if (quantity <= 0) {
            return R.error(400, "数量必须大于0");
        }
        Long userId = UserContext.get().getId();
        cartService.updateQuantity(userId, productId, quantity);
        return R.ok("更新数量成功", null);
    }

    @PutMapping("/check")
    public R checkItem(@RequestParam("productId") Long productId,
                      @RequestParam("checked") boolean checked) {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        Long userId = UserContext.get().getId();
        cartService.checkItem(userId, productId, checked);
        return R.ok(checked ? "选中商品成功" : "取消选中成功", null);
    }

    @PutMapping("/checkAll")
    public R checkAllItems(@RequestParam("checked") boolean checked) {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        Long userId = UserContext.get().getId();
        cartService.checkAllItems(userId, checked);
        return R.ok(checked ? "全选成功" : "取消全选成功", null);
    }

    @DeleteMapping("/item")
    public R removeItem(@RequestParam @NotNull Long productId) {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        Long userId = UserContext.get().getId();
        cartService.removeItem(userId, productId);
        return R.ok("删除商品成功", null);
    }

    @DeleteMapping("/checked")
    public R clearCheckedItems() {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        Long userId = UserContext.get().getId();
        cartService.clearCheckedItems(userId);
        return R.ok("清空已选商品成功", null);
    }

    @DeleteMapping("/clear")
    public R clearCart() {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        Long userId = UserContext.get().getId();
        cartService.clearCart(userId);
        return R.ok("清空购物车成功", null);
    }

    @GetMapping("/total")
    public R getCartTotal() {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        Long userId = UserContext.get().getId();
        Map<String, Object> total = cartService.getCartTotal(userId);
        return R.ok("查询购物车总价成功", total);
    }
}
