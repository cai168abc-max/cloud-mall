package com.atguigu.product.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public class ProductSqlProvider {

    public String searchProducts(@Param("keyword") final String keyword, @Param("categoryId") final Long categoryId) {
        StringBuilder sb = new StringBuilder("SELECT * FROM product WHERE 1=1");

        // 性能优化：使用后缀匹配替代前后通配符，可以利用索引
        // LIKE 'keyword%' 可以使用索引，而 LIKE '%keyword%' 会导致全表扫描
        if (keyword != null && !keyword.isEmpty()) {
            sb.append(" AND name LIKE CONCAT(#{keyword}, '%')");
        }
        if (categoryId != null) {
            sb.append(" AND category_id = #{categoryId}");
        }
        sb.append(" ORDER BY create_time DESC");

        return sb.toString();
    }

    public String batchIncreaseStock(@Param("items") final List<ProductMapper.StockItem> items) {
        if (items == null || items.isEmpty()) {
            return "UPDATE product SET num = num WHERE 1=0";
        }
        
        StringBuilder sb = new StringBuilder("UPDATE product SET num = num + CASE id");
        for (int i = 0; i < items.size(); i++) {
            ProductMapper.StockItem item = items.get(i);
            sb.append(" WHEN #{items[").append(i).append("].productId} THEN #{items[").append(i).append("].quantity}");
        }
        sb.append(" END, update_time = NOW() WHERE id IN (");
        for (int i = 0; i < items.size(); i++) {
            sb.append("#{items[").append(i).append("].productId}");
            if (i < items.size() - 1) {
                sb.append(",");
            }
        }
        sb.append(")");
        
        return sb.toString();
    }
}
