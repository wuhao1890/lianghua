package com.stock.auth.service;

import com.stock.auth.dto.RechargeRequest;

import java.util.Map;

public interface RechargeService {

    /**
     * 用户提交充值申请
     */
    void applyRecharge(RechargeRequest request);

    /**
     * 管理员给用户充值
     */
    void adminRecharge(RechargeRequest request, Long operatorId);

    /**
     * 管理员确认充值（用户微信转账后，管理员确认）
     */
    void confirmRecharge(Long orderId, Long operatorId);

    /**
     * 查询用户充值记录
     */
    Map<String, Object> getRecords(Long userId, Integer page, Integer pageSize);

    /**
     * 管理员查询所有充值记录
     */
    Map<String, Object> getAllRecords(Integer page, Integer pageSize, String status);
}
