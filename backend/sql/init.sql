-- =====================================================
-- 股票模拟交易系统 - 数据库初始化脚本
-- 数据库: stock_trading
-- =====================================================

CREATE DATABASE IF NOT EXISTS `stock_trading` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE `stock_trading`;

-- ---------------------------------------------------
-- 1. 用户表
-- ---------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(200) NOT NULL COMMENT '密码(BCrypt加密)',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `initial_capital` DECIMAL(18, 2) NOT NULL DEFAULT 1000000.00 COMMENT '初始资金',
    `available_cash` DECIMAL(18, 2) NOT NULL DEFAULT 1000000.00 COMMENT '可用资金',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删除, 1-已删除)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_email` (`email`),
    KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ---------------------------------------------------
-- 2. 股票基本信息表
-- ---------------------------------------------------
DROP TABLE IF EXISTS `stock_info`;
CREATE TABLE `stock_info` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `code` VARCHAR(20) NOT NULL COMMENT '股票代码',
    `name` VARCHAR(100) NOT NULL COMMENT '股票名称',
    `market` VARCHAR(20) NOT NULL COMMENT '市场(A_STOCK/NASDAQ)',
    `current_price` DECIMAL(18, 4) DEFAULT NULL COMMENT '当前价格',
    `change_percent` DECIMAL(10, 4) DEFAULT NULL COMMENT '涨跌幅(%)',
    `volume` BIGINT DEFAULT NULL COMMENT '成交量',
    `turnover` DECIMAL(18, 2) DEFAULT NULL COMMENT '成交额',
    `high_price` DECIMAL(18, 4) DEFAULT NULL COMMENT '最高价',
    `low_price` DECIMAL(18, 4) DEFAULT NULL COMMENT '最低价',
    `open_price` DECIMAL(18, 4) DEFAULT NULL COMMENT '开盘价',
    `prev_close` DECIMAL(18, 4) DEFAULT NULL COMMENT '昨收价',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_market` (`market`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='股票基本信息表';

-- ---------------------------------------------------
-- 3. 股票日K数据表
-- ---------------------------------------------------
DROP TABLE IF EXISTS `stock_daily`;
CREATE TABLE `stock_daily` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `stock_code` VARCHAR(20) NOT NULL COMMENT '股票代码',
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `open_price` DECIMAL(18, 4) DEFAULT NULL COMMENT '开盘价',
    `high_price` DECIMAL(18, 4) DEFAULT NULL COMMENT '最高价',
    `low_price` DECIMAL(18, 4) DEFAULT NULL COMMENT '最低价',
    `close_price` DECIMAL(18, 4) DEFAULT NULL COMMENT '收盘价',
    `volume` BIGINT DEFAULT NULL COMMENT '成交量',
    `turnover` DECIMAL(18, 2) DEFAULT NULL COMMENT '成交额',
    `ma5` DECIMAL(18, 4) DEFAULT NULL COMMENT '5日均线',
    `ma10` DECIMAL(18, 4) DEFAULT NULL COMMENT '10日均线',
    `ma20` DECIMAL(18, 4) DEFAULT NULL COMMENT '20日均线',
    `ma60` DECIMAL(18, 4) DEFAULT NULL COMMENT '60日均线',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code_date` (`stock_code`, `trade_date`),
    KEY `idx_stock_code` (`stock_code`),
    KEY `idx_trade_date` (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='股票日K数据表';

-- ---------------------------------------------------
-- 4. 交易订单表
-- ---------------------------------------------------
DROP TABLE IF EXISTS `trade_order`;
CREATE TABLE `trade_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `stock_code` VARCHAR(20) NOT NULL COMMENT '股票代码',
    `stock_name` VARCHAR(100) DEFAULT NULL COMMENT '股票名称',
    `market` VARCHAR(20) DEFAULT NULL COMMENT '市场',
    `direction` VARCHAR(10) NOT NULL COMMENT '买卖方向(BUY/SELL)',
    `order_type` VARCHAR(10) NOT NULL DEFAULT 'MARKET' COMMENT '订单类型(MARKET/LIMIT)',
    `price` DECIMAL(18, 4) NOT NULL COMMENT '委托价格',
    `quantity` INT NOT NULL COMMENT '委托数量',
    `amount` DECIMAL(18, 2) NOT NULL COMMENT '委托金额',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '订单状态(PENDING/FILLED/CANCELLED)',
    `fee` DECIMAL(18, 2) DEFAULT NULL COMMENT '手续费',
    `filled_time` DATETIME DEFAULT NULL COMMENT '成交时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_stock_code` (`stock_code`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易订单表';

-- ---------------------------------------------------
-- 5. 持仓表
-- ---------------------------------------------------
DROP TABLE IF EXISTS `position`;
CREATE TABLE `position` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `stock_code` VARCHAR(20) NOT NULL COMMENT '股票代码',
    `stock_name` VARCHAR(100) DEFAULT NULL COMMENT '股票名称',
    `market` VARCHAR(20) DEFAULT NULL COMMENT '市场',
    `quantity` INT NOT NULL DEFAULT 0 COMMENT '持仓数量',
    `avg_cost` DECIMAL(18, 4) NOT NULL COMMENT '平均成本',
    `current_price` DECIMAL(18, 4) DEFAULT NULL COMMENT '当前价格',
    `profit_loss` DECIMAL(18, 2) DEFAULT NULL COMMENT '浮动盈亏',
    `profit_loss_percent` DECIMAL(10, 4) DEFAULT NULL COMMENT '收益率(%)',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_stock` (`user_id`, `stock_code`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='持仓表';

-- ---------------------------------------------------
-- 6. 交易日志表
-- ---------------------------------------------------
DROP TABLE IF EXISTS `trade_log`;
CREATE TABLE `trade_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `order_id` BIGINT DEFAULT NULL COMMENT '订单ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `stock_code` VARCHAR(20) NOT NULL COMMENT '股票代码',
    `action` VARCHAR(10) NOT NULL COMMENT '操作(BUY/SELL)',
    `price` DECIMAL(18, 4) NOT NULL COMMENT '成交价格',
    `quantity` INT NOT NULL COMMENT '成交数量',
    `amount` DECIMAL(18, 2) NOT NULL COMMENT '成交金额',
    `profit_loss` DECIMAL(18, 2) DEFAULT NULL COMMENT '盈亏',
    `fee` DECIMAL(18, 2) DEFAULT NULL COMMENT '手续费',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_stock_code` (`stock_code`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易日志表';

-- =====================================================
-- 插入示例数据
-- =====================================================

-- 插入示例股票信息
INSERT INTO `stock_info` (`code`, `name`, `market`, `current_price`, `change_percent`, `volume`, `turnover`, `high_price`, `low_price`, `open_price`, `prev_close`) VALUES
('600519', '贵州茅台', 'A_STOCK', 1688.0000, 1.2500, 3500000, 5908000000.00, 1695.0000, 1665.0000, 1670.0000, 1667.2000),
('601318', '中国平安', 'A_STOCK', 45.8500, -0.6500, 52000000, 2384200000.00, 46.2000, 45.5000, 46.1000, 46.1500),
('002594', '比亚迪', 'A_STOCK', 265.5000, 2.1800, 18000000, 4779000000.00, 268.0000, 258.0000, 260.0000, 259.8400),
('00700', '腾讯控股', 'NASDAQ', 375.2000, 0.8900, 12000000, 4502400000.00, 378.0000, 370.5000, 372.0000, 371.8900),
('AAPL', '苹果', 'NASDAQ', 189.8400, 1.1200, 55000000, 10441200000.00, 191.0000, 187.5000, 188.0000, 187.7400),
('MSFT', '微软', 'NASDAQ', 415.6000, 0.5600, 22000000, 9143200000.00, 418.0000, 413.0000, 414.0000, 413.2900),
('TSLA', '特斯拉', 'NASDAQ', 178.3000, -1.8200, 95000000, 16938500000.00, 182.0000, 175.0000, 181.0000, 181.6100),
('NVDA', '英伟达', 'NASDAQ', 924.7900, 3.2500, 42000000, 38841180000.00, 930.0000, 895.0000, 900.0000, 895.6100);

-- 插入贵州茅台(600519)模拟日K数据(最近60个交易日)
-- 使用存储过程生成模拟数据
DELIMITER //
CREATE PROCEDURE generate_daily_data()
BEGIN
    DECLARE i INT DEFAULT 60;
    DECLARE base_price DECIMAL(18,4) DEFAULT 1600.0000;
    DECLARE current_price DECIMAL(18,4) DEFAULT 1600.0000;
    DECLARE trade_date DATE;
    DECLARE rand_val FLOAT;

    -- 贵州茅台 600519
    SET current_price = 1600.0000;
    SET trade_date = DATE_SUB(CURDATE(), INTERVAL 60 DAY);
    WHILE i > 0 DO
        -- 跳过周末
        WHILE DAYOFWEEK(trade_date) IN (1, 7) DO
            SET trade_date = DATE_ADD(trade_date, INTERVAL 1 DAY);
        END WHILE;

        SET rand_val = (RAND() - 0.48) * 40;
        SET current_price = current_price + rand_val;
        IF current_price < 1400 THEN SET current_price = 1400 + RAND() * 50; END IF;
        IF current_price > 1800 THEN SET current_price = 1750 - RAND() * 50; END IF;

        INSERT INTO `stock_daily` (`stock_code`, `trade_date`, `open_price`, `high_price`, `low_price`, `close_price`, `volume`, `turnover`)
        VALUES ('600519', trade_date,
            ROUND(current_price - rand_val * 0.3, 4),
            ROUND(current_price + ABS(rand_val) * 0.5, 4),
            ROUND(current_price - ABS(rand_val) * 0.5, 4),
            ROUND(current_price, 4),
            FLOOR(2000000 + RAND() * 3000000),
            ROUND(current_price * (2000000 + RAND() * 3000000), 2)
        )
        ON DUPLICATE KEY UPDATE
            `open_price` = VALUES(`open_price`),
            `high_price` = VALUES(`high_price`),
            `low_price` = VALUES(`low_price`),
            `close_price` = VALUES(`close_price`),
            `volume` = VALUES(`volume`),
            `turnover` = VALUES(`turnover`);

        SET trade_date = DATE_ADD(trade_date, INTERVAL 1 DAY);
        SET i = i - 1;
    END WHILE;

    -- 中国平安 601318
    SET i = 60;
    SET current_price = 42.0000;
    SET trade_date = DATE_SUB(CURDATE(), INTERVAL 60 DAY);
    WHILE i > 0 DO
        WHILE DAYOFWEEK(trade_date) IN (1, 7) DO
            SET trade_date = DATE_ADD(trade_date, INTERVAL 1 DAY);
        END WHILE;

        SET rand_val = (RAND() - 0.48) * 1.5;
        SET current_price = current_price + rand_val;
        IF current_price < 38 THEN SET current_price = 38 + RAND(); END IF;
        IF current_price < 0 THEN SET current_price = 40; END IF;
        IF current_price > 50 THEN SET current_price = 48 - RAND(); END IF;

        INSERT INTO `stock_daily` (`stock_code`, `trade_date`, `open_price`, `high_price`, `low_price`, `close_price`, `volume`, `turnover`)
        VALUES ('601318', trade_date,
            ROUND(current_price - rand_val * 0.3, 4),
            ROUND(current_price + ABS(rand_val) * 0.5, 4),
            ROUND(current_price - ABS(rand_val) * 0.5, 4),
            ROUND(current_price, 4),
            FLOOR(30000000 + RAND() * 40000000),
            ROUND(current_price * (30000000 + RAND() * 40000000), 2)
        )
        ON DUPLICATE KEY UPDATE
            `open_price` = VALUES(`open_price`),
            `high_price` = VALUES(`high_price`),
            `low_price` = VALUES(`low_price`),
            `close_price` = VALUES(`close_price`),
            `volume` = VALUES(`volume`),
            `turnover` = VALUES(`turnover`);

        SET trade_date = DATE_ADD(trade_date, INTERVAL 1 DAY);
        SET i = i - 1;
    END WHILE;

    -- 比亚迪 002594
    SET i = 60;
    SET current_price = 240.0000;
    SET trade_date = DATE_SUB(CURDATE(), INTERVAL 60 DAY);
    WHILE i > 0 DO
        WHILE DAYOFWEEK(trade_date) IN (1, 7) DO
            SET trade_date = DATE_ADD(trade_date, INTERVAL 1 DAY);
        END WHILE;

        SET rand_val = (RAND() - 0.48) * 8;
        SET current_price = current_price + rand_val;
        IF current_price < 200 THEN SET current_price = 200 + RAND() * 10; END IF;
        IF current_price > 300 THEN SET current_price = 290 - RAND() * 10; END IF;

        INSERT INTO `stock_daily` (`stock_code`, `trade_date`, `open_price`, `high_price`, `low_price`, `close_price`, `volume`, `turnover`)
        VALUES ('002594', trade_date,
            ROUND(current_price - rand_val * 0.3, 4),
            ROUND(current_price + ABS(rand_val) * 0.5, 4),
            ROUND(current_price - ABS(rand_val) * 0.5, 4),
            ROUND(current_price, 4),
            FLOOR(10000000 + RAND() * 15000000),
            ROUND(current_price * (10000000 + RAND() * 15000000), 2)
        )
        ON DUPLICATE KEY UPDATE
            `open_price` = VALUES(`open_price`),
            `high_price` = VALUES(`high_price`),
            `low_price` = VALUES(`low_price`),
            `close_price` = VALUES(`close_price`),
            `volume` = VALUES(`volume`),
            `turnover` = VALUES(`turnover`);

        SET trade_date = DATE_ADD(trade_date, INTERVAL 1 DAY);
        SET i = i - 1;
    END WHILE;

    -- 苹果 AAPL
    SET i = 60;
    SET current_price = 175.0000;
    SET trade_date = DATE_SUB(CURDATE(), INTERVAL 60 DAY);
    WHILE i > 0 DO
        WHILE DAYOFWEEK(trade_date) IN (1, 7) DO
            SET trade_date = DATE_ADD(trade_date, INTERVAL 1 DAY);
        END WHILE;

        SET rand_val = (RAND() - 0.48) * 4;
        SET current_price = current_price + rand_val;
        IF current_price < 165 THEN SET current_price = 165 + RAND() * 3; END IF;
        IF current_price > 195 THEN SET current_price = 192 - RAND() * 3; END IF;

        INSERT INTO `stock_daily` (`stock_code`, `trade_date`, `open_price`, `high_price`, `low_price`, `close_price`, `volume`, `turnover`)
        VALUES ('AAPL', trade_date,
            ROUND(current_price - rand_val * 0.3, 4),
            ROUND(current_price + ABS(rand_val) * 0.5, 4),
            ROUND(current_price - ABS(rand_val) * 0.5, 4),
            ROUND(current_price, 4),
            FLOOR(40000000 + RAND() * 30000000),
            ROUND(current_price * (40000000 + RAND() * 30000000), 2)
        )
        ON DUPLICATE KEY UPDATE
            `open_price` = VALUES(`open_price`),
            `high_price` = VALUES(`high_price`),
            `low_price` = VALUES(`low_price`),
            `close_price` = VALUES(`close_price`),
            `volume` = VALUES(`volume`),
            `turnover` = VALUES(`turnover`);

        SET trade_date = DATE_ADD(trade_date, INTERVAL 1 DAY);
        SET i = i - 1;
    END WHILE;

    -- 微软 MSFT
    SET i = 60;
    SET current_price = 390.0000;
    SET trade_date = DATE_SUB(CURDATE(), INTERVAL 60 DAY);
    WHILE i > 0 DO
        WHILE DAYOFWEEK(trade_date) IN (1, 7) DO
            SET trade_date = DATE_ADD(trade_date, INTERVAL 1 DAY);
        END WHILE;

        SET rand_val = (RAND() - 0.48) * 8;
        SET current_price = current_price + rand_val;
        IF current_price < 370 THEN SET current_price = 370 + RAND() * 5; END IF;
        IF current_price > 430 THEN SET current_price = 425 - RAND() * 5; END IF;

        INSERT INTO `stock_daily` (`stock_code`, `trade_date`, `open_price`, `high_price`, `low_price`, `close_price`, `volume`, `turnover`)
        VALUES ('MSFT', trade_date,
            ROUND(current_price - rand_val * 0.3, 4),
            ROUND(current_price + ABS(rand_val) * 0.5, 4),
            ROUND(current_price - ABS(rand_val) * 0.5, 4),
            ROUND(current_price, 4),
            FLOOR(15000000 + RAND() * 15000000),
            ROUND(current_price * (15000000 + RAND() * 15000000), 2)
        )
        ON DUPLICATE KEY UPDATE
            `open_price` = VALUES(`open_price`),
            `high_price` = VALUES(`high_price`),
            `low_price` = VALUES(`low_price`),
            `close_price` = VALUES(`close_price`),
            `volume` = VALUES(`volume`),
            `turnover` = VALUES(`turnover`);

        SET trade_date = DATE_ADD(trade_date, INTERVAL 1 DAY);
        SET i = i - 1;
    END WHILE;

    -- 特斯拉 TSLA
    SET i = 60;
    SET current_price = 200.0000;
    SET trade_date = DATE_SUB(CURDATE(), INTERVAL 60 DAY);
    WHILE i > 0 DO
        WHILE DAYOFWEEK(trade_date) IN (1, 7) DO
            SET trade_date = DATE_ADD(trade_date, INTERVAL 1 DAY);
        END WHILE;

        SET rand_val = (RAND() - 0.48) * 10;
        SET current_price = current_price + rand_val;
        IF current_price < 150 THEN SET current_price = 150 + RAND() * 10; END IF;
        IF current_price > 250 THEN SET current_price = 245 - RAND() * 10; END IF;

        INSERT INTO `stock_daily` (`stock_code`, `trade_date`, `open_price`, `high_price`, `low_price`, `close_price`, `volume`, `turnover`)
        VALUES ('TSLA', trade_date,
            ROUND(current_price - rand_val * 0.3, 4),
            ROUND(current_price + ABS(rand_val) * 0.5, 4),
            ROUND(current_price - ABS(rand_val) * 0.5, 4),
            ROUND(current_price, 4),
            FLOOR(70000000 + RAND() * 50000000),
            ROUND(current_price * (70000000 + RAND() * 50000000), 2)
        )
        ON DUPLICATE KEY UPDATE
            `open_price` = VALUES(`open_price`),
            `high_price` = VALUES(`high_price`),
            `low_price` = VALUES(`low_price`),
            `close_price` = VALUES(`close_price`),
            `volume` = VALUES(`volume`),
            `turnover` = VALUES(`turnover`);

        SET trade_date = DATE_ADD(trade_date, INTERVAL 1 DAY);
        SET i = i - 1;
    END WHILE;

    -- 英伟达 NVDA
    SET i = 60;
    SET current_price = 800.0000;
    SET trade_date = DATE_SUB(CURDATE(), INTERVAL 60 DAY);
    WHILE i > 0 DO
        WHILE DAYOFWEEK(trade_date) IN (1, 7) DO
            SET trade_date = DATE_ADD(trade_date, INTERVAL 1 DAY);
        END WHILE;

        SET rand_val = (RAND() - 0.45) * 25;
        SET current_price = current_price + rand_val;
        IF current_price < 700 THEN SET current_price = 700 + RAND() * 20; END IF;
        IF current_price > 950 THEN SET current_price = 940 - RAND() * 20; END IF;

        INSERT INTO `stock_daily` (`stock_code`, `trade_date`, `open_price`, `high_price`, `low_price`, `close_price`, `volume`, `turnover`)
        VALUES ('NVDA', trade_date,
            ROUND(current_price - rand_val * 0.3, 4),
            ROUND(current_price + ABS(rand_val) * 0.5, 4),
            ROUND(current_price - ABS(rand_val) * 0.5, 4),
            ROUND(current_price, 4),
            FLOOR(30000000 + RAND() * 20000000),
            ROUND(current_price * (30000000 + RAND() * 20000000), 2)
        )
        ON DUPLICATE KEY UPDATE
            `open_price` = VALUES(`open_price`),
            `high_price` = VALUES(`high_price`),
            `low_price` = VALUES(`low_price`),
            `close_price` = VALUES(`close_price`),
            `volume` = VALUES(`volume`),
            `turnover` = VALUES(`turnover`);

        SET trade_date = DATE_ADD(trade_date, INTERVAL 1 DAY);
        SET i = i - 1;
    END WHILE;

    -- 腾讯控股 00700
    SET i = 60;
    SET current_price = 340.0000;
    SET trade_date = DATE_SUB(CURDATE(), INTERVAL 60 DAY);
    WHILE i > 0 DO
        WHILE DAYOFWEEK(trade_date) IN (1, 7) DO
            SET trade_date = DATE_ADD(trade_date, INTERVAL 1 DAY);
        END WHILE;

        SET rand_val = (RAND() - 0.48) * 6;
        SET current_price = current_price + rand_val;
        IF current_price < 310 THEN SET current_price = 310 + RAND() * 5; END IF;
        IF current_price > 400 THEN SET current_price = 395 - RAND() * 5; END IF;

        INSERT INTO `stock_daily` (`stock_code`, `trade_date`, `open_price`, `high_price`, `low_price`, `close_price`, `volume`, `turnover`)
        VALUES ('00700', trade_date,
            ROUND(current_price - rand_val * 0.3, 4),
            ROUND(current_price + ABS(rand_val) * 0.5, 4),
            ROUND(current_price - ABS(rand_val) * 0.5, 4),
            ROUND(current_price, 4),
            FLOOR(8000000 + RAND() * 8000000),
            ROUND(current_price * (8000000 + RAND() * 8000000), 2)
        )
        ON DUPLICATE KEY UPDATE
            `open_price` = VALUES(`open_price`),
            `high_price` = VALUES(`high_price`),
            `low_price` = VALUES(`low_price`),
            `close_price` = VALUES(`close_price`),
            `volume` = VALUES(`volume`),
            `turnover` = VALUES(`turnover`);

        SET trade_date = DATE_ADD(trade_date, INTERVAL 1 DAY);
        SET i = i - 1;
    END WHILE;
END //
DELIMITER ;

-- 执行存储过程生成数据
CALL generate_daily_data();

-- 删除存储过程
DROP PROCEDURE IF EXISTS generate_daily_data;

-- =====================================================
-- 更新均线数据
-- =====================================================
DELIMITER //
CREATE PROCEDURE update_ma_data()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE v_code VARCHAR(20);
    DECLARE v_date DATE;
    DECLARE v_close DECIMAL(18,4);
    DECLARE v_idx INT DEFAULT 0;

    -- 游标：获取所有股票代码
    DECLARE cur CURSOR FOR SELECT DISTINCT stock_code FROM stock_daily ORDER BY stock_code;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO v_code;
        IF done THEN
            LEAVE read_loop;
        END IF;

        -- 计算每条记录的MA5, MA10, MA20, MA60
        SET v_idx = 0;
        BEGIN
            DECLARE done2 INT DEFAULT FALSE;
            DECLARE v_id BIGINT;
            DECLARE v_tc DATE;
            DECLARE v_cl DECIMAL(18,4);
            DECLARE cur2 CURSOR FOR SELECT id, trade_date, close_price FROM stock_daily WHERE stock_code = v_code ORDER BY trade_date ASC;
            DECLARE CONTINUE HANDLER FOR NOT FOUND SET done2 = TRUE;

            OPEN cur2;
            ma_loop: LOOP
                FETCH cur2 INTO v_id, v_tc, v_cl;
                IF done2 THEN
                    LEAVE ma_loop;
                END IF;

                SET v_idx = v_idx + 1;

                -- MA5
                IF v_idx >= 5 THEN
                    UPDATE stock_daily SET ma5 = (
                        SELECT AVG(close_price) FROM (
                            SELECT close_price FROM stock_daily
                            WHERE stock_code = v_code AND trade_date <= v_tc
                            ORDER BY trade_date DESC LIMIT 5
                        ) t
                    ) WHERE id = v_id;
                END IF;

                -- MA10
                IF v_idx >= 10 THEN
                    UPDATE stock_daily SET ma10 = (
                        SELECT AVG(close_price) FROM (
                            SELECT close_price FROM stock_daily
                            WHERE stock_code = v_code AND trade_date <= v_tc
                            ORDER BY trade_date DESC LIMIT 10
                        ) t
                    ) WHERE id = v_id;
                END IF;

                -- MA20
                IF v_idx >= 20 THEN
                    UPDATE stock_daily SET ma20 = (
                        SELECT AVG(close_price) FROM (
                            SELECT close_price FROM stock_daily
                            WHERE stock_code = v_code AND trade_date <= v_tc
                            ORDER BY trade_date DESC LIMIT 20
                        ) t
                    ) WHERE id = v_id;
                END IF;

                -- MA60
                IF v_idx >= 60 THEN
                    UPDATE stock_daily SET ma60 = (
                        SELECT AVG(close_price) FROM (
                            SELECT close_price FROM stock_daily
                            WHERE stock_code = v_code AND trade_date <= v_tc
                            ORDER BY trade_date DESC LIMIT 60
                        ) t
                    ) WHERE id = v_id;
                END IF;
            END LOOP;
            CLOSE cur2;
        END;

        SET done = FALSE;
    END LOOP;
    CLOSE cur;
END //
DELIMITER ;

-- 执行均线计算
CALL update_ma_data();

-- 删除存储过程
DROP PROCEDURE IF EXISTS update_ma_data;

-- =====================================================
-- 完成
-- =====================================================
SELECT '数据库初始化完成!' AS message;
