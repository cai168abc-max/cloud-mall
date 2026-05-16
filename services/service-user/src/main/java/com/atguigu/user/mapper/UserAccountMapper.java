package com.atguigu.user.mapper;

import com.atguigu.common.bean.UserAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {

    @Select("SELECT * FROM user_account WHERE (phone = #{phoneOrEmail} OR email = #{phoneOrEmail}) LIMIT 1")
    UserAccount selectByPhoneOrEmail(@Param("phoneOrEmail") String phoneOrEmail);

    @Select("SELECT * FROM user_account WHERE phone = #{phone} LIMIT 1")
    UserAccount selectByPhone(@Param("phone") String phone);

    @Select("SELECT * FROM user_account WHERE email = #{email} LIMIT 1")
    UserAccount selectByEmail(@Param("email") String email);

    @Select("SELECT * FROM user_account WHERE role = 'MERCHANT' ORDER BY create_time DESC")
    List<UserAccount> selectAllMerchants();

    @Select("SELECT * FROM user_account WHERE role = 'MERCHANT' AND verified = 0 ORDER BY create_time DESC")
    List<UserAccount> selectPendingMerchants();

    @Select("SELECT * FROM user_account WHERE role = #{role}")
    IPage<UserAccount> selectByRole(Page<UserAccount> page, @Param("role") String role);

    @Select("SELECT * FROM user_account ORDER BY create_time DESC")
    IPage<UserAccount> selectPageAll(Page<UserAccount> page);

    @Update("UPDATE user_account SET password_hash = #{passwordHash}, salt = #{salt}, update_time = NOW() WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash, @Param("salt") String salt);

    @Update("UPDATE user_account SET enabled = #{enabled}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("enabled") Boolean enabled);

    @Update("UPDATE user_account SET verified = #{verified}, enabled = #{enabled}, update_time = NOW() WHERE id = #{id}")
    int updateMerchantVerify(@Param("id") Long id, @Param("verified") Boolean verified, @Param("enabled") Boolean enabled);

    @Update("UPDATE user_account SET role = #{role}, update_time = NOW() WHERE id = #{id}")
    int updateRole(@Param("id") Long id, @Param("role") String role);

    @Update("UPDATE user_account SET nick_name = #{nickName}, phone = #{phone}, email = #{email}, update_time = NOW() WHERE id = #{id}")
    int updateUserInfo(@Param("id") Long id, @Param("nickName") String nickName, @Param("phone") String phone, @Param("email") String email);

    @Select("SELECT id, phone, email, nick_name, role, enabled, verified FROM user_account WHERE id = #{id} LIMIT 1")
    UserAccount selectUserInfoSimple(@Param("id") Long id);

    @Select("<script>" +
            "SELECT id, phone, email, nick_name, role, enabled, verified, create_time, update_time " +
            "FROM user_account WHERE id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<UserAccount> batchSelect(@Param("ids") List<Long> ids);

    @Insert("INSERT INTO user_account (phone, email, nick_name, salt, password_hash, role, merchant_name, enabled, verified, create_time, update_time) " +
            "VALUES (#{phone}, #{email}, #{nickName}, #{salt}, #{passwordHash}, #{role}, #{merchantName}, #{enabled}, #{verified}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertUser(UserAccount account);

    @Delete("DELETE FROM user_account WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Update("UPDATE user_account SET avatar_url = #{avatarUrl}, update_time = NOW() WHERE id = #{id}")
    int updateAvatarUrl(@Param("id") Long id, @Param("avatarUrl") String avatarUrl);

    @Select("SELECT * FROM user_account WHERE nick_name = #{nickName} LIMIT 1")
    UserAccount selectByNickName(@Param("nickName") String nickName);
}
