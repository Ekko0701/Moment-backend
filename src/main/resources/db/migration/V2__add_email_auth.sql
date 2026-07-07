-- Email 인증 추가 (기능정의서 F-01-05)
-- email과 password_hash는 Apple 사용자와의 공존을 위해 nullable이다.

ALTER TABLE users ADD COLUMN email VARCHAR(255);
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);
