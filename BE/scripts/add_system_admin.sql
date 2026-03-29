-- Add one extra SYSTEM_ADMIN account (idempotent)
-- Default login:
--   username: admin2
--   password: Admin@123
--   email: admin2@cvconnect.local

SET @username = 'admin2';
SET @email = 'admin2@cvconnect.local';
SET @password_hash = '$2b$12$cYrMn2qIkTd8Ln.UYu7Lz.TjAvxc.33AgQtSRQ3c1ouxkBghxhtsa';

INSERT INTO users (
  username,
  password,
  email,
  full_name,
  phone_number,
  address,
  date_of_birth,
  avatar_id,
  is_email_verified,
  access_method,
  created_by
)
SELECT
  @username,
  @password_hash,
  @email,
  'System Admin 2',
  '0123456788',
  'Ha Noi',
  NULL,
  NULL,
  1,
  'LOCAL',
  'seed-script'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM users WHERE username = @username OR email = @email
);

SET @user_id = (SELECT id FROM users WHERE username = @username LIMIT 1);
SET @role_id = (SELECT id FROM roles WHERE code = 'SYSTEM_ADMIN' LIMIT 1);

INSERT INTO role_user (
  user_id,
  role_id,
  is_default,
  created_by
)
SELECT
  @user_id,
  @role_id,
  1,
  'seed-script'
FROM DUAL
WHERE @user_id IS NOT NULL
  AND @role_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM role_user WHERE user_id = @user_id AND role_id = @role_id
  );

INSERT INTO management_members (
  user_id,
  created_by
)
SELECT
  @user_id,
  'seed-script'
FROM DUAL
WHERE @user_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM management_members WHERE user_id = @user_id
  );
