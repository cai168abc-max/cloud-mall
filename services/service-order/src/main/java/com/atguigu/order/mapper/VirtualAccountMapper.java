package com.atguigu.order.mapper;

import com.atguigu.order.bean.VirtualAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 虚拟账户Mapper接口
 */
@Mapper
public interface VirtualAccountMapper extends BaseMapper<VirtualAccount> {

    /**
     * 根据用户ID查询账户
     * @param userId 用户ID
     * @return 虚拟账户
     */
    @Select("SELECT * FROM virtual_account WHERE user_id = #{userId}")
    VirtualAccount selectByUserId(@Param("userId") Long userId);

    /**
     * 乐观锁更新余额（充值）
     * @param id 账户ID
     * @param amount 充值金额
     * @param version 当前版本号
     * @return 更新行数
     */
    @Update("UPDATE virtual_account SET balance = balance + #{amount}, version = version + 1, update_time = NOW() "
            + "WHERE id = #{id} AND version = #{version}")
    int updateBalanceWithVersion(@Param("id") Long id, @Param("amount") BigDecimal amount, @Param("version") Integer version);

    /**
     * 乐观锁扣款（支付）
     * 同时检查余额是否充足
     * @param id 账户ID
     * @param amount 扣款金额
     * @param version 当前版本号
     * @return 更新行数
     */
    @Update("UPDATE virtual_account SET balance = balance - #{amount}, version = version + 1, update_time = NOW() "
            + "WHERE id = #{id} AND version = #{version} AND balance >= #{amount}")
    int deductBalanceWithVersion(@Param("id") Long id, @Param("amount") BigDecimal amount, @Param("version") Integer version);

    /**
     * 冻结金额
     * @param id 账户ID
     * @param amount 冻结金额
     * @param version 当前版本号
     * @return 更新行数
     */
    @Update("UPDATE virtual_account SET balance = balance - #{amount}, frozen_amount = frozen_amount + #{amount}, "
            + "version = version + 1, update_time = NOW() WHERE id = #{id} AND version = #{version} AND balance >= #{amount}")
    int freezeBalanceWithVersion(@Param("id") Long id, @Param("amount") BigDecimal amount, @Param("version") Integer version);

    /**
     * 解冻金额
     * @param id 账户ID
     * @param amount 解冻金额
     * @param version 当前版本号
     * @return 更新行数
     */
    @Update("UPDATE virtual_account SET balance = balance + #{amount}, frozen_amount = frozen_amount - #{amount}, "
            + "version = version + 1, update_time = NOW() WHERE id = #{id} AND version = #{version} AND frozen_amount >= #{amount}")
    int unfreezeBalanceWithVersion(@Param("id") Long id, @Param("amount") BigDecimal amount, @Param("version") Integer version);

    /**
     * 更新账户状态
     * @param id 账户ID
     * @param status 状态
     * @return 更新行数
     */
    @Update("UPDATE virtual_account SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
