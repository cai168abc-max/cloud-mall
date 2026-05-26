package com.atguigu.user.controller;

import com.atguigu.common.bean.UserAccount;
import com.atguigu.common.bean.UserInfo;
import com.atguigu.common.result.R;
import com.atguigu.user.service.UserAuthService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api/user")
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class InternalUserController {

    private final UserAuthService userAuthService;

    @GetMapping("/{userId}")
    public R getUserById(@PathVariable("userId") Long userId) {
        UserAccount user = userAuthService.getUserById(userId);
        if (user == null) {
            return R.error(404, "用户不存在");
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setId(user.getId());
        userInfo.setNickName(user.getNickName());
        userInfo.setRole(user.getRole());
        userInfo.setEnabled(user.getEnabled());
        userInfo.setVerified(user.getVerified());
        return R.ok("获取用户信息成功", userInfo);
    }
}
