package com.atguigu.product.service.impl;

import com.atguigu.product.bean.Category;
import com.atguigu.product.mapper.CategoryMapper;
import com.atguigu.product.service.CategoryService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    @Override
    public Category getCategoryById(Long categoryId) {
        return categoryMapper.selectById(categoryId);
    }

    @Override
    public List<Category> listAllCategories() {
        return categoryMapper.selectList(null);
    }

    @Override
    public List<Category> listRootCategories() {
        return categoryMapper.selectRootCategories();
    }

    @Override
    public List<Category> listByParentId(Long parentId) {
        return categoryMapper.selectByParentId(parentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 30)
    public Category saveOrUpdate(Category category) {
        if (category.getId() == null) {
            if (category.getParentId() == null) {
                category.setLevel(1);
            } else {
                Category parent = categoryMapper.selectById(category.getParentId());
                if (parent != null) {
                    category.setLevel(parent.getLevel() + 1);
                }
            }
            categoryMapper.insertCategory(category);
        } else {
            categoryMapper.updateCategory(
                category.getId(),
                category.getName(),
                category.getEnabled()
            );
        }
        return category;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long categoryId) {
        long count = categoryMapper.countByParentId(categoryId);
        if (count > 0) {
            throw new IllegalArgumentException("该分类存在子分类，无法删除");
        }
        categoryMapper.deleteById(categoryId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableCategory(Long categoryId, Boolean enabled) {
        categoryMapper.updateEnabled(categoryId, enabled);
    }

    public List<Category> listByKeyword(String keyword) {
        return categoryMapper.selectByKeyword(keyword);
    }

    public IPage<Category> listCategoriesByPage(int pageNum, int pageSize) {
        Page<Category> page = new Page<>(pageNum, pageSize);
        return categoryMapper.selectPageAll(page);
    }
}
