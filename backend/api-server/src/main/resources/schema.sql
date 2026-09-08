-- MySQL Schema Script
-- H2에서 MySQL로 마이그레이션
-- 주의: Spring Boot는 이미 데이터베이스에 연결된 상태에서 이 스크립트를 실행합니다.

-- -----------------------------------------------------
-- Table `users`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `oauth_id` VARCHAR(255) NOT NULL,
  `oauth_provider` VARCHAR(50) NOT NULL,
  `nickname` VARCHAR(100) NULL,
  `email` VARCHAR(255) NOT NULL,
  `profile_image_url` VARCHAR(255) NULL,
  `apple_refresh_token_ciphertext` TEXT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`),
  UNIQUE KEY `uk_users_oauth_id` (`oauth_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------
-- Table `zone`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `zone` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `region` VARCHAR(100) NOT NULL,
  `type` VARCHAR(50) NULL,
  `subtype` VARCHAR(50) NULL,
  `description` TEXT NULL,
  `latitude` DECIMAL(10,7) NOT NULL,
  `longitude` DECIMAL(10,7) NOT NULL,
  `size` VARCHAR(50) NULL,
  `date` DATE NOT NULL DEFAULT (CURRENT_DATE),
  `address` VARCHAR(100) NOT NULL,
  `creator` VARCHAR(100) NULL,
  `creator_id` BIGINT NULL,
  `image` VARCHAR(255) NULL,
  `publication_status` VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_zone_address` (`address`),
  INDEX `idx_zone_creator_id` (`creator_id`),
  INDEX `idx_zone_creator` (`creator`),
  INDEX `idx_zone_region` (`region`),
  INDEX `idx_zone_type` (`type`),
  CONSTRAINT `fk_zone_creator` 
    FOREIGN KEY (`creator_id`) 
    REFERENCES `users` (`id`) 
    ON DELETE SET NULL 
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------
-- Table `zone_review`
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `zone_review` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `zone_id` INT NOT NULL,
  `author_id` BIGINT NULL,
  `content` TEXT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_zone_review_zone_id` (`zone_id`),
  INDEX `idx_zone_review_author_id` (`author_id`),
  CONSTRAINT `fk_zone_review_zone`
    FOREIGN KEY (`zone_id`)
    REFERENCES `zone` (`id`)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT `fk_zone_review_author`
    FOREIGN KEY (`author_id`)
    REFERENCES `users` (`id`)
    ON DELETE SET NULL
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------
-- Table `location_letter`
-- Letters are attached to a coarse coordinate rather than a registered place.
-- -----------------------------------------------------
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

-- -----------------------------------------------------
-- Table `location_letter_like`
-- -----------------------------------------------------
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

-- -----------------------------------------------------
-- Table `location_letter_report`
-- -----------------------------------------------------
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

-- -----------------------------------------------------
-- Table `user_block`
-- -----------------------------------------------------
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
    FOREIGN KEY (`blocked_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------
-- Table `zone_review_report`
-- -----------------------------------------------------
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

-- -----------------------------------------------------
-- Table `zone_report`
-- User reports for public place descriptions, addresses, and images.
-- -----------------------------------------------------
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

-- -----------------------------------------------------
-- Table `support_request`
-- Public support/account-deletion requests. The payload is never returned by public endpoints.
-- -----------------------------------------------------
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

-- -----------------------------------------------------
-- Table `user_preference`
-- Authenticated-user recommendation settings. Personalization is opt-in.
-- Tag sets are stored as JSON arrays in TEXT columns for MySQL/H2 portability.
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_preference` (
  `user_id` BIGINT NOT NULL,
  `personalization_enabled` BOOLEAN NOT NULL DEFAULT FALSE,
  `preferred_tags` TEXT NOT NULL,
  `disliked_tags` TEXT NOT NULL,
  `budget_level` VARCHAR(20) NULL,
  `max_distance_meters` INT NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_user_preference_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------
-- Table `user_saved_item`
-- Provider-scoped item keys prevent duplicate saves for one user.
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_saved_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `item_type` VARCHAR(40) NOT NULL,
  `provider` VARCHAR(64) NOT NULL,
  `item_id` VARCHAR(191) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `category` VARCHAR(100) NULL,
  `lat` DECIMAL(10,7) NOT NULL,
  `lng` DECIMAL(10,7) NOT NULL,
  `source_url` VARCHAR(2048) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_saved_item_key` (`user_id`, `item_type`, `provider`, `item_id`),
  INDEX `idx_user_saved_item_user_updated` (`user_id`, `updated_at`),
  CONSTRAINT `fk_user_saved_item_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------
-- Table `recommendation_interaction`
-- MVP interactions belong to authenticated users; anonymous rows are not collected.
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `recommendation_interaction` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `request_id` VARCHAR(64) NOT NULL,
  `item_type` VARCHAR(40) NOT NULL,
  `provider` VARCHAR(64) NOT NULL,
  `item_id` VARCHAR(191) NOT NULL,
  `event_type` VARCHAR(40) NOT NULL,
  `intent` VARCHAR(40) NOT NULL,
  `context_region` VARCHAR(100) NULL,
  `model_version` VARCHAR(64) NOT NULL,
  `occurred_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rec_interaction_event` (`user_id`, `request_id`, `item_type`, `provider`, `item_id`, `event_type`),
  INDEX `idx_rec_interaction_user_occurred` (`user_id`, `occurred_at`),
  INDEX `idx_rec_interaction_user_event_occurred` (`user_id`, `event_type`, `occurred_at`),
  INDEX `idx_rec_interaction_occurred` (`occurred_at`),
  INDEX `idx_rec_interaction_request` (`request_id`),
  INDEX `idx_rec_interaction_item` (`item_type`, `provider`, `item_id`),
  CONSTRAINT `fk_rec_interaction_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------
