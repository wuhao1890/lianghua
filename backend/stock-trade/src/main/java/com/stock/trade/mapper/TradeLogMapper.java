package com.stock.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stock.trade.entity.TradeLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TradeLogMapper extends BaseMapper<TradeLog> {
}
