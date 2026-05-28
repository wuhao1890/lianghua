package com.stock.stock.service;

import com.stock.stock.dto.FundInfoDTO;

import java.util.List;
import java.util.Map;

public interface FundService {

    /**
     * 获取基金列表（支持关键词搜索、类型筛选、分页）
     *
     * @param keyword  搜索关键词（基金代码或名称）
     * @param fundType 基金类型筛选
     * @param page     页码
     * @param pageSize 每页条数
     * @return Map包含 list / total / page / pageSize
     */
    Map<String, Object> getFundList(String keyword, String fundType, int page, int pageSize);

    /**
     * 获取基金详情（含实时估值）
     *
     * @param code 基金代码
     */
    FundInfoDTO getFundDetail(String code);

    /**
     * 获取基金净值历史（模拟数据，基于当前净值反推）
     *
     * @param code 基金代码
     * @param days 天数
     */
    List<FundInfoDTO> getFundNavHistory(String code, int days);
}
