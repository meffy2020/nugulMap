-- H2 Script generated from MySQL Script

-- -----------------------------------------------------
-- Schema mydb
-- -----------------------------------------------------
CREATE SCHEMA IF NOT EXISTS mydb;

-- -----------------------------------------------------
-- Table `zone`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `zone`;

CREATE TABLE IF NOT EXISTS `zone` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `region` VARCHAR(100) NOT NULL,
  `type` VARCHAR(50) NULL,
  `subtype` VARCHAR(50) NULL,
  `description` CLOB NULL,
  `latitude` DECIMAL(10,7) NOT NULL,
  `longitude` DECIMAL(10,7) NOT NULL,
  `size` VARCHAR(50) NULL,
  `date` DATE NOT NULL DEFAULT CURRENT_DATE,
  `address` VARCHAR(100) NOT NULL,
  `creator` VARCHAR(100) NULL,
  `image` VARCHAR(255) NULL,
  PRIMARY KEY (`id`),
  UNIQUE (`address`)
);

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
  UNIQUE (`email`),
  UNIQUE (`oauth_id`)
);

