package com.atguigu.user.controller;

import com.atguigu.common.result.R;
import com.atguigu.common.bean.UserAccount;
import com.atguigu.common.bean.UserAddress;
import com.atguigu.common.bean.UserInfo;
import com.atguigu.common.enums.UserRole;
import com.atguigu.user.service.UserAuthService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@Validated
@RequiredArgsConstructor
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserAuthService userAuthService;

    @PostMapping("/register")
    public R register(@RequestParam("account") @NotBlank String account,
                      @RequestParam("password") @NotBlank String password) {
        userAuthService.register(account, password);
        return R.ok("注册成功", null);
    }

    @PostMapping("/registerMerchant")
    public R registerMerchant(@RequestParam("account") @NotBlank String account,
                               @RequestParam("password") @NotBlank String password,
                               @RequestParam("merchantName") @NotBlank String merchantName) {
        UserAccount merchant = userAuthService.registerMerchant(account, password, merchantName);
        return R.ok("商家注册成功，等待审核", merchant);
    }

    @PostMapping("/login")
    public R login(@RequestParam("account") @NotBlank String account,
                   @RequestParam("password") @NotBlank String password) {
        String token = userAuthService.login(account, password);
        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", token);
        return R.ok("登录成功", data);
    }

    @PostMapping("/resetPassword")
    public R resetPassword(@RequestParam("account") @NotBlank String account,
                           @RequestParam("newPassword") @NotBlank String newPassword,
                           @RequestParam("verificationCode") @NotBlank String verificationCode) {
        userAuthService.resetPassword(account, newPassword, verificationCode);
        return R.ok("密码重置成功", null);
    }

    @PostMapping("/updatePassword")
    public R updatePassword(@RequestParam("oldPassword") @NotBlank String oldPassword,
                           @RequestParam("newPassword") @NotBlank String newPassword) {
        UserInfo currentUser = userAuthService.getCurrentUser(null);
        if (currentUser == null) {
            return R.error(401, "请先登录");
        }
        userAuthService.updatePassword(currentUser.getId(), oldPassword, newPassword);
        return R.ok("密码修改成功", null);
    }

    @PostMapping("/sendVerificationCode")
    public R sendVerificationCode(@RequestParam("phoneOrEmail") @NotBlank String phoneOrEmail) {
        userAuthService.sendVerificationCode(phoneOrEmail);
        return R.ok("验证码发送成功", null);
    }

    @GetMapping("/me")
    public R getCurrentUser() {
        UserInfo user = userAuthService.getCurrentUser(null);
        return R.ok("获取用户信息成功", user);
    }

    @GetMapping("/address")
    public R getAddresses() {
        UserInfo currentUser = userAuthService.getCurrentUser(null);
        if (currentUser == null) {
            return R.error(401, "请先登录");
        }
        List<UserAddress> addresses = userAuthService.listAddresses(currentUser.getId());
        return R.ok("获取地址成功", addresses);
    }

    @PostMapping("/address")
    public R addAddress(@RequestBody UserAddress address) {
        UserInfo currentUser = userAuthService.getCurrentUser(null);
        if (currentUser == null) {
            return R.error(401, "请先登录");
        }
        address.setUserId(currentUser.getId());
        userAuthService.saveOrUpdateAddress(address);
        return R.ok("添加地址成功", null);
    }

    @PutMapping("/address/{id}")
    public R updateAddress(@PathVariable("id") Long id, @RequestBody UserAddress address) {
        UserInfo currentUser = userAuthService.getCurrentUser(null);
        if (currentUser == null) {
            return R.error(401, "请先登录");
        }
        address.setId(id);
        address.setUserId(currentUser.getId());
        userAuthService.saveOrUpdateAddress(address);
        return R.ok("修改地址成功", null);
    }

    @DeleteMapping("/address/{id}")
    public R deleteAddress(@PathVariable("id") Long id) {
        UserInfo currentUser = userAuthService.getCurrentUser(null);
        if (currentUser == null) {
            return R.error(401, "请先登录");
        }
        userAuthService.deleteAddress(currentUser.getId(), id);
        return R.ok("删除地址成功", null);
    }
}
