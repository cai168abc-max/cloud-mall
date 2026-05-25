package com.atguigu.order.service.impl;

import com.atguigu.order.bean.Cart;
import com.atguigu.order.bean.CartItem;
import com.atguigu.order.feign.ProductFeign;
import com.atguigu.order.service.CartService;
import com.atguigu.product.bean.Product;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 购物车服务实现类（基于Redis实现）
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private static final Logger log = LoggerFactory.getLogger(CartServiceImpl.class);

    /**
     * Redis键前缀
     */
    private static final String CART_KEY_PREFIX = "cart:";
    
    /**
     * 热点商品缓存键前缀
     */
    private static final String HOT_PRODUCT_CACHE_PREFIX = "hot_product:";
    
    /**
     * 热点商品缓存过期时间（秒）
     */
    private static final long HOT_PRODUCT_CACHE_EXPIRE_TIME = 300; // 5分钟

    private final ProductFeign productFeign;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;

    private static final String CART_LOCK_PREFIX = "cart:lock:";

    @Override
    public Cart addItemToCart(Long userId, Long productId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("商品数量必须大于0");
        }

        // 性能优化：使用Redisson分布式锁替代简单SETNX，支持看门狗自动续期
        String lockKey = CART_LOCK_PREFIX + userId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁，等待3秒，锁自动过期时间由看门狗管理（默认30秒）
            boolean acquired = lock.tryLock(3, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("系统繁忙，请稍后重试");
            }

            Product product = getProductFromCache(productId);
            if (product == null) {
                throw new IllegalArgumentException("商品不存在");
            }

            if (product.getNum() < quantity) {
                throw new IllegalArgumentException("商品库存不足，当前库存：" + product.getNum());
            }

            Cart cart = getCartInternal(userId);

            Optional<CartItem> existingItemOpt = cart.getItems().stream()
                    .filter(item -> Objects.equals(item.getProductId(), productId))
                    .findFirst();

            if (existingItemOpt.isPresent()) {
                CartItem existingItem = existingItemOpt.get();
                existingItem.setQuantity(existingItem.getQuantity() + quantity);
            } else {
                CartItem newItem = new CartItem();
                newItem.setProductId(productId);
                newItem.setProductName(product.getName());
                newItem.setPrice(product.getPrice());
                newItem.setQuantity(quantity);
                newItem.setCategoryId(product.getCategoryId());
                newItem.setChecked(true);
                cart.addItem(newItem);
            }

            cart.calculateTotal();
            saveCartToRedis(cart);
            return cart;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取购物车锁被中断, userId={}", userId, e);
            throw new IllegalStateException("系统繁忙，请稍后重试");
        } finally {
            // Redisson会自动释放锁（看门狗机制）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Cart removeItemFromCart(Long userId, Long productId) {
        String lockKey = CART_LOCK_PREFIX + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(3, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("系统繁忙，请稍后重试");
            }
            Cart cart = getCartInternal(userId);
            if (cart == null || cart.isItemsEmpty()) {
                return cart;
            }
            cart.removeItem(productId);
            cart.calculateTotal();
            saveCartToRedis(cart);
            return cart;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("系统繁忙，请稍后重试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Cart updateItemQuantity(Long userId, Long productId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("商品数量必须大于0");
        }
        String lockKey = CART_LOCK_PREFIX + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(3, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("系统繁忙，请稍后重试");
            }
            Cart cart = getCartInternal(userId);
            if (cart == null || cart.isItemsEmpty()) {
                return cart;
            }
            if (cart.updateItemQuantity(productId, quantity)) {
                Product product = getProductFromCache(productId);
                if (product != null && product.getNum() < quantity) {
                    throw new IllegalArgumentException("商品库存不足，当前库存：" + product.getNum());
                }
                cart.calculateTotal();
                saveCartToRedis(cart);
            }
            return cart;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("系统繁忙，请稍后重试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Cart updateItemChecked(Long userId, Long productId, Boolean checked) {
        String lockKey = CART_LOCK_PREFIX + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(3, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("系统繁忙，请稍后重试");
            }
            Cart cart = getCartInternal(userId);
            if (cart == null || cart.isItemsEmpty()) {
                return cart;
            }
            if (cart.updateItemChecked(productId, checked)) {
                cart.calculateTotal();
                saveCartToRedis(cart);
            }
            return cart;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("系统繁忙，请稍后重试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Cart updateAllItemsChecked(Long userId, Boolean checked) {
        String lockKey = CART_LOCK_PREFIX + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(3, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("系统繁忙，请稍后重试");
            }
            Cart cart = getCartInternal(userId);
            if (cart == null || cart.isItemsEmpty()) {
                return cart;
            }
            cart.updateAllChecked(checked);
            cart.calculateTotal();
            saveCartToRedis(cart);
            return cart;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("系统繁忙，请稍后重试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Cart clearCart(Long userId) {
        String lockKey = CART_LOCK_PREFIX + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(3, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("系统繁忙，请稍后重试");
            }
            redisTemplate.delete(CART_KEY_PREFIX + userId);
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.calculateTotal();
            return cart;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("系统繁忙，请稍后重试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Cart getCart(Long userId) {
        return getCartInternal(userId);
    }

    @Override
    public List<CartItem> getCheckedItems(Long userId) {
        Cart cart = getCartInternal(userId);
        if (cart == null) {
            return new ArrayList<>();
        }

        // 返回选中的商品项
        return cart.getItems().stream()
                .filter(CartItem::getChecked)
                .collect(Collectors.toList());
    }

    @Override
    public Integer getCartItemCount(Long userId) {
        Cart cart = getCartInternal(userId);
        if (cart == null) {
            return 0;
        }

        // 返回购物车中所有商品的总数量
        return cart.getItems().stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }

    /**
     * 从Redis获取购物车，如果不存在则创建新的
     */
    @SuppressWarnings("unchecked")
    private Cart getCartInternal(Long userId) {
        String key = CART_KEY_PREFIX + userId;
        Object value = redisTemplate.opsForValue().get(key);
        
        if (value instanceof Cart) {
            return (Cart) value;
        } else if (value instanceof List<?>) {
            // 兼容旧格式（直接存储List<CartItem>）
            List<CartItem> items = (List<CartItem>) value;
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setItems(items);
            cart.calculateTotal();
            // 转换为新格式并保存
            saveCartToRedis(cart);
            return cart;
        } else {
            // 创建新购物车
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setItems(new ArrayList<>());
            cart.calculateTotal();
            return cart;
        }
    }

    /**
     * 从热点商品缓存或商品服务获取商品信息
     */
    private Product getProductFromCache(Long productId) {
        // 先从热点商品缓存获取
        String cacheKey = HOT_PRODUCT_CACHE_PREFIX + productId;
        Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
        
        if (product != null) {
            return product;
        }
        
        // 缓存不存在，从商品服务获取
        product = productFeign.getProductById(productId);
        if (product != null) {
            // 存入热点商品缓存，设置过期时间
            redisTemplate.opsForValue().set(cacheKey, product, HOT_PRODUCT_CACHE_EXPIRE_TIME, java.util.concurrent.TimeUnit.SECONDS);
        }
        
        return product;
    }
    
    /**
     * 将购物车保存到Redis，设置过期时间为7天
     */
    private void saveCartToRedis(Cart cart) {
        String key = CART_KEY_PREFIX + cart.getUserId();
        // 设置购物车过期时间为7天（604800秒）
        redisTemplate.opsForValue().set(key, cart, 604800, java.util.concurrent.TimeUnit.SECONDS);
    }
}
