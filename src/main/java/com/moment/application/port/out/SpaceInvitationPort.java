package com.moment.application.port.out;

import com.moment.domain.invite.SpaceInvitation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceInvitationPort {

    SpaceInvitation save(SpaceInvitation invitation);

    Optional<SpaceInvitation> findById(UUID id);

    /** 응답 처리 시 행을 잠가(비관적 쓰기 잠금) 동시 수락을 직렬화한다. */
    Optional<SpaceInvitation> findByIdForUpdate(UUID id);

    /**
     * 두 사용자 사이에 아직 유효한(생성 시각이 since 이후인) PENDING 초대가 있는지 확인한다.
     * TTL이 지난 PENDING은 상태 마킹 여부와 무관하게 차단 근거로 삼지 않는다.
     */
    boolean existsPendingBetween(UUID a, UUID b, Instant since);

    List<SpaceInvitation> findAllByUser(UUID userId, boolean received);

    List<SpaceInvitation> findAllPendingByUserId(UUID userId);

    int countByFromUserIdSince(UUID fromUserId, Instant since);

    boolean existsDeclinedBetweenSince(UUID from, UUID to, Instant since);
}
