-- 그룹 스페이스 (기능정의서 F-02-01 GROUP)
-- H2(PostgreSQL MODE)와 PostgreSQL 양쪽에서 동작하는 포터블 SQL만 사용한다.

ALTER TABLE space_members ADD COLUMN notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE group_invite_codes (
    code       VARCHAR(6) NOT NULL,
    space_id   UUID       NOT NULL,
    issued_by  UUID       NOT NULL,
    expires_at TIMESTAMP  NOT NULL,
    created_at TIMESTAMP  NOT NULL,
    CONSTRAINT pk_group_invite_codes PRIMARY KEY (code),
    CONSTRAINT uq_group_invite_codes_space UNIQUE (space_id),
    CONSTRAINT fk_group_invite_codes_space FOREIGN KEY (space_id) REFERENCES spaces (id),
    CONSTRAINT fk_group_invite_codes_issuer FOREIGN KEY (issued_by) REFERENCES users (id)
);
