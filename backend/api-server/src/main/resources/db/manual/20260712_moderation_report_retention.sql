-- Record terminal moderation time so closed report PII can be purged on schedule.
SET @zone_review_report_table_exists = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_name = 'zone_review_report'
);

SET @zone_review_report_resolved_at_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'zone_review_report'
    AND column_name = 'resolved_at'
);

SET @zone_review_report_resolved_at_ddl = IF(
  @zone_review_report_table_exists = 1 AND @zone_review_report_resolved_at_column_exists = 0,
  'ALTER TABLE `zone_review_report` ADD COLUMN `resolved_at` DATETIME NULL AFTER `created_at`',
  'SELECT 1'
);

PREPARE zone_review_report_resolved_at_statement FROM @zone_review_report_resolved_at_ddl;
EXECUTE zone_review_report_resolved_at_statement;
DEALLOCATE PREPARE zone_review_report_resolved_at_statement;

UPDATE `zone_review_report`
SET `resolved_at` = `created_at`
WHERE `status` IN ('RESOLVED', 'DISMISSED')
  AND `resolved_at` IS NULL;

SET @zone_review_report_resolved_at_index_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'zone_review_report'
    AND index_name = 'idx_zone_review_report_status_resolved'
);

SET @zone_review_report_resolved_at_index_ddl = IF(
  @zone_review_report_table_exists = 1 AND @zone_review_report_resolved_at_index_exists = 0,
  'ALTER TABLE `zone_review_report` ADD INDEX `idx_zone_review_report_status_resolved` (`status`, `resolved_at`)',
  'SELECT 1'
);

PREPARE zone_review_report_resolved_at_index_statement FROM @zone_review_report_resolved_at_index_ddl;
EXECUTE zone_review_report_resolved_at_index_statement;
DEALLOCATE PREPARE zone_review_report_resolved_at_index_statement;

SET @zone_report_table_exists = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_name = 'zone_report'
);

SET @zone_report_resolved_at_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'zone_report'
    AND column_name = 'resolved_at'
);

SET @zone_report_resolved_at_ddl = IF(
  @zone_report_table_exists = 1 AND @zone_report_resolved_at_column_exists = 0,
  'ALTER TABLE `zone_report` ADD COLUMN `resolved_at` DATETIME NULL AFTER `created_at`',
  'SELECT 1'
);

PREPARE zone_report_resolved_at_statement FROM @zone_report_resolved_at_ddl;
EXECUTE zone_report_resolved_at_statement;
DEALLOCATE PREPARE zone_report_resolved_at_statement;

UPDATE `zone_report`
SET `resolved_at` = `created_at`
WHERE `status` IN ('RESOLVED', 'DISMISSED')
  AND `resolved_at` IS NULL;

SET @zone_report_resolved_at_index_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'zone_report'
    AND index_name = 'idx_zone_report_status_resolved'
);

SET @zone_report_resolved_at_index_ddl = IF(
  @zone_report_table_exists = 1 AND @zone_report_resolved_at_index_exists = 0,
  'ALTER TABLE `zone_report` ADD INDEX `idx_zone_report_status_resolved` (`status`, `resolved_at`)',
  'SELECT 1'
);

PREPARE zone_report_resolved_at_index_statement FROM @zone_report_resolved_at_index_ddl;
EXECUTE zone_report_resolved_at_index_statement;
DEALLOCATE PREPARE zone_report_resolved_at_index_statement;
