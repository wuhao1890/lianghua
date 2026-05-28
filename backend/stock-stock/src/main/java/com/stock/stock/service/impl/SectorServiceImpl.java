package com.stock.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stock.stock.dto.SectorDTO;
import com.stock.stock.dto.StockDTO;
import com.stock.stock.entity.SectorInfo;
import com.stock.stock.entity.SectorStock;
import com.stock.stock.entity.StockDaily;
import com.stock.stock.entity.StockInfo;
import com.stock.stock.mapper.SectorInfoMapper;
import com.stock.stock.mapper.SectorStockMapper;
import com.stock.stock.mapper.StockDailyMapper;
import com.stock.stock.mapper.StockInfoMapper;
import com.stock.stock.service.SectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SectorServiceImpl implements SectorService {

    private final SectorInfoMapper sectorInfoMapper;
    private final SectorStockMapper sectorStockMapper;
    private final StockInfoMapper stockInfoMapper;
    private final StockDailyMapper stockDailyMapper;

    public SectorServiceImpl(SectorInfoMapper sectorInfoMapper,
                             SectorStockMapper sectorStockMapper,
                             StockInfoMapper stockInfoMapper,
                             StockDailyMapper stockDailyMapper) {
        this.sectorInfoMapper = sectorInfoMapper;
        this.sectorStockMapper = sectorStockMapper;
        this.stockInfoMapper = stockInfoMapper;
        this.stockDailyMapper = stockDailyMapper;
    }

    @Override
    public List<SectorDTO> getAllSectors() {
        // 获取所有板块
        List<SectorInfo> sectorList = sectorInfoMapper.selectList(
                new LambdaQueryWrapper<SectorInfo>()
                        .orderByDesc(SectorInfo::getChangePercent));

        if (sectorList.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量查询所有板块的成分股
        List<String> sectorCodes = sectorList.stream()
                .map(SectorInfo::getSectorCode)
                .collect(Collectors.toList());

        List<SectorStock> allStocks = sectorStockMapper.selectList(
                new LambdaQueryWrapper<SectorStock>()
                        .in(SectorStock::getSectorCode, sectorCodes));

        // 按板块分组
        Map<String, List<SectorStock>> stocksBySector = allStocks.stream()
                .collect(Collectors.groupingBy(SectorStock::getSectorCode));

        // 查询所有股票的最新行情
        List<String> allStockCodes = allStocks.stream()
                .map(SectorStock::getStockCode)
                .distinct()
                .collect(Collectors.toList());

        List<StockInfo> stockInfos = allStockCodes.isEmpty() ? new ArrayList<>() :
                stockInfoMapper.selectList(
                        new LambdaQueryWrapper<StockInfo>()
                                .in(StockInfo::getCode, allStockCodes));

        Map<String, StockInfo> stockInfoMap = stockInfos.stream()
                .collect(Collectors.toMap(StockInfo::getCode, si -> si, (a, b) -> a));

        List<SectorDTO> result = new ArrayList<>();
        for (SectorInfo sector : sectorList) {
            SectorDTO dto = convertToDTO(sector);

            // 计算板块平均涨跌幅
            List<SectorStock> sectorStocks = stocksBySector.getOrDefault(sector.getSectorCode(), new ArrayList<>());
            BigDecimal totalChange = BigDecimal.ZERO;
            int validCount = 0;

            for (SectorStock ss : sectorStocks) {
                StockInfo info = stockInfoMap.get(ss.getStockCode());
                if (info != null && info.getChangePercent() != null) {
                    totalChange = totalChange.add(info.getChangePercent());
                    validCount++;
                }
            }

            BigDecimal avgChange = null;
            if (validCount > 0) {
                avgChange = totalChange.divide(BigDecimal.valueOf(validCount), 4, RoundingMode.HALF_UP);
            }
            dto.setChangePercent(avgChange);
            dto.setAvgChange(avgChange);

            // 获取龙头股的最新涨跌幅
            if (sector.getLeaderStock() != null && !sector.getLeaderStock().isEmpty()) {
                StockDTO leaderQuote = getLatestStockQuote(sector.getLeaderStock());
                if (leaderQuote != null) {
                    dto.setLeaderChangePercent(leaderQuote.getChangePercent());
                }
            }

            result.add(dto);
        }

        // 按涨跌幅降序排序
        result.sort((a, b) -> {
            BigDecimal ca = a.getChangePercent() != null ? a.getChangePercent() : BigDecimal.ZERO;
            BigDecimal cb = b.getChangePercent() != null ? b.getChangePercent() : BigDecimal.ZERO;
            return cb.compareTo(ca);
        });

        return result;
    }

    @Override
    public SectorDTO getSectorDetail(String sectorCode) {
        SectorInfo sector = sectorInfoMapper.selectOne(
                new LambdaQueryWrapper<SectorInfo>()
                        .eq(SectorInfo::getSectorCode, sectorCode));

        if (sector == null) {
            log.warn("板块不存在: {}", sectorCode);
            return null;
        }

        SectorDTO dto = convertToDTO(sector);

        // 查询板块成分股并计算平均涨跌幅
        List<SectorStock> sectorStocks = sectorStockMapper.selectList(
                new LambdaQueryWrapper<SectorStock>()
                        .eq(SectorStock::getSectorCode, sectorCode));

        if (!sectorStocks.isEmpty()) {
            List<String> stockCodes = sectorStocks.stream()
                    .map(SectorStock::getStockCode)
                    .collect(Collectors.toList());

            List<StockInfo> stockInfos = stockInfoMapper.selectList(
                    new LambdaQueryWrapper<StockInfo>()
                            .in(StockInfo::getCode, stockCodes));

            Map<String, StockInfo> stockInfoMap = stockInfos.stream()
                    .collect(Collectors.toMap(StockInfo::getCode, si -> si, (a, b) -> a));

            BigDecimal totalChange = BigDecimal.ZERO;
            int validCount = 0;
            for (SectorStock ss : sectorStocks) {
                StockInfo info = stockInfoMap.get(ss.getStockCode());
                if (info != null && info.getChangePercent() != null) {
                    totalChange = totalChange.add(info.getChangePercent());
                    validCount++;
                }
            }
            if (validCount > 0) {
                BigDecimal avg = totalChange.divide(BigDecimal.valueOf(validCount), 4, RoundingMode.HALF_UP);
                dto.setChangePercent(avg);
                dto.setAvgChange(avg);
            }
        }

        // 获取龙头股行情
        if (sector.getLeaderStock() != null && !sector.getLeaderStock().isEmpty()) {
            StockDTO leaderQuote = getLatestStockQuote(sector.getLeaderStock());
            if (leaderQuote != null) {
                dto.setLeaderChangePercent(leaderQuote.getChangePercent());
            }
        }

        return dto;
    }

    @Override
    public List<StockDTO> getSectorStocks(String sectorCode) {
        // 查询板块下所有股票
        List<SectorStock> sectorStocks = sectorStockMapper.selectList(
                new LambdaQueryWrapper<SectorStock>()
                        .eq(SectorStock::getSectorCode, sectorCode));

        if (sectorStocks == null || sectorStocks.isEmpty()) {
            return new ArrayList<>();
        }

        // 收集所有股票代码
        List<String> stockCodes = sectorStocks.stream()
                .map(SectorStock::getStockCode)
                .collect(Collectors.toList());

        // 查询股票基本信息
        List<StockInfo> stockInfos = stockInfoMapper.selectList(
                new LambdaQueryWrapper<StockInfo>()
                        .in(StockInfo::getCode, stockCodes));

        Map<String, StockInfo> stockInfoMap = stockInfos.stream()
                .collect(Collectors.toMap(StockInfo::getCode, si -> si, (a, b) -> a));

        // 构建返回结果
        List<StockDTO> result = new ArrayList<>();
        for (SectorStock ss : sectorStocks) {
            StockDTO dto = new StockDTO();
            dto.setCode(ss.getStockCode());
            dto.setName(ss.getStockName());

            // 如果有数据库中的股票信息，填充行情数据
            StockInfo info = stockInfoMap.get(ss.getStockCode());
            if (info != null) {
                dto.setMarket(info.getMarket());
                dto.setCurrentPrice(info.getCurrentPrice());
                dto.setChangePercent(info.getChangePercent());
                dto.setVolume(info.getVolume());
                dto.setTurnover(info.getTurnover());
                dto.setHighPrice(info.getHighPrice());
                dto.setLowPrice(info.getLowPrice());
                dto.setOpenPrice(info.getOpenPrice());
                dto.setPrevClose(info.getPrevClose());
            } else {
                // 尝试从stock_daily获取最新行情
                StockDTO latest = getLatestStockQuote(ss.getStockCode());
                if (latest != null) {
                    dto.setCurrentPrice(latest.getCurrentPrice());
                    dto.setChangePercent(latest.getChangePercent());
                    dto.setVolume(latest.getVolume());
                    dto.setTurnover(latest.getTurnover());
                    dto.setHighPrice(latest.getHighPrice());
                    dto.setLowPrice(latest.getLowPrice());
                    dto.setOpenPrice(latest.getOpenPrice());
                    dto.setPrevClose(latest.getPrevClose());
                }
            }

            result.add(dto);
        }

        return result;
    }

    /**
     * 从stock_daily获取股票最新行情
     */
    private StockDTO getLatestStockQuote(String stockCode) {
        StockDaily daily = stockDailyMapper.selectOne(
                new LambdaQueryWrapper<StockDaily>()
                        .eq(StockDaily::getStockCode, stockCode)
                        .orderByDesc(StockDaily::getTradeDate)
                        .last("LIMIT 1"));

        if (daily == null) {
            return null;
        }

        StockDTO dto = new StockDTO();
        dto.setCode(stockCode);
        dto.setCurrentPrice(daily.getClosePrice());
        dto.setHighPrice(daily.getHighPrice());
        dto.setLowPrice(daily.getLowPrice());
        dto.setOpenPrice(daily.getOpenPrice());
        dto.setPrevClose(daily.getPrevClose());
        dto.setVolume(daily.getVolume());
        dto.setTurnover(daily.getTurnover());

        // 计算涨跌幅
        if (daily.getPrevClose() != null && daily.getPrevClose().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal changePercent = daily.getClosePrice()
                    .subtract(daily.getPrevClose())
                    .divide(daily.getPrevClose(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            dto.setChangePercent(changePercent);
        }

        return dto;
    }

    private SectorDTO convertToDTO(SectorInfo info) {
        SectorDTO dto = new SectorDTO();
        dto.setSectorName(info.getSectorName());
        dto.setSectorCode(info.getSectorCode());
        dto.setChangePercent(info.getChangePercent());
        dto.setLeaderStock(info.getLeaderStock());
        dto.setLeaderName(info.getLeaderName());
        dto.setStockCount(info.getStockCount());
        dto.setAvgChange(info.getAvgChange());
        return dto;
    }
}
