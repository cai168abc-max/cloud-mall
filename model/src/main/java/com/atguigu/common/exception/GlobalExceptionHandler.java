package com.atguigu.common.exception;

import com.atguigu.common.result.R;
import com.atguigu.common.utils.SensitiveDataMasker;
import feign.FeignException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理各类异常，返回标准化响应
 * 
 * <p>此异常处理器应被所有微服务共享，确保整个系统的异常处理一致性。</p>
 * 
 * <p>使用方式：各服务模块只需依赖model模块，无需再定义自己的GlobalExceptionHandler。</p>
 * 
 * @author Backend Architect
 * @since 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    /**
     * 获取当前请求的traceId
     */
    private String getTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : "N/A";
    }

    /**
     * 脱敏异常堆栈信息
     */
    private String maskStackTrace(Throwable e) {
        if (e == null) {
            return null;
        }
        String stackTrace = getStackTraceString(e);
        return SensitiveDataMasker.maskSensitiveInfo(stackTrace);
    }

    private String getStackTraceString(Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.toString()).append("\n");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
            if (sb.length() > 1000) {
                sb.append("\t...(truncated)\n");
                break;
            }
        }
        return sb.toString();
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(ValidationException.class)
    public R handleValidationException(ValidationException e) {
        log.warn("[traceId={}] 参数校验异常: {}", getTraceId(), e.getMessage());
        return R.badRequest(e.getMessage());
    }

    /**
     * 处理资源不存在异常
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public R handleResourceNotFoundException(ResourceNotFoundException e) {
        log.warn("[traceId={}] 资源不存在: resourceType={}, resourceId={}", 
                getTraceId(), e.getResourceType(), e.getResourceId());
        return R.notFound(e.getMessage());
    }

    /**
     * 处理权限不足异常
     */
    @ExceptionHandler(ForbiddenException.class)
    public R handleForbiddenException(ForbiddenException e) {
        log.warn("[traceId={}] 权限不足: resource={}, action={}", 
                getTraceId(), e.getResource(), e.getAction());
        return R.forbidden(e.getMessage());
    }

    /**
     * 处理权限拒绝异常
     */
    @ExceptionHandler(PermissionDeniedException.class)
    public R handlePermissionDeniedException(PermissionDeniedException e) {
        log.warn("[traceId={}] 权限验证失败: {}", getTraceId(), e.getPermissionDetail());
        return R.forbidden(e.getMessage());
    }

    /**
     * 处理服务不可用异常
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public R handleServiceUnavailableException(ServiceUnavailableException e) {
        log.error("[traceId={}] 服务不可用: {}", getTraceId(), e.getMessage());
        return R.serviceUnavailable(e.getMessage());
    }

    /**
     * 处理Feign调用异常
     */
    @ExceptionHandler(FeignException.class)
    public R handleFeignException(FeignException e) {
        int status = e.status();
        String message;
        
        if (status >= 500) {
            log.error("[traceId={}] Feign调用远程服务异常: status={}, url={}", 
                    getTraceId(), status, e.request().url(), e);
            message = "远程服务异常，请稍后重试";
        } else if (status == 404) {
            log.warn("[traceId={}] Feign调用资源不存在: url={}", 
                    getTraceId(), e.request().url());
            message = "请求的资源不存在";
        } else if (status == 401 || status == 403) {
            log.warn("[traceId={}] Feign调用权限不足: status={}, url={}", 
                    getTraceId(), status, e.request().url());
            message = "无权访问远程服务";
        } else {
            log.warn("[traceId={}] Feign调用异常: status={}, url={}", 
                    getTraceId(), status, e.request().url());
            message = "服务调用失败";
        }
        
        return R.error(status, message);
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public R handleBusinessException(BusinessException e) {
        int code = e.getCode();
        String message = e.getMessage();
        
        // 根据错误码记录不同级别的日志
        if (code >= 500) {
            log.error("[traceId={}] 业务异常: code={}, message={}", getTraceId(), code, message);
        } else {
            log.warn("[traceId={}] 业务异常: code={}, message={}", getTraceId(), code, message);
        }
        
        return R.error(code, message);
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "参数校验失败";
        log.warn("[traceId={}] 参数校验失败: {}", getTraceId(), msg);
        return R.badRequest(msg);
    }

    /**
     * 处理约束违规异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R handleConstraintViolationException(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        log.warn("[traceId={}] 参数校验失败: {}", getTraceId(), msg);
        return R.badRequest(msg);
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public R handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("[traceId={}] 参数错误: {}", getTraceId(), e.getMessage());
        return R.badRequest(e.getMessage());
    }

    /**
     * 处理非法状态异常
     */
    @ExceptionHandler(IllegalStateException.class)
    public R handleIllegalStateException(IllegalStateException e) {
        log.warn("[traceId={}] 业务状态错误: {}", getTraceId(), e.getMessage());
        return R.error(400, e.getMessage());
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    public R handleNullPointerException(NullPointerException e) {
        log.error("[traceId={}] 空指针异常", getTraceId(), e);
        return R.internalServerError("系统繁忙，请稍后重试");
    }

    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Throwable.class)
    public R handleException(Throwable e) {
        log.error("[traceId={}] 系统异常: {}", getTraceId(), e.getMessage(), e);
        // 非生产环境返回脱敏后的异常信息
        if (!"prod".equals(activeProfile)) {
            return R.error(500, "系统异常，请稍后重试", maskStackTrace(e));
        }
        return R.internalServerError("系统繁忙，请稍后重试");
    }
}
