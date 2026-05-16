package com.atguigu.user.mapper;

import com.atguigu.common.bean.UserAddress;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddress> {

    @Select("SELECT * FROM user_address WHERE user_id = #{userId} ORDER BY is_default DESC, create_time DESC")
    List<UserAddress> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM user_address WHERE user_id = #{userId} AND is_default = 1 LIMIT 1")
    UserAddress selectDefaultByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM user_address WHERE user_id = #{userId}")
    IPage<UserAddress> selectPageByUserId(Page<UserAddress> page, @Param("userId") Long userId);

    @Update("UPDATE user_address SET is_default = 0 WHERE user_id = #{userId}")
    void clearDefaultByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM user_address WHERE id = #{id} AND user_id = #{userId} LIMIT 1")
    UserAddress selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Update("UPDATE user_address SET is_default = 0 WHERE user_id = #{userId} AND id != #{excludeId}")
    int clearDefaultExcept(@Param("userId") Long userId, @Param("excludeId") Long excludeId);

    @Update("UPDATE user_address SET is_default = 1 WHERE id = #{id} AND user_id = #{userId}")
    int setDefault(@Param("id") Long id, @Param("userId") Long userId);

    @Insert("INSERT INTO user_address (user_id, consignee, phone, province, city, district, detail, is_default, create_time, update_time) " +
            "VALUES (#{userId}, #{consignee}, #{phone}, #{province}, #{city}, #{district}, #{detail}, #{isDefault}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertAddress(UserAddress address);

    @Update("UPDATE user_address SET consignee = #{consignee}, phone = #{phone}, province = #{province}, city = #{city}, " +
            "district = #{district}, detail = #{detail}, update_time = NOW() WHERE id = #{id} AND user_id = #{userId}")
    int updateAddress(@Param("id") Long id, @Param("userId") Long userId, @Param("consignee") String consignee,
                     @Param("phone") String phone, @Param("province") String province, @Param("city") String city,
                     @Param("district") String district, @Param("detail") String detail);

    @Delete("DELETE FROM user_address WHERE id = #{id} AND user_id = #{userId}")
    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    @Select("<script>" +
            "SELECT * FROM user_address WHERE user_id IN " +
            "<foreach item='userId' collection='userIds' open='(' separator=',' close=')'>" +
            "#{userId}" +
            "</foreach>" +
            "</script>")
    List<UserAddress> batchSelectByUserIds(@Param("userIds") List<Long> userIds);
}
