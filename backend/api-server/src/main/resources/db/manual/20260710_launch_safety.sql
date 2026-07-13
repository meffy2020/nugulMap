-- NugulMap production migration: support intake and public UGC moderation.
-- Safe to run repeatedly on MySQL 8.0 because every table creation is guarded.
-- Apply this file before restarting an API image that contains the matching entities.

CREATE TABLE IF NOT EXISTS `user_block` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `blocker_id` BIGINT NOT NULL,
  `blocked_id` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_block_pair` (`blocker_id`, `blocked_id`),
  INDEX `idx_user_block_blocked_id` (`blocked_id`),
  CONSTRAINT `fk_user_block_blocker`
    FOREIGN KEY (`blocker_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_user_block_blocked`
    FOREIGN KEY (`blocked_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_user_block_not_self` CHECK (`blocker_id` <> `blocked_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `zone_review_report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `review_id` BIGINT NOT NULL,
  `reporter_id` BIGINT NOT NULL,
  `reason` VARCHAR(40) NOT NULL,
  `details` TEXT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_zone_review_report_reporter` (`review_id`, `reporter_id`),
  INDEX `idx_zone_review_report_status_created` (`status`, `created_at`),
  INDEX `idx_zone_review_report_status_resolved` (`status`, `resolved_at`),
  INDEX `idx_zone_review_report_reporter` (`reporter_id`),
  CONSTRAINT `fk_zone_review_report_review`
    FOREIGN KEY (`review_id`) REFERENCES `zone_review` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_zone_review_report_reporter`
    FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `zone_report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `zone_id` INT NOT NULL,
  `reporter_id` BIGINT NOT NULL,
  `reason` VARCHAR(40) NOT NULL,
  `details` TEXT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_zone_report_reporter` (`zone_id`, `reporter_id`),
  INDEX `idx_zone_report_status_created` (`status`, `created_at`),
  INDEX `idx_zone_report_status_resolved` (`status`, `resolved_at`),
  INDEX `idx_zone_report_reporter` (`reporter_id`),
  CONSTRAINT `fk_zone_report_zone`
    FOREIGN KEY (`zone_id`) REFERENCES `zone` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_zone_report_reporter`
    FOREIGN KEY (`reporter_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `support_request` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `category` VARCHAR(40) NOT NULL,
  `email` VARCHAR(255) NOT NULL,
  `message` TEXT NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_support_request_status_created` (`status`, `created_at`),
  INDEX `idx_support_request_status_resolved` (`status`, `resolved_at`),
  INDEX `idx_support_request_email_category_created` (`email`, `category`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
