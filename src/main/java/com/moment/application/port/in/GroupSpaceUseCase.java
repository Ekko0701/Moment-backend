package com.moment.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 그룹 스페이스 유스케이스. 1:1 연결(InvitationUseCase)과 별개로, 초대 코드와 방장 수락으로 여러 명이 참여하는
 * 스페이스의 생성·참여·멤버 관리를 담당한다.
 */
public interface GroupSpaceUseCase {

    /** 그룹을 만들고 생성자를 방장으로 등록한 뒤 첫 초대 코드를 발급한다. */
    CreatedGroup create(UUID meId, String name);

    /** 방장이 초대 코드를 새로 발급한다. 이전 코드는 즉시 무효가 된다. */
    IssuedCode reissueCode(UUID meId, UUID spaceId);

    /** 그룹 멤버 누구나 현재 초대 코드를 조회한다. */
    IssuedCode currentCode(UUID meId, UUID spaceId);

    /**
     * 초대 코드로 참여를 요청한다. 방장이 수락해야 멤버가 된다.
     * 같은 그룹에 대기 중인 요청이 있으면 그 요청을 그대로 돌려준다.
     */
    JoinRequestView requestJoin(UUID meId, String code);

    /** 내가 보낸 대기 중 요청. 최신 순이며 종료된 그룹의 요청은 뺀다. */
    List<JoinRequestView> myJoinRequests(UUID meId);

    /** 내가 보낸 대기 중 요청을 취소한다. */
    void cancelJoinRequest(UUID meId, UUID spaceId, UUID requestId);

    /** 방장이 그룹에 들어온 대기 중 요청을 본다. 오래된 순. */
    List<PendingJoinRequestView> joinRequests(UUID meId, UUID spaceId);

    /** 방장이 대기 중 요청을 수락하거나 거절한다. 수락하면 요청자가 멤버가 된다. */
    void decideJoinRequest(UUID meId, UUID spaceId, UUID requestId, boolean approve);

    /** 활성 멤버 목록. 방장이 먼저, 나머지는 가입 순. */
    List<MemberView> members(UUID meId, UUID spaceId);

    /** 방장이 멤버를 내보낸다. */
    void kick(UUID meId, UUID spaceId, UUID targetUserId);

    /** 내 멤버십의 새 모먼트 알림 수신 여부를 바꾼다. 1:1과 그룹 모두에 적용된다. */
    void setNotifications(UUID meId, UUID spaceId, boolean enabled);

    /** 방장이 그룹을 종료한다. 모든 멤버가 나가고 스페이스는 비활성화된다. */
    void delete(UUID meId, UUID spaceId);

    record CreatedGroup(UUID spaceId, String code, Instant expiresAt) {
    }

    record IssuedCode(String code, Instant expiresAt) {
    }

    /** 요청자에게 보이는 참여 요청. status는 PENDING / APPROVED / REJECTED / CANCELED */
    record JoinRequestView(UUID id, UUID spaceId, String spaceName, String status, long createdAt) {
    }

    /** 방장에게 보이는 대기 중 요청과 요청자 정보 */
    record PendingJoinRequestView(
            UUID id,
            UUID userId,
            String handle,
            String nickname,
            String profileImageUrl,
            long createdAt) {
    }

    record MemberView(
            UUID userId,
            String handle,
            String nickname,
            String profileImageUrl,
            String role,
            long joinedAt) {
    }
}
