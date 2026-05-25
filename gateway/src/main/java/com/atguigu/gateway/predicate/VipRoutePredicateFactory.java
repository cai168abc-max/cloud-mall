package com.atguigu.gateway.predicate;

import jakarta.validation.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.GatewayPredicate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * VIP用户路由断言工厂
 * 功能：根据请求查询参数判断是否路由到VIP专属服务
 * 使用方法：
 * 在application.yml中配置：
 * predicates:
 *   - name: Vip
 *     args:
 *       param: vip
 *       value: true
 * 或者使用快捷方式：
 * predicates:
 *   - Vip=vip,true
 * 当请求包含?vip=true参数时，会匹配此路由
 */
@Component
public class VipRoutePredicateFactory extends AbstractRoutePredicateFactory<VipRoutePredicateFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(VipRoutePredicateFactory.class);

    public VipRoutePredicateFactory() {
        super(Config.class);
    }

    /**
     * 配置快捷方式字段顺序
     * 对应配置：Vip=param,value
     */
    @Override
    @NonNull
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("param", "value");
    }

    /**
     * 应用断言配置
     * @param config 断言配置
     * @return 断言对象
     */
    @Override
    @NonNull
    public Predicate<ServerWebExchange> apply(@NonNull final Config config) {
        // 防御性检查：确保配置参数不为空
        if (!StringUtils.hasText(config.getParam()) || !StringUtils.hasText(config.getValue())) {
            log.warn("VIP路由断言配置参数不完整，param: {}, value: {}", config.getParam(), config.getValue());
            // 参数不完整时，永远不匹配此路由
            return exchange -> false;
        }

        log.debug("VIP路由断言初始化完成，参数: {}, 期望值: {}", config.getParam(), config.getValue());

        // 直接返回GatewayPredicate实现，避免不安全的类型转换
        return new GatewayPredicate() {
            @Override
            public boolean test(@NonNull final ServerWebExchange exchange) {
                ServerHttpRequest request = exchange.getRequest();
                String actualValue = request.getQueryParams().getFirst(config.getParam());

                boolean matches = StringUtils.hasText(actualValue) && actualValue.equals(config.getValue());

                if (log.isTraceEnabled()) {
                    log.trace("VIP路由断言匹配结果: {}, 参数: {}, 实际值: {}, 期望值: {}",
                            matches, config.getParam(), actualValue, config.getValue());
                }

                return matches;
            }

            @Override
            public String toString() {
                return String.format("Vip: param=%s, value=%s", config.getParam(), config.getValue());
            }
        };
    }

    /**
     * VIP路由断言配置类
     */
    @Validated
    public static class Config {
        /**
         * 要检查的查询参数名称
         */
        @NotEmpty(message = "查询参数名称不能为空")
        private String param;

        /**
         * 参数的期望值
         */
        @NotEmpty(message = "参数期望值不能为空")
        private String value;

        public String getParam() {
            return param;
        }

        public void setParam(String param) {
            this.param = param;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "Config{" +
                    "param='" + param + '\'' +
                    ", value='" + value + '\'' +
                    '}';
        }
    }
}