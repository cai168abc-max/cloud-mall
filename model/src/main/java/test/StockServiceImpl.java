package test;

import java.util.HashMap;
import java.util.Map;

public class StockServiceImpl implements StockService {
    private final Map<String, Integer> stockMap = new HashMap<>();
    @Override
    public void addStock(final String productId, final int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("增加数量必须大于0");
        }
        stockMap.merge(productId, amount, Integer::sum);
    }

    @Override
    public boolean deductStock(final String productId, final int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("扣减数量必须大于0");
        }
        final Integer currentStock = stockMap.get(productId);
        if (currentStock == null || currentStock < amount) {
            return false;
        } else {
            stockMap.put(productId, currentStock - amount);
            return true;
        }
    }

    @Override
    public int getStock(final String productId) {
        return 0;
    }

    @Override
    public int getProductCount() {
        return 0;
    }

}
