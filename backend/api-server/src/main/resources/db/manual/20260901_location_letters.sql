-- Location-based letters can be left and read anywhere without a registered place.

CREATE TABLE IF NOT EXISTS `location_letter` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `author_id` BIGINT NOT NULL,
  `latitude_bucket` DECIMAL(7,4) NOT NULL,
  `longitude_bucket` DECIMAL(8,4) NOT NULL,
  `content` TEXT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_location_letter_nearby` (`latitude_bucket`, `longitude_bucket`, `created_at`),
  INDEX `idx_location_letter_author` (`author_id`),
  CONSTRAINT `fk_location_letter_author`
    FOREIGN KEY (`author_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `location_letter_like` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `letter_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_location_letter_like_user` (`letter_id`, `user_id`),
  INDEX `idx_location_letter_like_user` (`user_id`),
  CONSTRAINT `fk_location_letter_like_letter`
    FOREIGN KEY (`letter_id`) REFERENCES `location_letter` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_location_letter_like_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `location_letter_report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `letter_id` BIGINT NOT NULL,
  `reporter_id` BIGINT NOT NULL,
  `reason` VARCHAR(40) NOT NULL,
  `details` TEXT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_location_letter_report_reporter` (`letter_id`, `reporter_id`),
  INDEX `idx_location_letter_report_status_created` (`status`, `created_at`),
  INDEX `idx_location_letter_report_status_resolved` (`status`, `resolved_at`),
  INDEX `idx_location_letter_report_reporter` (`reporter_id`),
  CONSTRAINT `fk_location_letter_report_letter`
    FOREIGN KEY (`letter_id`) REFERENCES `location_letter` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_location_letter_report_reporter`
    FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Repair the moderation lifecycle when an earlier preview created the table
-- before resolved_at and its retention index were introduced.
SET @location_letter_report_resolved_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'location_letter_report'
    AND column_name = 'resolved_at'
);

SET @location_letter_report_resolved_column_ddl = IF(
  @location_letter_report_resolved_column_exists = 0,
  'ALTER TABLE `location_letter_report` ADD COLUMN `resolved_at` DATETIME NULL AFTER `created_at`',
  'SELECT 1'
);

PREPARE location_letter_report_resolved_column_statement
  FROM @location_letter_report_resolved_column_ddl;
EXECUTE location_letter_report_resolved_column_statement;
DEALLOCATE PREPARE location_letter_report_resolved_column_statement;

SET @location_letter_report_resolved_index_exists = (
  SELECT COUNT(DISTINCT index_name)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'location_letter_report'
    AND index_name = 'idx_location_letter_report_status_resolved'
);

SET @location_letter_report_resolved_index_ddl = IF(
  @location_letter_report_resolved_index_exists = 0,
  'ALTER TABLE `location_letter_report` ADD INDEX `idx_location_letter_report_status_resolved` (`status`, `resolved_at`)',
  'SELECT 1'
);

PREPARE location_letter_report_resolved_index_statement
  FROM @location_letter_report_resolved_index_ddl;
EXECUTE location_letter_report_resolved_index_statement;
DEALLOCATE PREPARE location_letter_report_resolved_index_statement;
