-- 사진 위 글씨: 시작 모서리(TOP/BOTTOM)와 색 이름.
-- 사진과 글이 함께 있는 모먼트에만 값이 있고, 그 밖에는 NULL이다.
-- H2(PostgreSQL MODE)와 PostgreSQL 양쪽에서 동작하는 포터블 SQL만 사용한다.

ALTER TABLE moments ADD COLUMN text_position VARCHAR(8);
ALTER TABLE moments ADD COLUMN text_color VARCHAR(8);
