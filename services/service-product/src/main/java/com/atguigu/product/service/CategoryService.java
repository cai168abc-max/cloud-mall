package com.atguigu.product.service;

import com.atguigu.product.bean.Category;

import java.util.List;

public interface CategoryService {
    /**
     * 根据分类 ID 获取分类详情
     */
    Category getCategoryById(Long categoryId);

    /**
     * 查询所有分类列表
     */
    List<Category> listAllCategories();

    /**
     * 查询一级分类
     */
    List<Category> listRootCategories();

    /**
     * 根据父分类 ID 查询子分类
     */
    List<Category> listByParentId(Long parentId);

    /**
     * 新增或更新分类
     */
    Category saveOrUpdate(Category category);

    /**
     * 删除分类
     */
    void deleteCategory(Long categoryId);

    /**
     * 启用/禁用分类
     */
    void enableCategory(Long categoryId, Boolean enabled);
}
