-- Recommendation data foundation for authenticated-user MVP personalization.
-- This migration is safe to run repeatedly: every table is guarded by IF NOT EXISTS.

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
