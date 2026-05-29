package com.stock.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_analysis_result")
public class AiAnalysisResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long configId;        // 使用的模型配置

    private String stockCode;     // 股票代码

    private String stockName;     // 股票名称

    private String signalType;     // BUY/SELL/HOLD

    private BigDecimal score;     // 综合评分 0-100

    private String targetPrice;   // 目标价

    private String analysis;      // 分析内容(Markdown)

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
