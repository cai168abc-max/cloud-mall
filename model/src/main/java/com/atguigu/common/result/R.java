package com.atguigu.common.result;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Data
public class R {
    private Integer code;
    private String msg;
    private Object data;
    private String requestId;
    private String timestamp;
    private String errorDetails;

    /**
     * 是否为生产环境（默认false，生产环境应设置为true）
     * 可通过R.setProductionMode(true)设置
     */
    private static boolean productionMode = false;

    public R() {
        this.requestId = UUID.randomUUID().toString().replace("-", "");
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 设置生产环境模式
     * @param mode true表示生产环境，false表示非生产环境
     */
    public static void setProductionMode(boolean mode) {
        productionMode = mode;
    }

    /**
     * 获取当前是否为生产环境模式
     * @return true表示生产环境
     */
    public static boolean isProductionMode() {
        return productionMode;
    }

    public static R ok() {
        R r = new R();
        r.setCode(200);
        r.setMsg("操作成功");
        return r;
    }

    public static R ok(Object data) {
        R r = new R();
        r.setCode(200);
        r.setMsg("操作成功");
        r.setData(data);
        return r;
    }

    public static R ok(String msg, Object data) {
        R r = new R();
        r.setCode(200);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }

    public static R error() {
        R r = new R();
        r.setCode(500);
        r.setMsg("系统异常，请稍后重试");
        return r;
    }

    public static R error(String msg) {
        R r = new R();
        r.setCode(500);
        r.setMsg(msg);
        return r;
    }

    public static R error(Integer code, String msg) {
        R r = new R();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }

    public static R error(Integer code, String msg, String errorDetails) {
        R r = new R();
        r.setCode(code);
        r.setMsg(msg);
        // 生产环境不返回详细错误信息
        if (!productionMode) {
            r.setErrorDetails(errorDetails);
        }
        return r;
    }

    public static R badRequest(String msg) {
        return error(400, msg);
    }

    public static R unauthorized(String msg) {
        return error(401, msg);
    }

    public static R forbidden(String msg) {
        return error(403, msg);
    }

    public static R notFound(String msg) {
        return error(404, msg);
    }

    public static R methodNotAllowed(String msg) {
        return error(405, msg);
    }

    public static R conflict(String msg) {
        return error(409, msg);
    }

    public static R unprocessableEntity(String msg) {
        return error(422, msg);
    }

    public static R tooManyRequests(String msg) {
        return error(429, msg);
    }

    public static R internalServerError(String msg) {
        return error(500, msg);
    }

    public static R serviceUnavailable(String msg) {
        return error(503, msg);
    }
}
