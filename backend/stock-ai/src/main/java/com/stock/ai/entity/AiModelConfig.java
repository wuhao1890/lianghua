package com.stock.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_model_config")
public class AiModelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;          // 配置名称

    private String provider;      // 供应商: openai, deepseek, qwen, custom

    private String apiKey;        // API密钥

    private String baseUrl;       // API地址

    private String modelName;     // 模型名称

    private Long userId;          // 所属用户

    private Boolean enabled;      // 是否启用

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
