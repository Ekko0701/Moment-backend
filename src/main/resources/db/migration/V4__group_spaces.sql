-- 그룹 스페이스: 멤버 알림 설정, 그룹 초대 코드, 참여 요청
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

-- 그룹 참여 요청. 코드를 입력하면 PENDING으로 생기고, 방장이 수락(APPROVED)·거절(REJECTED)하거나 요청자가 취소(CANCELED)한다.
-- (스페이스, 사용자)당 PENDING은 하나만 두며, 서비스가 스페이스 행 잠금 아래에서 보장한다.
CREATE TABLE group_join_requests (
    id         UUID        NOT NULL,
    space_id   UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    status     VARCHAR(16) NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    decided_at TIMESTAMP,
    CONSTRAINT pk_group_join_requests PRIMARY KEY (id),
    CONSTRAINT fk_group_join_requests_space FOREIGN KEY (space_id) REFERENCES spaces (id),
    CONSTRAINT fk_group_join_requests_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_group_join_requests_space_status ON group_join_requests (space_id, status);
CREATE INDEX idx_group_join_requests_user_status ON group_join_requests (user_id, status);

-- 멤버별 최신 모먼트 조회(스페이스·작성자별 최신 1건)를 받치는 인덱스
CREATE INDEX idx_moments_space_author_created ON moments (space_id, author_id, created_at);
