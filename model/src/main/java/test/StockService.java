package test;

public interface StockService {
    //    增加商品库存
    //    @param  productId 商品ID
    // @param amount 增加数量，必须大于0
    //
    void addStock(String productId, int amount);
    // @param productId 商品ID
    // @param amount 扣减是否成功 (库存不足返回false)
    //
    boolean deductStock(String productId, int amount);
    //
    // 查询商品当前库存
    // @param productId 商品ID
    // @return 库存数量
    //
    int getStock(String productId);
    //
     // 获取当前管理的商品总数
    // @return 商品数量
    //
    int getProductCount();
}
