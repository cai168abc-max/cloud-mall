package com.atguigu.user.service.impl;

import com.atguigu.common.utils.PasswordUtil;
import com.atguigu.common.utils.SensitiveDataMasker;
import com.atguigu.common.utils.JwtKeyGenerator;
import com.atguigu.common.service.RateLimitService;
import com.atguigu.common.service.VerificationCodeService;
import com.atguigu.common.bean.UserAccount;
import com.atguigu.common.bean.UserAddress;
import com.atguigu.common.bean.UserInfo;
import com.atguigu.common.enums.UserRole;
import com.atguigu.common.mq.VerifyCodeMessage;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.atguigu.user.mapper.UserAccountMapper;
import com.atguigu.user.mapper.UserAddressMapper;
import com.atguigu.user.service.UserAuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserAuthServiceImpl implements UserAuthService {

    private static final Logger log = LoggerFactory.getLogger(UserAuthServiceImpl.class);

    private final UserAccountMapper userAccountMapper;

    private final UserAddressMapper userAddressMapper;

    private final RocketMQTemplate rocketMQTemplate;

    private final RateLimitService rateLimitService;

    private final VerificationCodeService verificationCodeService;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${security.jwt.expire-seconds:3600}")
    private long expireSeconds;

    @Value("${security.jwt.issuer:cloudtry-auth}")
    private String jwtIssuer;

    @Value("${security.jwt.audience:cloudtry-api}")
    private String jwtAudience;

    @PostConstruct
    public void init() {
        String error = JwtKeyGenerator.validateSecret(jwtSecret);
        if (error != null) {
            throw new IllegalStateException(error);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserAccount register(String phoneOrEmail, String password) {
        UserAccount account = new UserAccount();
        if (phoneOrEmail.contains("@")) {
            account.setEmail(phoneOrEmail);
        } else {
            account.setPhone(phoneOrEmail);
        }
        account.setRole(UserRole.USER);
        String hash = PasswordUtil.hashPassword(password);
        account.setPasswordHash(hash);
        account.setEnabled(true);
        account.setVerified(true);
        userAccountMapper.insertUser(account);
        return account;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserAccount registerMerchant(String phoneOrEmail, String password, String merchantName) {
        UserAccount account = new UserAccount();
        if (phoneOrEmail.contains("@")) {
            account.setEmail(phoneOrEmail);
        } else {
            account.setPhone(phoneOrEmail);
        }
        account.setRole(UserRole.MERCHANT);
        account.setMerchantName(merchantName);
        account.setNickName(merchantName);
        String hash = PasswordUtil.hashPassword(password);
        account.setPasswordHash(hash);
        account.setEnabled(true);
        account.setVerified(false);
        userAccountMapper.insertUser(account);
        return account;
    }

    @Override
    public UserAccount getMerchantById(Long merchantId) {
        UserAccount account = userAccountMapper.selectById(merchantId);
        return account != null && UserRole.MERCHANT.equals(account.getRole()) ? account : null;
    }

    @Override
    public List<UserAccount> listMerchants(int page, int size) {
        Page<UserAccount> pageObj = new Page<>(page, size);
        IPage<UserAccount> result = userAccountMapper.selectByRole(pageObj, UserRole.MERCHANT.name());
        return result.getRecords();
    }

    @Override
    public List<UserAccount> listPendingMerchants(int page, int size) {
        return userAccountMapper.selectPendingMerchants();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 30)
    public void verifyMerchant(Long merchantId, boolean approved) {
        userAccountMapper.updateMerchantVerify(merchantId, approved, approved);
    }

    @Override
    public String login(String phoneOrEmail, String password) {
        if (rateLimitService.isAccountLocked(phoneOrEmail)) {
            long remaining = rateLimitService.getRemainingLockTime(phoneOrEmail);
            throw new IllegalStateException("账号已被锁定，请" + remaining + "秒后重试");
        }

        UserAccount account = userAccountMapper.selectByPhoneOrEmail(phoneOrEmail);
        if (account == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (account.getEnabled() == null || !account.getEnabled()) {
            throw new IllegalArgumentException("用户账号已被禁用");
        }
        if (UserRole.MERCHANT.equals(account.getRole())) {
            if (account.getVerified() == null || !account.getVerified()) {
                throw new IllegalArgumentException("商家账号未通过审核");
            }
        }
        if (!PasswordUtil.matches(password, account.getPasswordHash())) {
            rateLimitService.recordLoginFailure(phoneOrEmail);
            throw new IllegalArgumentException("密码错误");
        }
        rateLimitService.clearLoginFailure(phoneOrEmail);
        return generateToken(account);
    }

    @Override
    public void sendVerificationCode(String phoneOrEmail) {
        UserAccount account = userAccountMapper.selectByPhoneOrEmail(phoneOrEmail);
        if (account == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        String code = verificationCodeService.generateAndStore(phoneOrEmail);
        log.info("向 {} 发送验证码：{}", 
            SensitiveDataMasker.maskPhoneOrEmail(phoneOrEmail), 
            SensitiveDataMasker.maskCode(code));

        String type = phoneOrEmail.contains("@") ? "EMAIL" : "SMS";
        rocketMQTemplate.asyncSend("verify-code-topic",
            VerifyCodeMessage.builder()
                .account(phoneOrEmail)
                .code(code)
                .type(type)
                .build(),
            new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("验证码消息发送成功: {}", sendResult);
                }
                @Override
                public void onException(Throwable e) {
                    log.error("验证码消息发送失败", e);
                }
            });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(String phoneOrEmail, String newPassword, String verifyCode) {
        // 防止时序攻击：添加随机延迟，使响应时间不可预测
        long startTime = System.currentTimeMillis();
        
        boolean codeValid = validateVerificationCode(phoneOrEmail, verifyCode);
        
        // 查询用户（无论验证码是否正确都执行，防止通过响应时间推断）
        UserAccount account = userAccountMapper.selectByPhoneOrEmail(phoneOrEmail);
        
        // 添加随机延迟（100-300ms），使总响应时间趋于一致
        addRandomDelay(100, 300);
        
        if (!codeValid) {
            log.warn("密码重置验证码错误: {}", SensitiveDataMasker.maskPhoneOrEmail(phoneOrEmail));
            throw new IllegalArgumentException("验证码错误或已过期");
        }
        
        if (account == null) {
            log.warn("密码重置用户不存在: {}", SensitiveDataMasker.maskPhoneOrEmail(phoneOrEmail));
            throw new IllegalArgumentException("用户不存在");
        }
        
        String hash = PasswordUtil.hashPassword(newPassword);
        userAccountMapper.updatePassword(account.getId(), hash, null);
        
        log.info("密码重置成功，耗时: {}ms", System.currentTimeMillis() - startTime);
    }
    
    /**
     * 添加随机延迟，防止时序攻击
     * @param minMs 最小延迟毫秒数
     * @param maxMs 最大延迟毫秒数
     */
    private void addRandomDelay(int minMs, int maxMs) {
        try {
            int delay = minMs + new Random().nextInt(maxMs - minMs);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public UserInfo getCurrentUser(Long userId) {
        UserAccount account = userAccountMapper.selectById(userId);
        if (account == null) {
            return null;
        }
        UserInfo info = new UserInfo();
        info.setId(account.getId());
        info.setNickName(Optional.ofNullable(account.getNickName()).orElse(Optional.ofNullable(account.getEmail()).orElse(account.getPhone())));
        info.setRole(account.getRole());
        // 敏感信息脱敏处理
        info.setPhone(SensitiveDataMasker.maskPhone(account.getPhone()));
        info.setEmail(SensitiveDataMasker.maskEmail(account.getEmail()));
        info.setMerchantName(account.getMerchantName());
        info.setVerified(account.getVerified());
        info.setEnabled(account.getEnabled());
        return info;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfo updateUserInfo(UserInfo userInfo) {
        userAccountMapper.updateUserInfo(
            userInfo.getId(),
            userInfo.getNickName(),
            userInfo.getPhone(),
            userInfo.getEmail()
        );
        return getCurrentUser(userInfo.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 30)
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        UserAccount account = userAccountMapper.selectById(userId);
        if (account == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (!PasswordUtil.matches(oldPassword, account.getPasswordHash())) {
            throw new IllegalArgumentException("旧密码错误");
        }
        String hash = PasswordUtil.hashPassword(newPassword);
        userAccountMapper.updatePassword(userId, hash, null);
    }

    @Override
    public List<UserAddress> listAddresses(Long userId) {
        return userAddressMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserAddress saveOrUpdateAddress(UserAddress address) {
        if (address.getId() == null) {
            userAddressMapper.insertAddress(address);
        } else {
            userAddressMapper.updateAddress(
                address.getId(),
                address.getUserId(),
                address.getConsignee(),
                address.getPhone(),
                address.getProvince(),
                address.getCity(),
                address.getDistrict(),
                address.getDetail()
            );
        }
        return address;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 30)
    public void deleteAddress(Long userId, Long addressId) {
        userAddressMapper.deleteById(addressId, userId);
    }

    @Override
    public List<UserAccount> listAllUsers(int page, int size) {
        Page<UserAccount> pageObj = new Page<>(page, size);
        IPage<UserAccount> result = userAccountMapper.selectPageAll(pageObj);
        return result.getRecords();
    }

    @Override
    public UserAccount getUserById(Long userId) {
        return userAccountMapper.selectById(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long userId, boolean enabled) {
        userAccountMapper.updateStatus(userId, enabled);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserRole(Long userId, UserRole role) {
        userAccountMapper.updateRole(userId, role.name());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 30)
    public void deleteUser(Long userId) {
        userAccountMapper.deleteById(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<Long, Boolean> batchUpdateUserStatus(Map<Long, Boolean> userStatusMap) {
        Map<Long, Boolean> result = new HashMap<>();
        for (Map.Entry<Long, Boolean> entry : userStatusMap.entrySet()) {
            int updated = userAccountMapper.updateStatus(entry.getKey(), entry.getValue());
            result.put(entry.getKey(), updated > 0);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 30)
    public Map<Long, Boolean> batchUpdateUserRole(Map<Long, UserRole> userRoleMap) {
        Map<Long, Boolean> result = new HashMap<>();
        for (Map.Entry<Long, UserRole> entry : userRoleMap.entrySet()) {
            int updated = userAccountMapper.updateRole(entry.getKey(), entry.getValue().name());
            result.put(entry.getKey(), updated > 0);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<Long, Boolean> batchDeleteUsers(List<Long> userIds) {
        Map<Long, Boolean> result = new HashMap<>();
        for (Long userId : userIds) {
            int deleted = userAccountMapper.deleteById(userId);
            result.put(userId, deleted > 0);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 30)
    public void updateAvatarUrl(Long userId, String avatarUrl) {
        userAccountMapper.updateAvatarUrl(userId, avatarUrl);
    }

    private String generateToken(UserAccount account) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String jwtId = UUID.randomUUID().toString();
        
        return Jwts.builder()
                .setSubject(String.valueOf(account.getId()))
                .claim("nickName", Optional.ofNullable(account.getNickName()).orElse(Optional.ofNullable(account.getEmail()).orElse(account.getPhone())))
                .claim("role", account.getRole().name())
                .setId(jwtId)  // jti: JWT唯一标识符，用于token撤销等场景
                .setIssuer(jwtIssuer)  // iss: 签发者
                .setAudience(jwtAudience)  // aud: 受众
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(key)
                .compact();
    }

    private boolean validateVerificationCode(String phoneOrEmail, String code) {
        return verificationCodeService.verify(phoneOrEmail, code);
    }
}
