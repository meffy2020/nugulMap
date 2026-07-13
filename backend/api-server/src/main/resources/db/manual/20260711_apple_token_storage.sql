-- Store only AES-GCM ciphertext. The plaintext Apple refresh token must never be persisted.
SET @apple_token_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'users'
    AND column_name = 'apple_refresh_token_ciphertext'
);

SET @apple_token_ddl = IF(
  @apple_token_column_exists = 0,
  'ALTER TABLE `users` ADD COLUMN `apple_refresh_token_ciphertext` TEXT NULL AFTER `profile_image_url`',
  'SELECT 1'
);

PREPARE apple_token_statement FROM @apple_token_ddl;
EXECUTE apple_token_statement;
DEALLOCATE PREPARE apple_token_statement;
