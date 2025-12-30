-- MySQL Schema Script
-- H2에서 MySQL로 마이그레이션
-- 주의: Spring Boot는 이미 데이터베이스에 연결된 상태에서 이 스크립트를 실행합니다.
-- CREATE DATABASE와 USE는 필요 없습니다 (JDBC URL에 데이터베이스가 이미 지정됨).

-- -----------------------------------------------------
-- Table `zone`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `zone`;

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
  `image` VARCHAR(255) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_zone_address` (`address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------
-- Table `users`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `users`;

CREATE TABLE IF NOT EXISTS `users` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `oauth_id` VARCHAR(255) NOT NULL,
  `oauth_provider` VARCHAR(50) NOT NULL,
  `nickname` VARCHAR(100) NULL,
  `email` VARCHAR(255) NOT NULL,
  `profile_image_url` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`),
  UNIQUE KEY `uk_users_oauth_id` (`oauth_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
