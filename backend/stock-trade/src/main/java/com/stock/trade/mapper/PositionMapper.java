package com.stock.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stock.trade.entity.Position;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PositionMapper extends BaseMapper<Position> {
}
