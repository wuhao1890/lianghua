package com.stock.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.stock.auth.dto.RechargeRequest;
import com.stock.auth.entity.RechargeOrder;
import com.stock.auth.entity.User;
import com.stock.auth.mapper.RechargeOrderMapper;
import com.stock.auth.service.RechargeService;
import com.stock.auth.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class RechargeServiceImpl extends ServiceImpl<RechargeOrderMapper, RechargeOrder> implements RechargeService {

    @Autowired
    private UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyRecharge(RechargeRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("充值金额必须大于0");
        }
        RechargeOrder order = new RechargeOrder();
        order.setUserId(request.getUserId());
        order.setAmount(request.getAmount());
        order.setStatus("PENDING");
        order.setType("WECHAT");
        order.setRemark(request.getRemark());
        this.save(order);
        log.info("用户 {} 提交充值申请, 金额: {}", request.getUserId(), request.getAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminRecharge(RechargeRequest request, Long operatorId) {
        if (request.getUserId() == null) {
            throw new RuntimeException("请指定充值用户");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("充值金额必须大于0");
        }
        User user = userService.getById(request.getUserId());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        // 增加用户资金
        userService.addCash(request.getUserId(), request.getAmount());
        // 创建充值记录
        RechargeOrder order = new RechargeOrder();
        order.setUserId(request.getUserId());
        order.setAmount(request.getAmount());
        order.setStatus("SUCCESS");
        order.setType("ADMIN");
        order.setOperatorId(operatorId);
        order.setRemark(request.getRemark());
        this.save(order);
        log.info("管理员 {} 给用户 {} 充值 {} 元", operatorId, request.getUserId(), request.getAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmRecharge(Long orderId, Long operatorId) {
        RechargeOrder order = this.getById(orderId);
        if (order == null) {
            throw new RuntimeException("充值订单不存在");
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new RuntimeException("该订单已处理");
        }
        // 增加用户资金
        userService.addCash(order.getUserId(), order.getAmount());
        // 更新订单状态
        order.setStatus("SUCCESS");
        order.setOperatorId(operatorId);
        this.updateById(order);
        log.info("管理员 {} 确认充值订单 {}", operatorId, orderId);
    }

    @Override
    public Map<String, Object> getRecords(Long userId, Integer page, Integer pageSize) {
        Page<RechargeOrder> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<RechargeOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RechargeOrder::getUserId, userId)
               .orderByDesc(RechargeOrder::getCreateTime);
        Page<RechargeOrder> result = this.page(pageParam, wrapper);
        Map<String, Object> map = new HashMap<>();
        map.put("list", result.getRecords());
        map.put("total", result.getTotal());
        return map;
    }

    @Override
    public Map<String, Object> getAllRecords(Integer page, Integer pageSize, String status) {
        Page<RechargeOrder> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<RechargeOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(RechargeOrder::getStatus, status);
        }
        wrapper.orderByDesc(RechargeOrder::getCreateTime);
        Page<RechargeOrder> result = this.page(pageParam, wrapper);
        Map<String, Object> map = new HashMap<>();
        map.put("list", result.getRecords());
        map.put("total", result.getTotal());
        return map;
    }
}
