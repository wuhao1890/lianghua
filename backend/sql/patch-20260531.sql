USE `stock_trading`;

ALTER TABLE `stock_daily`
  ADD COLUMN `prev_close` DECIMAL(18, 4) DEFAULT NULL COMMENT 'previous close';

ALTER TABLE `trade_order`
  ADD COLUMN `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time';

CREATE TABLE IF NOT EXISTS `sector_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `sector_name` VARCHAR(100) NOT NULL,
  `sector_code` VARCHAR(50) NOT NULL,
  `change_percent` DECIMAL(10, 4) DEFAULT 0,
  `leader_stock` VARCHAR(20) DEFAULT NULL,
  `leader_name` VARCHAR(100) DEFAULT NULL,
  `stock_count` INT DEFAULT 0,
  `avg_change` DECIMAL(10, 4) DEFAULT 0,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sector_code` (`sector_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `sector_stock` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `sector_code` VARCHAR(50) NOT NULL,
  `stock_code` VARCHAR(20) NOT NULL,
  `stock_name` VARCHAR(100) NOT NULL,
  `market_cap` DECIMAL(18, 2) DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sector_stock` (`sector_code`, `stock_code`),
  KEY `idx_sector_code` (`sector_code`),
  KEY `idx_stock_code` (`stock_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `sector_info`
  (`sector_name`, `sector_code`, `change_percent`, `leader_stock`, `leader_name`, `stock_count`, `avg_change`)
VALUES
  ('白酒', 'liquor', 1.2500, '600519', '贵州茅台', 1, 1.2500),
  ('保险', 'insurance', -0.6500, '601318', '中国平安', 1, -0.6500),
  ('新能源汽车', 'ev', 2.1800, '002594', '比亚迪', 1, 2.1800),
  ('科技', 'technology', 1.5000, 'AAPL', '苹果', 3, 1.5000)
ON DUPLICATE KEY UPDATE
  `sector_name` = VALUES(`sector_name`),
  `change_percent` = VALUES(`change_percent`),
  `leader_stock` = VALUES(`leader_stock`),
  `leader_name` = VALUES(`leader_name`),
  `stock_count` = VALUES(`stock_count`),
  `avg_change` = VALUES(`avg_change`);

INSERT INTO `sector_stock`
  (`sector_code`, `stock_code`, `stock_name`, `market_cap`)
VALUES
  ('liquor', '600519', '贵州茅台', 0),
  ('insurance', '601318', '中国平安', 0),
  ('ev', '002594', '比亚迪', 0),
  ('technology', 'AAPL', '苹果', 0),
  ('technology', 'MSFT', '微软', 0),
  ('technology', 'NVDA', '英伟达', 0)
ON DUPLICATE KEY UPDATE
  `stock_name` = VALUES(`stock_name`),
  `market_cap` = VALUES(`market_cap`);

UPDATE `stock_daily` d
JOIN (
  SELECT
    id,
    LAG(close_price) OVER (PARTITION BY stock_code ORDER BY trade_date) AS prev_close_value
  FROM `stock_daily`
) x ON d.id = x.id
SET d.prev_close = x.prev_close_value
WHERE d.prev_close IS NULL AND x.prev_close_value IS NOT NULL;
