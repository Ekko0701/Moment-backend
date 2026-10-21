package com.moment.application.port.in;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 모먼트 유스케이스 포트 (인바운드).
 */
public interface MomentUseCase {

    /**
     * 모먼트 업로드 URL 발급.
     *
     * @param meId 현재 사용자 ID
     * @param contentType MIME 타입
     * @return uploadUrl, imageUrl
     */
    PresignedUrlResponse presign(UUID meId, String contentType);

    /**
     * 모먼트 생성.
     *
     * @param meId 현재 사용자 ID
     * @param command imageUrl, text 포함
     * @return 생성된 모먼트 뷰
     */
    MomentView create(UUID meId, CreateMomentCommand command);

    /**
     * 스페이스 타임라인 조회.
     *
     * @param meId 현재 사용자 ID
     * @param spaceId 스페이스 ID
     * @param cursor 불투명 페이지 커서 (null 허용) — (createdAt, id)를 인코딩한 문자열
     * @param limit 최대 조회 건수 (기본 20, 최대 50)
     * @return 모먼트 목록 및 페이징 정보
     */
    TimelinePage timeline(UUID meId, UUID spaceId, String cursor, int limit);

    /**
     * 스페이스의 최신 모먼트 조회 (위젯용).
     *
     * @param meId 현재 사용자 ID
     * @param spaceId 스페이스 ID
     * @param excludeMine 내 모먼트 제외 여부
     * @return 최신 모먼트
     */
    Optional<MomentView> latest(UUID meId, UUID spaceId, boolean excludeMine);

    /**
     * 모먼트 삭제 (작성자만 가능, 소프트 삭제).
     *
     * @param meId 현재 사용자 ID
     * @param momentId 모먼트 ID
     */
    void delete(UUID meId, UUID momentId);

    /**
     * 스페이스 활성 멤버 각자의 최신 모먼트 1건씩. 나와 차단 관계인 멤버는 제외한다.
     * 멤버가 아니면 FORBIDDEN.
     */
    List<MomentView> latestPerMember(UUID meId, UUID spaceId);

    /** 내가 속한 스페이스마다 나와 차단 관계를 제외한 최신 모먼트 1건. 모먼트가 없는 스페이스는 빠진다. */
    List<MomentView> latestForMySpaces(UUID meId);

    record CreateMomentCommand(UUID spaceId, String imageUrl, String text) {
    }

    record PresignedUrlResponse(String uploadUrl, String imageUrl) {
    }

    record MomentView(
            UUID id,
            UUID spaceId,
            AuthorView author,
            String imageUrl,
            String text,
            long createdAtMs,
            List<ReactionCount> reactions,
            String myReaction) {

        public record AuthorView(UUID userId, String handle, String nickname, String profileImageUrl) {
        }

        public record ReactionCount(String emoji, int count) {
        }
    }

    record TimelinePage(List<MomentView> items, String nextCursor, boolean hasNext) {
    }
}
