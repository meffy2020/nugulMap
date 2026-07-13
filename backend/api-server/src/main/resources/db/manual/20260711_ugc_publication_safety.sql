-- Keep existing places public, but require moderation for every new or edited app submission.
SET @zone_publication_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'zone'
    AND column_name = 'publication_status'
);

SET @zone_publication_ddl = IF(
  @zone_publication_column_exists = 0,
  'ALTER TABLE `zone` ADD COLUMN `publication_status` VARCHAR(20) NOT NULL DEFAULT ''PUBLISHED'' AFTER `image`',
  'SELECT 1'
);

PREPARE zone_publication_statement FROM @zone_publication_ddl;
EXECUTE zone_publication_statement;
DEALLOCATE PREPARE zone_publication_statement;

UPDATE `zone`
SET `publication_status` = 'PUBLISHED'
WHERE `publication_status` IS NULL OR `publication_status` = '';

-- Database safety fallback for legacy/manual deletes. The application account-deletion flow explicitly
-- deletes authored reviews first; SET NULL only prevents an unexpected direct DB user delete from failing.
SET @review_author_fk_requires_rebuild = (
  SELECT COUNT(*)
  FROM information_schema.referential_constraints
  WHERE constraint_schema = DATABASE()
    AND table_name = 'zone_review'
    AND constraint_name = 'fk_zone_review_author'
    AND delete_rule <> 'SET NULL'
);

SET @review_author_drop_fk_ddl = IF(
  @review_author_fk_requires_rebuild > 0,
  'ALTER TABLE `zone_review` DROP FOREIGN KEY `fk_zone_review_author`',
  'SELECT 1'
);

PREPARE review_author_drop_fk_statement FROM @review_author_drop_fk_ddl;
EXECUTE review_author_drop_fk_statement;
DEALLOCATE PREPARE review_author_drop_fk_statement;

SET @review_author_requires_nullable = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'zone_review'
    AND column_name = 'author_id'
    AND is_nullable = 'NO'
);

SET @review_author_nullable_ddl = IF(
  @review_author_requires_nullable > 0,
  'ALTER TABLE `zone_review` MODIFY COLUMN `author_id` BIGINT NULL',
  'SELECT 1'
);

PREPARE review_author_nullable_statement FROM @review_author_nullable_ddl;
EXECUTE review_author_nullable_statement;
DEALLOCATE PREPARE review_author_nullable_statement;

SET @review_author_fk_exists = (
  SELECT COUNT(*)
  FROM information_schema.referential_constraints
  WHERE constraint_schema = DATABASE()
    AND table_name = 'zone_review'
    AND constraint_name = 'fk_zone_review_author'
);

SET @review_author_add_fk_ddl = IF(
  @review_author_fk_exists = 0,
  'ALTER TABLE `zone_review` ADD CONSTRAINT `fk_zone_review_author` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE',
  'SELECT 1'
);

PREPARE review_author_add_fk_statement FROM @review_author_add_fk_ddl;
EXECUTE review_author_add_fk_statement;
DEALLOCATE PREPARE review_author_add_fk_statement;
