package com.atguigu.product.mapper;

import com.atguigu.product.bean.Category;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    @Select("SELECT * FROM category WHERE parent_id = #{parentId} ORDER BY create_time DESC")
    List<Category> selectByParentId(@Param("parentId") Long parentId);

    @Select("SELECT * FROM category WHERE parent_id IS NULL ORDER BY create_time DESC")
    List<Category> selectRootCategories();

    @Select("SELECT * FROM category WHERE name LIKE CONCAT('%', #{keyword}, '%')")
    List<Category> selectByKeyword(@Param("keyword") String keyword);

    @Select("SELECT * FROM category ORDER BY create_time DESC")
    IPage<Category> selectPageAll(Page<Category> page);

    @Insert("INSERT INTO category (parent_id, name, level, enabled, create_time, update_time) " +
            "VALUES (#{parentId}, #{name}, #{level}, #{enabled}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertCategory(Category category);

    @Update("UPDATE category SET name = #{name}, enabled = #{enabled}, update_time = NOW() WHERE id = #{id}")
    int updateCategory(@Param("id") Long id, @Param("name") String name, @Param("enabled") Boolean enabled);

    @Update("UPDATE category SET enabled = #{enabled}, update_time = NOW() WHERE id = #{id}")
    int updateEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled);

    @Delete("DELETE FROM category WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM category WHERE parent_id = #{parentId}")
    long countByParentId(@Param("parentId") Long parentId);

    @Select("<script>" +
            "SELECT id, parent_id, name, level, enabled, create_time, update_time FROM category WHERE id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<Category> batchSelectByIds(@Param("ids") List<Long> ids);
}
