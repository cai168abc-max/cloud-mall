package com.atguigu.user.service;

import com.atguigu.common.bean.UserAccount;
import com.atguigu.common.bean.UserAddress;
import com.atguigu.common.bean.UserInfo;
import com.atguigu.common.enums.UserRole;

import java.util.List;
import java.util.Map;

public interface UserAuthService {

    UserAccount register(String phoneOrEmail, String password);

    UserAccount registerMerchant(String phoneOrEmail, String password, String merchantName);

    UserAccount getMerchantById(Long merchantId);

    List<UserAccount> listMerchants(int page, int size);
    
    /**
     * 查询待审核商家列表
     */
    List<UserAccount> listPendingMerchants(int page, int size);
    
    /**
     * 审核商家（通过或拒绝）
     */
    void verifyMerchant(Long merchantId, boolean approved);

    String login(String phoneOrEmail, String password);

    /**
     * 发送验证码
     */
    void sendVerificationCode(String phoneOrEmail);

    void resetPassword(String phoneOrEmail, String newPassword, String verifyCode);

    UserInfo getCurrentUser(Long userId);

    UserInfo updateUserInfo(UserInfo userInfo);

    void updatePassword(Long userId, String oldPassword, String newPassword);

    List<UserAddress> listAddresses(Long userId);

    UserAddress saveOrUpdateAddress(UserAddress address);

    void deleteAddress(Long userId, Long addressId);
    
    // ========== 管理员用户管理功能 ==========
    
    /**
     * 查询所有用户列表
     */
    List<UserAccount> listAllUsers(int page, int size);
    
    /**
     * 根据用户ID查询用户账号信息
     */
    UserAccount getUserById(Long userId);
    
    /**
     * 禁用/启用用户
     */
    void updateUserStatus(Long userId, boolean enabled);
    
    /**
     * 修改用户角色
     */
    void updateUserRole(Long userId, UserRole role);
    
    /**
     * 删除用户
     */
    void deleteUser(Long userId);
    
    /**
     * 批量禁用/启用用户
     */
    Map<Long, Boolean> batchUpdateUserStatus(Map<Long, Boolean> userStatusMap);
    
    /**
     * 批量修改用户角色
     */
    Map<Long, Boolean> batchUpdateUserRole(Map<Long, UserRole> userRoleMap);
    
    /**
     * 批量删除用户
     */
    Map<Long, Boolean> batchDeleteUsers(List<Long> userIds);

    /**
     * 更新用户头像URL
     */
    void updateAvatarUrl(Long userId, String avatarUrl);
}
