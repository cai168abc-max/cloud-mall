package com.atguigu.order.exception;

import com.atguigu.common.result.R;
import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;

/**
 * Sentinel统一阻塞异常处理器
 * 处理限流、熔断、系统保护等场景
 */
@Component
public class SentinelBlockHandler implements BlockExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(SentinelBlockHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String resourceName, BlockException e) throws Exception {
        log.warn("Sentinel阻塞: resource={}, exception={}", resourceName, e.getClass().getSimpleName());
        
        httpServletResponse.setContentType("application/json;charset=utf-8");
        httpServletResponse.setStatus(429);
        
        String message = getBlockMessage(e);
        R error = R.tooManyRequests(message);
        
        PrintWriter writer = httpServletResponse.getWriter();
        String json = objectMapper.writeValueAsString(error);
        writer.write(json);
        writer.flush();
        writer.close();
    }
    
    /**
     * 根据异常类型获取对应的提示信息
     * @param e BlockException
     * @return 提示信息
     */
    private String getBlockMessage(BlockException e) {
        if (e instanceof FlowException) {
            return "系统繁忙，请稍后重试";
        } else if (e instanceof DegradeException) {
            return "服务暂时不可用，请稍后重试";
        } else if (e instanceof ParamFlowException) {
            return "访问频率过高，请稍后重试";
        } else if (e instanceof SystemBlockException) {
            return "系统负载过高，请稍后重试";
        } else if (e instanceof AuthorityException) {
            return "访问权限不足";
        } else {
            return "请求被限制，请稍后重试";
        }
    }
}
