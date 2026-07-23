-- Sign in with Apple authorization code 교환으로 얻은 refresh token의 암호문만 저장한다.
CREATE TABLE apple_credentials (
    user_id                 UUID          NOT NULL,
    encrypted_refresh_token VARCHAR(2048) NOT NULL,
    updated_at              TIMESTAMP     NOT NULL,
    CONSTRAINT pk_apple_credentials PRIMARY KEY (user_id),
    CONSTRAINT fk_apple_credentials_user FOREIGN KEY (user_id) REFERENCES users (id)
);
