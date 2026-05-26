package com.atguigu.product.controller;

import com.atguigu.common.result.R;
import com.atguigu.common.enums.UserRole;
import com.atguigu.product.bean.Category;
import com.atguigu.common.context.UserContext;
import com.atguigu.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/{id}")
    public R getCategory(@PathVariable("id") final Long categoryId) {
        Category category = categoryService.getCategoryById(categoryId);
        if (category == null) {
            return R.notFound("分类不存在");
        }
        return R.ok("查询分类成功", category);
    }

    @GetMapping
    public R listCategories() {
        List<Category> list = categoryService.listAllCategories();
        return R.ok("查询分类列表成功", list);
    }

    @GetMapping("/root")
    public R listRootCategories() {
        List<Category> list = categoryService.listRootCategories();
        return R.ok("查询一级分类成功", list);
    }

    @GetMapping("/parent/{parentId}")
    public R listByParentId(@PathVariable("parentId") final Long parentId) {
        List<Category> list = categoryService.listByParentId(parentId);
        return R.ok("查询子分类成功", list);
    }

    @PostMapping
    public R saveOrUpdate(@RequestBody final Category category) {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        if (UserContext.get().getRole() != UserRole.ADMIN) {
            return R.error(403, "仅管理员可以管理分类");
        }
        Category saved = categoryService.saveOrUpdate(category);
        return R.ok("保存分类成功", saved);
    }

    @DeleteMapping("/{id}")
    public R deleteCategory(@PathVariable("id") final Long categoryId) {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        if (UserContext.get().getRole() != UserRole.ADMIN) {
            return R.error(403, "仅管理员可以删除分类");
        }
        try {
            categoryService.deleteCategory(categoryId);
            return R.ok("删除分类成功", null);
        } catch (IllegalArgumentException e) {
            return R.badRequest(e.getMessage());
        }
    }

    @PostMapping("/{id}/enable")
    public R enableCategory(@PathVariable("id") final Long categoryId,
                            @RequestParam("enabled") final boolean enabled) {
        if (UserContext.get() == null) {
            return R.error(403, "请先登录");
        }
        if (UserContext.get().getRole() != UserRole.ADMIN) {
            return R.error(403, "仅管理员可以启用/禁用分类");
        }
        categoryService.enableCategory(categoryId, enabled);
        return R.ok(enabled ? "分类已启用" : "分类已禁用", null);
    }
}
