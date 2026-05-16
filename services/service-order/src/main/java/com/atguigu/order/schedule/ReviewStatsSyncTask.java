package com.atguigu.order.schedule;

import com.atguigu.order.service.OrderReviewService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 评价统计定时任务
 * 定期同步Redis缓存中的评价统计数据到数据库
 */
@Component
@RequiredArgsConstructor
public class ReviewStatsSyncTask {

    private static final Logger log = LoggerFactory.getLogger(ReviewStatsSyncTask.class);

    private final OrderReviewService orderReviewService;

    /**
     * 每小时同步一次评价统计
     * cron表达式: 0 0 * * * ? 表示每小时整点执行
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void syncReviewStats() {
        log.info("开始执行评价统计同步任务...");
        long startTime = System.currentTimeMillis();

        try {
            orderReviewService.syncAllReviewStats();
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("评价统计同步任务完成，耗时: {}ms", elapsed);
        } catch (Exception e) {
            log.error("评价统计同步任务失败", e);
        }
    }
}
