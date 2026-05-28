package com.stock.stock.service;

import com.stock.stock.dto.FundInfoDTO;

import java.util.List;

public interface FundService {

    /**
     * 获取基金列表
     */
    List<FundInfoDTO> getFundList();

    /**
     * 获取基金详情
     *
     * @param code 基金代码
     */
    FundInfoDTO getFundDetail(String code);
}
