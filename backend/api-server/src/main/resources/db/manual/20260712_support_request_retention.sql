-- Record terminal processing time so support PII can be deleted on the published retention schedule.
SET @support_resolved_at_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'support_request'
    AND column_name = 'resolved_at'
);

SET @support_resolved_at_ddl = IF(
  @support_resolved_at_column_exists = 0,
  'ALTER TABLE `support_request` ADD COLUMN `resolved_at` DATETIME NULL AFTER `created_at`',
  'SELECT 1'
);

PREPARE support_resolved_at_statement FROM @support_resolved_at_ddl;
EXECUTE support_resolved_at_statement;
DEALLOCATE PREPARE support_resolved_at_statement;

UPDATE `support_request`
SET `resolved_at` = `created_at`
WHERE `status` IN ('RESOLVED', 'REJECTED')
  AND `resolved_at` IS NULL;

SET @support_resolved_at_index_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'support_request'
    AND index_name = 'idx_support_request_status_resolved'
);

SET @support_resolved_at_index_ddl = IF(
  @support_resolved_at_index_exists = 0,
  'ALTER TABLE `support_request` ADD INDEX `idx_support_request_status_resolved` (`status`, `resolved_at`)',
  'SELECT 1'
);

PREPARE support_resolved_at_index_statement FROM @support_resolved_at_index_ddl;
EXECUTE support_resolved_at_index_statement;
DEALLOCATE PREPARE support_resolved_at_index_statement;
