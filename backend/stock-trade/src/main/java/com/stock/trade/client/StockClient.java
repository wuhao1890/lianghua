package com.stock.trade.client;

import com.stock.trade.dto.StockDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "stock-stock", fallback = StockClientFallback.class)
public interface StockClient {

    /**
     * 获取股票实时行情
     */
    @GetMapping("/api/stock/realtime/{code}")
    StockDTO getRealtimeQuote(@PathVariable("code") String code);
}
