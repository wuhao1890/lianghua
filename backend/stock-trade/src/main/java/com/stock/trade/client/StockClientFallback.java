package com.stock.trade.client;

import com.stock.trade.dto.StockDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class StockClientFallback implements StockClient {

    @Override
    public StockDTO getRealtimeQuote(String code) {
        log.warn("Feign调用stock服务获取行情失败, 使用降级数据, code={}", code);
        // 返回一个默认的降级数据
        StockDTO dto = new StockDTO();
        dto.setCode(code);
        dto.setName(code);
        dto.setMarket("A_STOCK");
        dto.setCurrentPrice(new BigDecimal("0"));
        dto.setChangePercent(new BigDecimal("0"));
        dto.setVolume(0L);
        dto.setTurnover(new BigDecimal("0"));
        dto.setHighPrice(new BigDecimal("0"));
        dto.setLowPrice(new BigDecimal("0"));
        dto.setOpenPrice(new BigDecimal("0"));
        dto.setPrevClose(new BigDecimal("0"));
        return dto;
    }
}
