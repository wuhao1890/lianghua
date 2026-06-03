package com.stock.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import com.stock.server.config.QualifiedBeanNameGenerator;

@SpringBootApplication
@ComponentScan(
        basePackages = {
                "com.stock.server",
                "com.stock.auth",
                "com.stock.stock",
                "com.stock.trade",
                "com.stock.analysis",
                "com.stock.ai"
        },
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.stock\\..*\\..*Application"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.stock\\.(auth|stock|trade|analysis|ai)\\.config\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.stock\\..*\\.common\\.GlobalExceptionHandler")
        }
)
@MapperScan(basePackages = {
        "com.stock.auth.mapper",
        "com.stock.stock.mapper",
        "com.stock.trade.mapper",
        "com.stock.analysis.mapper",
        "com.stock.ai.mapper"
}, nameGenerator = QualifiedBeanNameGenerator.class)
public class StockServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockServerApplication.class, args);
    }
}
