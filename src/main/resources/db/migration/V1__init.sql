-- Moment 초기 스키마 (기능정의서 v1.2 / 6장 데이터 모델)
-- H2(PostgreSQL MODE)와 PostgreSQL 양쪽에서 동작하는 포터블 SQL만 사용한다.

CREATE TABLE users (
    id                UUID         NOT NULL,
    handle            VARCHAR(15)  NOT NULL,
    nickname          VARCHAR(12)  NOT NULL,
    profile_image_url VARCHAR(500),
    auth_provider     VARCHAR(20)  NOT NULL,
    apple_sub         VARCHAR(255),
    is_searchable     BOOLEAN      NOT NULL DEFAULT TRUE,
    handle_updated_at TIMESTAMP,
    status            VARCHAR(20)  NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_handle UNIQUE (handle),
    CONSTRAINT uq_users_apple_sub UNIQUE (apple_sub)
);

CREATE TABLE spaces (
    id          UUID        NOT NULL,
    type        VARCHAR(20) NOT NULL,
    name        VARCHAR(50),
    max_members INT         NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    CONSTRAINT pk_spaces PRIMARY KEY (id)
);

CREATE TABLE space_members (
    id        UUID        NOT NULL,
    space_id  UUID        NOT NULL,
    user_id   UUID        NOT NULL,
    role      VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP   NOT NULL,
    left_at   TIMESTAMP,
    CONSTRAINT pk_space_members PRIMARY KEY (id),
    CONSTRAINT uq_space_members UNIQUE (space_id, user_id),
    CONSTRAINT fk_space_members_space FOREIGN KEY (space_id) REFERENCES spaces (id),
    CONSTRAINT fk_space_members_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_space_members_user ON space_members (user_id);

CREATE TABLE invite_codes (
    code           VARCHAR(6) NOT NULL,
    issuer_user_id UUID       NOT NULL,
    expires_at     TIMESTAMP  NOT NULL,
    used_at        TIMESTAMP,
    created_at     TIMESTAMP  NOT NULL,
    CONSTRAINT pk_invite_codes PRIMARY KEY (code),
    CONSTRAINT fk_invite_codes_issuer FOREIGN KEY (issuer_user_id) REFERENCES users (id)
);

CREATE TABLE space_invitations (
    id           UUID        NOT NULL,
    from_user_id UUID        NOT NULL,
    to_user_id   UUID        NOT NULL,
    via          VARCHAR(10) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    created_at   TIMESTAMP   NOT NULL,
    responded_at TIMESTAMP,
    CONSTRAINT pk_space_invitations PRIMARY KEY (id),
    CONSTRAINT fk_invitations_from FOREIGN KEY (from_user_id) REFERENCES users (id),
    CONSTRAINT fk_invitations_to FOREIGN KEY (to_user_id) REFERENCES users (id)
);

CREATE INDEX idx_invitations_to_status ON space_invitations (to_user_id, status);
CREATE INDEX idx_invitations_from_status ON space_invitations (from_user_id, status);

CREATE TABLE moments (
    id         UUID      NOT NULL,
    space_id   UUID      NOT NULL,
    author_id  UUID      NOT NULL,
    image_url  VARCHAR(500),
    text       VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    CONSTRAINT pk_moments PRIMARY KEY (id),
    CONSTRAINT fk_moments_space FOREIGN KEY (space_id) REFERENCES spaces (id),
    CONSTRAINT fk_moments_author FOREIGN KEY (author_id) REFERENCES users (id)
);

CREATE INDEX idx_moments_space_created ON moments (space_id, created_at);

CREATE TABLE reactions (
    id         UUID        NOT NULL,
    moment_id  UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    emoji      VARCHAR(10) NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    CONSTRAINT pk_reactions PRIMARY KEY (id),
    CONSTRAINT uq_reactions UNIQUE (moment_id, user_id),
    CONSTRAINT fk_reactions_moment FOREIGN KEY (moment_id) REFERENCES moments (id),
    CONSTRAINT fk_reactions_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE devices (
    id         UUID         NOT NULL,
    user_id    UUID         NOT NULL,
    apns_token VARCHAR(200) NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    CONSTRAINT pk_devices PRIMARY KEY (id),
    CONSTRAINT uq_devices_token UNIQUE (apns_token),
    CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE reports (
    id          UUID        NOT NULL,
    reporter_id UUID        NOT NULL,
    moment_id   UUID        NOT NULL,
    reason      VARCHAR(30) NOT NULL,
    detail      VARCHAR(500),
    created_at  TIMESTAMP   NOT NULL,
    CONSTRAINT pk_reports PRIMARY KEY (id),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT fk_reports_moment FOREIGN KEY (moment_id) REFERENCES moments (id)
);

CREATE TABLE blocks (
    id         UUID      NOT NULL,
    blocker_id UUID      NOT NULL,
    blocked_id UUID      NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_blocks PRIMARY KEY (id),
    CONSTRAINT uq_blocks UNIQUE (blocker_id, blocked_id),
    CONSTRAINT fk_blocks_blocker FOREIGN KEY (blocker_id) REFERENCES users (id),
    CONSTRAINT fk_blocks_blocked FOREIGN KEY (blocked_id) REFERENCES users (id)
);

CREATE TABLE refresh_tokens (
    id         UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
