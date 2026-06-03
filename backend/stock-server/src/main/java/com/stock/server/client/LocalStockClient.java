package com.stock.server.client;

import com.stock.stock.service.StockService;
import com.stock.trade.client.StockClient;
import com.stock.trade.dto.StockDTO;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class LocalStockClient implements StockClient {

    private final StockService stockService;

    public LocalStockClient(StockService stockService) {
        this.stockService = stockService;
    }

    @Override
    public StockDTO getRealtimeQuote(String code) {
        com.stock.stock.dto.StockDTO source = stockService.getRealtimeQuote(code);
        if (source == null) {
            return null;
        }
        StockDTO target = new StockDTO();
        target.setCode(source.getCode());
        target.setName(source.getName());
        target.setMarket(source.getMarket());
        target.setCurrentPrice(source.getCurrentPrice());
        target.setChangePercent(source.getChangePercent());
        target.setVolume(source.getVolume());
        target.setTurnover(source.getTurnover());
        target.setHighPrice(source.getHighPrice());
        target.setLowPrice(source.getLowPrice());
        target.setOpenPrice(source.getOpenPrice());
        target.setPrevClose(source.getPrevClose());
        return target;
    }
}
