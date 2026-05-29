package com.stock.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stock.trade.entity.TradeOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {
}
