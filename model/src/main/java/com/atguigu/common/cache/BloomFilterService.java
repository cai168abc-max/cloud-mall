package com.atguigu.common.cache;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BloomFilterService {

    private static final Logger log = LoggerFactory.getLogger(BloomFilterService.class);

    private BloomFilter<Long> productBloomFilter;
    private BloomFilter<Long> userBloomFilter;

    @PostConstruct
    public void init() {
        productBloomFilter = BloomFilter.create(
            Funnels.longFunnel(),
            1_000_000,
            0.01
        );
        userBloomFilter = BloomFilter.create(
            Funnels.longFunnel(),
            500_000,
            0.01
        );
        log.info("布隆过滤器初始化完成");
    }

    public void loadHotProducts(List<Long> productIds) {
        for (Long productId : productIds) {
            productBloomFilter.put(productId);
        }
        log.info("热点商品ID加载完成，共{}条", productIds.size());
    }

    public void loadHotUsers(List<Long> userIds) {
        for (Long userId : userIds) {
            userBloomFilter.put(userId);
        }
        log.info("热点用户ID加载完成，共{}条", userIds.size());
    }

    public boolean mightContainProduct(Long productId) {
        return productBloomFilter.mightContain(productId);
    }

    public void addProduct(Long productId) {
        productBloomFilter.put(productId);
    }

    public boolean mightContainUser(Long userId) {
        return userBloomFilter.mightContain(userId);
    }
}
