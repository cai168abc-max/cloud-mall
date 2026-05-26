package com.atguigu.order.controller;

import com.atguigu.common.context.UserContext;
import com.atguigu.common.result.R;
import com.atguigu.order.bean.VirtualAccount;
import com.atguigu.order.bean.VirtualAccountLog;
import com.atguigu.order.service.VirtualAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "虚拟账户管理", description = "虚拟账户充值、余额查询、交易流水等接口")
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class VirtualAccountController {

    private final VirtualAccountService virtualAccountService;

    @Operation(summary = "账户充值", description = "为虚拟账户充值")
    @PostMapping("/recharge")
    public R recharge(@RequestBody final RechargeRequest request) {
        com.atguigu.common.bean.UserInfo userInfo = UserContext.get();
        Long userId = userInfo != null ? userInfo.getId() : null;
        if (userId == null) {
            return R.error(401, "请先登录");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return R.error(400, "充值金额必须大于0");
        }

        try {
            VirtualAccountLog log = virtualAccountService.recharge(userId, request.getAmount(), request.getTransactionNo());
            return R.ok(toLogMap(log));
        } catch (Exception e) {
            return R.error(500, e.getMessage());
        }
    }

    @Operation(summary = "查询余额", description = "查询当前用户虚拟账户余额")
    @GetMapping("/balance")
    public R getBalance() {
        com.atguigu.common.bean.UserInfo userInfo = UserContext.get();
        Long userId = userInfo != null ? userInfo.getId() : null;
        if (userId == null) {
            return R.error(401, "请先登录");
        }

        try {
            BigDecimal balance = virtualAccountService.getBalance(userId);
            VirtualAccount account = virtualAccountService.getAccount(userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("balance", balance != null ? balance : BigDecimal.ZERO);
            result.put("frozenAmount", account != null && account.getFrozenAmount() != null 
                    ? account.getFrozenAmount() : BigDecimal.ZERO);
            result.put("status", account != null && account.getStatus() != null 
                    ? account.getStatus() : 1);
            return R.ok(result);
        } catch (Exception e) {
            return R.error(500, e.getMessage());
        }
    }

    @Operation(summary = "查询账户信息", description = "查询当前用户虚拟账户详细信息")
    @GetMapping("/info")
    public R getAccountInfo() {
        com.atguigu.common.bean.UserInfo userInfo = UserContext.get();
        Long userId = userInfo != null ? userInfo.getId() : null;
        if (userId == null) {
            return R.error(401, "请先登录");
        }

        try {
            VirtualAccount account = virtualAccountService.getOrCreateAccount(userId);
            return R.ok(account);
        } catch (Exception e) {
            return R.error(500, e.getMessage());
        }
    }

    @Operation(summary = "查询交易流水", description = "查询当前用户的交易流水记录")
    @GetMapping("/logs")
    public R getTransactionLogs() {
        com.atguigu.common.bean.UserInfo userInfo = UserContext.get();
        Long userId = userInfo != null ? userInfo.getId() : null;
        if (userId == null) {
            return R.error(401, "请先登录");
        }

        try {
            List<VirtualAccountLog> logs = virtualAccountService.getTransactionLogs(userId);
            return R.ok(logs);
        } catch (Exception e) {
            return R.error(500, e.getMessage());
        }
    }

    @Operation(summary = "查询订单流水", description = "根据订单ID查询交易流水")
    @GetMapping("/logs/order/{orderId}")
    public R getLogByOrderId(
            @Parameter(description = "订单ID") @PathVariable final Long orderId) {
        com.atguigu.common.bean.UserInfo userInfo = UserContext.get();
        Long userId = userInfo != null ? userInfo.getId() : null;
        if (userId == null) {
            return R.error(401, "请先登录");
        }
        try {
            VirtualAccountLog log = virtualAccountService.getLogByOrderId(orderId);
            if (log == null) {
                return R.error(404, "未找到相关流水记录");
            }
            return R.ok(log);
        } catch (Exception e) {
            return R.error(500, e.getMessage());
        }
    }

    @Operation(summary = "刷新余额缓存", description = "强制刷新余额缓存，从数据库重新加载")
    @PostMapping("/refresh")
    public R refreshBalanceCache() {
        com.atguigu.common.bean.UserInfo userInfo = UserContext.get();
        Long userId = userInfo != null ? userInfo.getId() : null;
        if (userId == null) {
            return R.error(401, "请先登录");
        }

        try {
            virtualAccountService.refreshBalanceCache(userId);
            return R.ok("刷新成功");
        } catch (Exception e) {
            return R.error(500, e.getMessage());
        }
    }

    @Operation(summary = "支付（内部接口）", description = "从虚拟账户扣款支付订单")
    @PostMapping("/internal/pay")
    public R internalPay(@RequestBody final PayRequest request) {
        try {
            VirtualAccountLog log = virtualAccountService.pay(
                    request.getUserId(), 
                    request.getAmount(), 
                    request.getOrderId(),
                    request.getTransactionNo()
            );
            return R.ok(toLogMap(log));
        } catch (Exception e) {
            return R.error(500, e.getMessage());
        }
    }

    @Operation(summary = "退款（内部接口）", description = "退款到虚拟账户")
    @PostMapping("/internal/refund")
    public R internalRefund(@RequestBody final RefundRequest request) {
        try {
            VirtualAccountLog log = virtualAccountService.refund(
                    request.getUserId(), 
                    request.getAmount(), 
                    request.getOrderId(),
                    request.getTransactionNo()
            );
            return R.ok(toLogMap(log));
        } catch (Exception e) {
            return R.error(500, e.getMessage());
        }
    }

    @Operation(summary = "查询余额（内部接口）", description = "查询指定用户虚拟账户余额")
    @GetMapping("/internal/balance/{userId}")
    public R internalGetBalance(
            @Parameter(description = "用户ID") @PathVariable final Long userId) {
        try {
            BigDecimal balance = virtualAccountService.getBalance(userId);
            return R.ok(balance);
        } catch (Exception e) {
            return R.error(500, e.getMessage());
        }
    }

    private Map<String, Object> toLogMap(final VirtualAccountLog log) {
        Map<String, Object> map = new HashMap<>();
        if (log == null) {
            return map;
        }
        
        if (log.getTransactionNo() != null) {
            map.put("transactionNo", log.getTransactionNo());
        }
        if (log.getType() != null) {
            map.put("type", log.getType());
        }
        if (log.getAmount() != null) {
            map.put("amount", log.getAmount());
        }
        if (log.getBalanceBefore() != null) {
            map.put("balanceBefore", log.getBalanceBefore());
        }
        if (log.getBalanceAfter() != null) {
            map.put("balanceAfter", log.getBalanceAfter());
        }
        if (log.getStatus() != null) {
            map.put("status", log.getStatus());
        }
        if (log.getCreateTime() != null) {
            map.put("createTime", log.getCreateTime());
        }
        if (log.getRemark() != null) {
            map.put("remark", log.getRemark());
        }
        if (log.getRelatedOrderId() != null) {
            map.put("orderId", log.getRelatedOrderId());
        }
        return map;
    }

    @lombok.Data
    public static class RechargeRequest {
        private BigDecimal amount;
        private String transactionNo;
    }

    @lombok.Data
    public static class PayRequest {
        private Long userId;
        private BigDecimal amount;
        private Long orderId;
        private String transactionNo;
    }

    @lombok.Data
    public static class RefundRequest {
        private Long userId;
        private BigDecimal amount;
        private Long orderId;
        private String transactionNo;
    }
}
