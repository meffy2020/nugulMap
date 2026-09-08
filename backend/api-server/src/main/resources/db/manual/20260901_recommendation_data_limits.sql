-- Enforce recommendation interaction idempotency on databases created before the unique key existed.
-- The application enforces the 500 saved-item and 10,000 interaction per-user caps while holding users.id.

SET @rec_interaction_table_exists = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_name = 'recommendation_interaction'
);

SET @rec_interaction_unique_exists = (
  SELECT COUNT(DISTINCT index_name)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'recommendation_interaction'
    AND index_name = 'uk_rec_interaction_event'
    AND non_unique = 0
);

SET @rec_interaction_dedupe_sql = IF(
  @rec_interaction_table_exists = 1 AND @rec_interaction_unique_exists = 0,
  'DELETE duplicate_row FROM `recommendation_interaction` duplicate_row INNER JOIN `recommendation_interaction` canonical_row ON duplicate_row.`user_id` = canonical_row.`user_id` AND duplicate_row.`request_id` = canonical_row.`request_id` AND duplicate_row.`item_type` = canonical_row.`item_type` AND duplicate_row.`provider` = canonical_row.`provider` AND duplicate_row.`item_id` = canonical_row.`item_id` AND duplicate_row.`event_type` = canonical_row.`event_type` AND duplicate_row.`id` > canonical_row.`id`',
  'SELECT 1'
);

PREPARE rec_interaction_dedupe_statement FROM @rec_interaction_dedupe_sql;
EXECUTE rec_interaction_dedupe_statement;
DEALLOCATE PREPARE rec_interaction_dedupe_statement;

SET @rec_interaction_unique_ddl = IF(
  @rec_interaction_table_exists = 1 AND @rec_interaction_unique_exists = 0,
  'ALTER TABLE `recommendation_interaction` ADD UNIQUE KEY `uk_rec_interaction_event` (`user_id`, `request_id`, `item_type`, `provider`, `item_id`, `event_type`)',
  'SELECT 1'
);

PREPARE rec_interaction_unique_statement FROM @rec_interaction_unique_ddl;
EXECUTE rec_interaction_unique_statement;
DEALLOCATE PREPARE rec_interaction_unique_statement;

SET @rec_interaction_occurred_index_exists = (
  SELECT COUNT(DISTINCT index_name)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'recommendation_interaction'
    AND index_name = 'idx_rec_interaction_occurred'
);

SET @rec_interaction_occurred_index_ddl = IF(
  @rec_interaction_table_exists = 1 AND @rec_interaction_occurred_index_exists = 0,
  'ALTER TABLE `recommendation_interaction` ADD INDEX `idx_rec_interaction_occurred` (`occurred_at`)',
  'SELECT 1'
);

PREPARE rec_interaction_occurred_index_statement FROM @rec_interaction_occurred_index_ddl;
EXECUTE rec_interaction_occurred_index_statement;
DEALLOCATE PREPARE rec_interaction_occurred_index_statement;
