package com.atguigu.product.service.impl;

import com.atguigu.common.cache.BloomFilterService;
import com.atguigu.common.cache.CacheService;
import com.atguigu.product.bean.Product;
import com.atguigu.product.mapper.ProductMapper;
import com.atguigu.product.service.ProductService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private static final String PRODUCT_CACHE_KEY_PREFIX = "product:";
    private static final String NULL_CACHE_VALUE = "NULL";

    private final ProductMapper productMapper;

    private final CacheService cacheService;

    private final BloomFilterService bloomFilterService;

    @Override
    public Product getProductById(Long productId) {
        if (productId == null) {
            return null;
        }

        // 1. 布隆过滤器检查：快速判断商品是否可能存在
        if (!bloomFilterService.mightContainProduct(productId)) {
            log.debug("布隆过滤器判断商品不存在，productId={}", productId);
            return null;
        }

        // 2. 缓存查询
        String cacheKey = PRODUCT_CACHE_KEY_PREFIX + productId;
        
        // 检查空值缓存，防止缓存穿透
        if (cacheService.isNullCache(cacheKey)) {
            log.debug("命中空值缓存，productId={}", productId);
            return null;
        }

        // 尝试从缓存获取
        Object cachedValue = cacheService.getCache(cacheKey);
        if (cachedValue != null) {
            if (cachedValue instanceof Product) {
                log.debug("命中商品缓存，productId={}", productId);
                return (Product) cachedValue;
            }
        }

        // 3. 数据库查询
        Product product = productMapper.selectById(productId);

        // 4. 缓存结果
        if (product != null) {
            cacheService.setCache(cacheKey, product);
            log.debug("商品已缓存，productId={}", productId);
        } else {
            // 设置空值缓存，防止缓存穿透（有效期60秒）
            cacheService.setNullCache(cacheKey);
            log.debug("商品不存在，设置空值缓存，productId={}", productId);
        }

        return product;
    }

    @Override
    public List<Product> batchGetProducts(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<Product> result = new ArrayList<>();
        List<Long> missedIds = new ArrayList<>();

        for (Long id : ids) {
            if (id == null) {
                continue;
            }

            String cacheKey = PRODUCT_CACHE_KEY_PREFIX + id;

            if (cacheService.isNullCache(cacheKey)) {
                continue;
            }

            Object cachedValue = cacheService.getCache(cacheKey);
            if (cachedValue instanceof Product) {
                result.add((Product) cachedValue);
            } else {
                missedIds.add(id);
            }
        }

        if (!missedIds.isEmpty()) {
            List<Product> dbProducts = productMapper.selectBatchIds(missedIds);
            for (Product product : dbProducts) {
                String cacheKey = PRODUCT_CACHE_KEY_PREFIX + product.getId();
                cacheService.setCache(cacheKey, product);
                result.add(product);
            }

            Set<Long> foundIds = dbProducts.stream()
                    .map(Product::getId)
                    .collect(Collectors.toSet());
            for (Long missedId : missedIds) {
                if (!foundIds.contains(missedId)) {
                    String cacheKey = PRODUCT_CACHE_KEY_PREFIX + missedId;
                    cacheService.setNullCache(cacheKey);
                }
            }
        }

        return result;
    }

    @Override
    public List<Product> listAllProducts() {
        return productMapper.selectList(null);
    }

    @Override
    public List<Product> listByMerchant(Long merchantId) {
        return productMapper.selectByMerchantId(merchantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Product saveOrUpdate(Product product) {
        if (product.getId() != null) {
            productMapper.updateProduct(product);
            final Long productIdRef = product.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheService.deleteWithDoubleRemoval(PRODUCT_CACHE_KEY_PREFIX + productIdRef);
                }
            });
        } else {
            productMapper.insertProduct(product);
            final Long newProductIdRef = product.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (newProductIdRef != null) {
                        bloomFilterService.addProduct(newProductIdRef);
                    }
                }
            });
        }
        return product;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteProduct(Long productId) {
        int deleted = productMapper.deleteById(productId);
        if (deleted > 0) {
            final Long productIdRef = productId;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheService.deleteWithDoubleRemoval(PRODUCT_CACHE_KEY_PREFIX + productIdRef);
                }
            });
        }
        return deleted > 0;
    }

    @Override
    public List<Product> searchProducts(String keyword, String category, String sortBy, boolean ascending) {
        Long categoryId = null;
        if (category != null && !category.isEmpty()) {
            try {
                categoryId = Long.parseLong(category);
            } catch (NumberFormatException e) {
                return new ArrayList<>();
            }
        }

        Page<Product> page = new Page<>(1, 100);
        IPage<Product> result = productMapper.searchProducts(page, keyword, categoryId);
        List<Product> products = result.getRecords();

        if (sortBy != null && !sortBy.isEmpty()) {
            Comparator<Product> comparator;
            switch (sortBy.toLowerCase()) {
                case "price":
                    comparator = Comparator.comparing(Product::getPrice);
                    break;
                case "stock":
                    comparator = Comparator.comparing(Product::getNum);
                    break;
                case "sales":
                    comparator = Comparator.comparing(Product::getSales);
                    break;
                case "name":
                    comparator = Comparator.comparing(Product::getName);
                    break;
                default:
                    comparator = Comparator.comparing(Product::getId);
            }
            if (!ascending) {
                comparator = comparator.reversed();
            }
            products = products.stream().sorted(comparator).collect(Collectors.toList());
        }

        return products;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean decreaseStock(Long productId, Integer quantity) {
        int result = productMapper.decreaseStock(productId, quantity);
        if (result > 0) {
            final Long productIdRef = productId;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheService.deleteWithDoubleRemoval(PRODUCT_CACHE_KEY_PREFIX + productIdRef);
                }
            });
            return true;
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean increaseStock(Long productId, Integer quantity) {
        int result = productMapper.increaseStock(productId, quantity);
        if (result > 0) {
            final Long productIdRef = productId;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheService.deleteWithDoubleRemoval(PRODUCT_CACHE_KEY_PREFIX + productIdRef);
                }
            });
            return true;
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchIncreaseStock(List<ProductMapper.StockItem> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }
        int result = productMapper.batchIncreaseStock(items);
        if (result > 0) {
            final List<ProductMapper.StockItem> itemsRef = items;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (ProductMapper.StockItem item : itemsRef) {
                        cacheService.deleteWithDoubleRemoval(PRODUCT_CACHE_KEY_PREFIX + item.getProductId());
                    }
                }
            });
            return true;
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePrice(Long productId, BigDecimal price) {
        int result = productMapper.updatePrice(productId, price);
        if (result > 0) {
            final Long productIdRef = productId;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheService.deleteWithDoubleRemoval(PRODUCT_CACHE_KEY_PREFIX + productIdRef);
                }
            });
        }
        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 30)
    public boolean updateEnabled(Long productId, Boolean enabled) {
        int result = productMapper.updateEnabled(productId, enabled);
        if (result > 0) {
            final Long productIdRef = productId;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheService.deleteWithDoubleRemoval(PRODUCT_CACHE_KEY_PREFIX + productIdRef);
                }
            });
        }
        return result > 0;
    }

    @Override
    public IPage<Product> listProductsByPage(int pageNum, int pageSize) {
        Page<Product> page = new Page<>(pageNum, pageSize);
        return productMapper.selectPageAll(page);
    }

    @Override
    public IPage<Product> listProductsByMerchant(Long merchantId, int pageNum, int pageSize) {
        Page<Product> page = new Page<>(pageNum, pageSize);
        return productMapper.selectPageByMerchantId(page, merchantId);
    }

    @Override
    public List<Product> listInStock() {
        return productMapper.selectInStock();
    }

    @Override
    public List<Product> listHotProducts(int limit) {
        return productMapper.selectHotProducts(limit);
    }

    @Override
    public List<Product> listByCategory(Long categoryId) {
        return productMapper.selectByCategoryId(categoryId);
    }

    @Override
    public List<Product> listByKeyword(String keyword) {
        return productMapper.selectByKeyword(keyword);
    }

    @Override
    public List<Product> listByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return productMapper.selectByPriceRange(minPrice, maxPrice);
    }
}
