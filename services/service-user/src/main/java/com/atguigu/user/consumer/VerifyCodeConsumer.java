package com.atguigu.user.consumer;

import com.atguigu.common.mq.VerifyCodeMessage;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
    topic = "verify-code-topic",
    consumerGroup = "verify-code-consumer-group"
)
public class VerifyCodeConsumer implements RocketMQListener<VerifyCodeMessage> {

    private static final Logger log = LoggerFactory.getLogger(VerifyCodeConsumer.class);

    @Override
    public void onMessage(VerifyCodeMessage message) {
        log.info("收到验证码消息: account={}, type={}", message.getAccount(), message.getType());

        if ("SMS".equals(message.getType())) {
            sendSMS(message.getAccount(), message.getCode());
        } else if ("EMAIL".equals(message.getType())) {
            sendEmail(message.getAccount(), message.getCode());
        } else {
            log.warn("未知的验证码类型: {}", message.getType());
        }
    }

    private void sendSMS(String phone, String code) {
        log.info("【模拟短信】发送验证码到 {}: {}", phone, code);
    }

    private void sendEmail(String email, String code) {
        log.info("【模拟邮件】发送验证码到 {}: {}", email, code);
    }
}
