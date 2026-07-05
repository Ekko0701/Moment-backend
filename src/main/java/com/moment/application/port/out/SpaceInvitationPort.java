package com.moment.application.port.out;

import com.moment.domain.invite.SpaceInvitation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceInvitationPort {

    SpaceInvitation save(SpaceInvitation invitation);

    Optional<SpaceInvitation> findById(UUID id);

    boolean existsPendingBetween(UUID a, UUID b);

    List<SpaceInvitation> findAllByUser(UUID userId, boolean received);

    List<SpaceInvitation> findAllPendingByUserId(UUID userId);

    int countByFromUserIdSince(UUID fromUserId, Instant since);

    boolean existsDeclinedBetweenSince(UUID from, UUID to, Instant since);
}
