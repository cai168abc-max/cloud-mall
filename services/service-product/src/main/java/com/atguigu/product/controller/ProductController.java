package com.atguigu.product.controller;

import com.atguigu.common.annotation.RequirePermission;
import com.atguigu.common.bean.UserInfo;
import com.atguigu.common.context.UserContext;
import com.atguigu.common.enums.RequireMode;
import com.atguigu.common.enums.UserRole;
import com.atguigu.common.result.R;
import com.atguigu.product.bean.Product;
import com.atguigu.product.service.CategoryService;
import com.atguigu.product.service.ProductService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品控制器
 * 使用权限注解进行权限控制
 */
@RestController
@RequestMapping("/api/product")
@Validated
@RequiredArgsConstructor
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;

    private final CategoryService categoryService;

    @GetMapping("/{id}")
    @RequirePermission("product:read")
    public R getProduct(@PathVariable @NotNull Long id) {
        Product product = productService.getProductById(id);
        if (product == null) {
            return R.error(404, "商品不存在");
        }
        return R.ok("查询商品成功", product);
    }

    @GetMapping
    @RequirePermission("product:read")
    public R listProducts(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        IPage<Product> pageResult = productService.listProductsByPage(page, size);
        return R.ok("查询商品列表成功", pageResult);
    }

    @GetMapping("/merchant/{merchantId}")
    @RequirePermission(value = {"product:read", "merchant:manage"}, mode = RequireMode.ANY)
    public R listByMerchant(
            @PathVariable("merchantId") Long merchantId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        UserInfo user = UserContext.get();
        if (user.getRole() == UserRole.MERCHANT && !merchantId.equals(user.getId())) {
            return R.error(403, "商家只能查看自己的商品");
        }
        IPage<Product> pageResult = productService.listProductsByMerchant(merchantId, page, size);
        return R.ok("查询商家商品成功", pageResult);
    }

    @GetMapping("/category/{categoryId}")
    @RequirePermission("product:read")
    public R listByCategory(@PathVariable("categoryId") Long categoryId) {
        List<Product> list = productService.listByCategory(categoryId);
        return R.ok("查询分类商品成功", list);
    }

    @GetMapping("/inStock")
    @RequirePermission("product:read")
    public R listInStock() {
        List<Product> list = productService.listInStock();
        return R.ok("查询有货商品成功", list);
    }

    @GetMapping("/hot")
    @RequirePermission("product:read")
    public R listHotProducts(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        List<Product> list = productService.listHotProducts(limit);
        return R.ok("查询热点商品成功", list);
    }

    @GetMapping("/search")
    @RequirePermission("product:read")
    public R searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(value = "sortBy", defaultValue = "id") String sortBy,
            @RequestParam(value = "ascending", defaultValue = "true") boolean ascending) {
        List<Product> list = productService.searchProducts(keyword, category, sortBy, ascending);
        return R.ok("搜索成功", list);
    }

    @PostMapping("/manage/save")
    @RequirePermission(value = {"product:write", "merchant:manage"}, mode = RequireMode.ANY)
    public R saveProduct(@RequestBody Product product) {
        UserInfo user = UserContext.get();
        if (user.getRole() == UserRole.MERCHANT) {
            product.setMerchantId(user.getId());
        }
        Product result = productService.saveOrUpdate(product);
        return result != null ? R.ok("商品保存成功") : R.error("商品保存失败");
    }

    @DeleteMapping("/manage/{id}")
    @RequirePermission(value = {"product:delete", "merchant:manage"}, mode = RequireMode.ANY)
    public R deleteProduct(@PathVariable("id") Long id) {
        UserInfo user = UserContext.get();
        Product product = productService.getProductById(id);
        if (product == null) {
            return R.error(404, "商品不存在");
        }
        if (user.getRole() == UserRole.MERCHANT) {
            if (!product.getMerchantId().equals(user.getId())) {
                return R.error(403, "无权限删除他人的商品");
            }
        }
        boolean success = productService.deleteProduct(id);
        return success ? R.ok("商品删除成功") : R.error("商品删除失败");
    }

    @PutMapping("/manage/{id}/price")
    @RequirePermission(value = {"product:write", "merchant:manage"}, mode = RequireMode.ANY)
    public R updatePrice(@PathVariable("id") Long id, @RequestParam BigDecimal price) {
        UserInfo user = UserContext.get();
        Product product = productService.getProductById(id);
        if (product == null) {
            return R.error(404, "商品不存在");
        }
        if (user.getRole() == UserRole.MERCHANT) {
            if (!product.getMerchantId().equals(user.getId())) {
                return R.error(403, "无权限修改他人的商品");
            }
        }
        boolean success = productService.updatePrice(id, price);
        return success ? R.ok("价格修改成功") : R.error("价格修改失败");
    }

}
